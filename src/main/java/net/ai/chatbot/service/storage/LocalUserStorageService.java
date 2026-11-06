package net.ai.chatbot.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-user files on local disk.
 *
 * Layout (under {@code ai.storage.root}, default {@code ./storage}):
 * <pre>
 *   storage/users/{userFolder}/images/
 *   storage/users/{userFolder}/uploads/
 * </pre>
 * {@code userFolder} is the sanitized Clerk user id, or a sanitized email when no id is available.
 */
@Service
@Slf4j
public class LocalUserStorageService {

    public static final String IMAGES = "images";
    public static final String UPLOADS = "uploads";

    @Value("${ai.storage.root:storage}")
    private String storageRoot;

    /**
     * Origin placed in URLs returned to the browser. The AI Images page assigns this
     * string directly to {@code img.src}, which does not send a bearer token, so the
     * file is loaded from this public-by-id path. The same path is also served on port 8080.
     */
    @Value("${ai.storage.public-base-url:https://subratapc.net}")
    private String publicBaseUrl;

    public StoredFile saveImage(String userId, String email, byte[] bytes, String extension) throws IOException {
        return save(userId, email, IMAGES, bytes, extension);
    }

    public StoredFile saveUpload(String userId, String email, byte[] bytes, String extension) throws IOException {
        return save(userId, email, UPLOADS, bytes, extension);
    }

    /**
     * Find a previously saved file by its unguessable file name.
     * Only {@code images} and {@code uploads} are searched. The storage root is not listed.
     */
    public Optional<Path> findByFileName(String fileName) {
        if (!isSafeFileName(fileName)) {
            return Optional.empty();
        }
        Path users = root().resolve("users");
        if (!Files.isDirectory(users)) {
            return Optional.empty();
        }
        try (DirectoryStream<Path> userDirs = Files.newDirectoryStream(users)) {
            for (Path userDir : userDirs) {
                if (!Files.isDirectory(userDir)) {
                    continue;
                }
                for (String kind : new String[] { IMAGES, UPLOADS }) {
                    Path candidate = userDir.resolve(kind).resolve(fileName).normalize();
                    if (candidate.startsWith(users) && Files.isRegularFile(candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Could not look up local file {}", fileName);
        }
        return Optional.empty();
    }

    /**
     * Find a file by the {@code users/{folder}/{images|uploads}/{fileName}} path that
     * {@link StoredFile#relativePath()} returns. Any other shape is refused.
     */
    public Optional<Path> findRelative(String relativePath) {
        if (relativePath == null) {
            return Optional.empty();
        }
        String[] parts = relativePath.split("/", -1);
        if (parts.length != 4 || !"users".equals(parts[0])
                || !(IMAGES.equals(parts[2]) || UPLOADS.equals(parts[2]))
                || !isSafeFileName(parts[1]) || !isSafeFileName(parts[3])) {
            return Optional.empty();
        }
        Path users = root().resolve("users");
        Path candidate = users.resolve(parts[1]).resolve(parts[2]).resolve(parts[3]).normalize();
        if (candidate.startsWith(users) && Files.isRegularFile(candidate)) {
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** True when {@code file} sits in the folder for this user id or email. */
    public boolean ownedBy(Path file, String userId, String email) {
        if (file == null || file.getParent() == null || file.getParent().getParent() == null) {
            return false;
        }
        String folder = file.getParent().getParent().getFileName().toString();
        return userFolder(userId, email).equals(folder);
    }

    public void deleteRelative(String relativePath) {
        if (relativePath == null || !relativePath.startsWith("users/")) {
            return;
        }
        Path root = root();
        Path file = root.resolve(relativePath).normalize();
        Path users = root.resolve("users");
        if (!file.startsWith(users)) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete local file {}", relativePath);
        }
    }

    public static String userFolder(String userId, String email) {
        String raw = firstNonBlank(userId, email);
        if (raw == null) {
            return "unknown";
        }
        String safe = raw.replaceAll("[^A-Za-z0-9_-]", "_").replaceAll("_+", "_");
        safe = safe.replaceAll("^_+|_+$", "");
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
            return "unknown";
        }
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return safe;
    }

    public static String contentTypeFor(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    public static boolean isSafeFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 128) {
            return false;
        }
        if (fileName.contains("..") || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
            return false;
        }
        return fileName.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }

    private StoredFile save(String userId, String email, String kind, byte[] bytes, String extension) throws IOException {
        if (!IMAGES.equals(kind) && !UPLOADS.equals(kind)) {
            throw new IOException("Unsupported user storage folder");
        }
        String folder = userFolder(userId, email);
        String ext = sanitizeExtension(extension);
        String fileName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        Path userRoot = root().resolve("users").resolve(folder).normalize();
        if (!userRoot.startsWith(root().resolve("users"))) {
            throw new IOException("Invalid user folder");
        }
        Path images = userRoot.resolve(IMAGES);
        Path uploads = userRoot.resolve(UPLOADS);
        Files.createDirectories(images);
        Files.createDirectories(uploads);

        Path dir = IMAGES.equals(kind) ? images : uploads;
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) {
            throw new IOException("Invalid file path");
        }
        Files.write(file, bytes);

        String relative = "users/" + folder + "/" + kind + "/" + fileName;
        log.info("Saved user file locally at {} (folder {})", relative, folder);
        return new StoredFile(publicUrl(fileName), relative, fileName, folder, userId);
    }

    private String publicUrl(String fileName) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/v1/api/public/user-files/" + fileName;
    }

    private Path root() {
        Path dir = Paths.get(storageRoot == null || storageRoot.isBlank() ? "storage" : storageRoot);
        if (!dir.isAbsolute()) {
            dir = Paths.get(System.getProperty("user.dir", ".")).resolve(dir);
        }
        return dir.toAbsolutePath().normalize();
    }

    private static String sanitizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        int dot = ext.lastIndexOf('.');
        if (dot >= 0) {
            ext = ext.substring(dot + 1);
        }
        if (!ext.matches("[a-z0-9]{1,8}")) {
            return "bin";
        }
        return ext;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    public record StoredFile(String publicUrl, String relativePath, String fileName, String userFolder, String userId) {}
}
