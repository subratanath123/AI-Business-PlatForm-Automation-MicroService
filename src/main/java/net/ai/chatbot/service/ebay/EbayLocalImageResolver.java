package net.ai.chatbot.service.ebay;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.service.storage.LocalUserStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the bytes behind a picture URL this app emitted (subratapc.net,
 * localhost, the configured base/frontend/storage origins, or a Supabase URL
 * whose file was kept locally) so it can be copied to eBay Picture Services.
 * Files are found through {@link LocalUserStorageService}; when the file is
 * not on disk, the same path is requested from this app on localhost.
 */
@Component
@Slf4j
class EbayLocalImageResolver {

    private static final int MAX_BYTES = 12 * 1024 * 1024;

    private final LocalUserStorageService storage;
    private final Set<String> ownHosts;
    private final int serverPort;

    EbayLocalImageResolver(LocalUserStorageService storage,
                           @Value("${app.base-url:}") String baseUrl,
                           @Value("${app.frontend-url:}") String frontendUrl,
                           @Value("${ai.storage.public-base-url:https://subratapc.net}") String storageBaseUrl,
                           @Value("${server.port:8080}") int serverPort) {
        this.storage = storage;
        this.serverPort = serverPort;
        Set<String> hosts = new HashSet<>();
        for (String origin : new String[] { baseUrl, frontendUrl, storageBaseUrl }) {
            String host = EbayListingFields.hostOfOrigin(origin);
            if (host != null) hosts.add(host);
        }
        this.ownHosts = Set.copyOf(hosts);
    }

    /** True when the URL names a file in this app's storage, whether or not it still exists. */
    boolean isLocal(String url) {
        return EbayListingFields.localImageRef(url, ownHosts) != null;
    }

    Optional<EbayListingFields.DecodedImage> load(String url) {
        EbayListingFields.LocalImageRef ref = EbayListingFields.localImageRef(url, ownHosts);
        if (ref == null) return Optional.empty();
        Optional<Path> file = ref.relativePath() == null
                ? Optional.empty()
                : storage.findRelative(ref.relativePath());
        if (file.isEmpty()) file = storage.findByFileName(ref.fileName());
        if (file.isPresent()) {
            try {
                byte[] bytes = Files.readAllBytes(file.get());
                if (bytes.length > 0) return Optional.of(image(bytes, ref.fileName(), null));
            } catch (IOException e) {
                log.warn("Could not read local product image {}: {}", ref.fileName(), e.getMessage());
            }
        }
        if (!ref.ownHost()) return Optional.empty();
        return fetchFromSelf(ref);
    }

    private Optional<EbayListingFields.DecodedImage> fetchFromSelf(EbayListingFields.LocalImageRef ref) {
        String path = ref.path();
        if (!path.startsWith("/v1/api/public/") && !path.startsWith("/api/public/")) return Optional.empty();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + serverPort + path))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofByteArray());
            String type = response.headers().firstValue("Content-Type").orElse("")
                    .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            byte[] bytes = response.body();
            if (response.statusCode() != 200 || !type.startsWith("image/")
                    || bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
                return Optional.empty();
            }
            return Optional.of(image(bytes, ref.fileName(), type));
        } catch (Exception e) {
            log.warn("Could not fetch local product image {} from this app: {}", ref.fileName(), e.getMessage());
            return Optional.empty();
        }
    }

    private static EbayListingFields.DecodedImage image(byte[] bytes, String fileName, String contentType) {
        String type = contentType;
        if (type == null || !type.startsWith("image/")) type = LocalUserStorageService.contentTypeFor(fileName);
        if (!type.startsWith("image/")) type = "image/jpeg";
        String ext = switch (type) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            default -> "jpg";
        };
        return new EbayListingFields.DecodedImage(bytes, type, ext);
    }
}
