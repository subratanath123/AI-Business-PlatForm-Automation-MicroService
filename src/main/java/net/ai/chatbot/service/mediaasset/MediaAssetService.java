package net.ai.chatbot.service.mediaasset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.AssetFolderDao;
import net.ai.chatbot.dao.MediaAssetDao;
import net.ai.chatbot.dto.mediaasset.DeleteResponse;
import net.ai.chatbot.dto.mediaasset.ListAssetsResponse;
import net.ai.chatbot.dto.mediaasset.UploadResponse;
import net.ai.chatbot.entity.AssetFolder;
import net.ai.chatbot.entity.MediaAsset;
import net.ai.chatbot.service.storage.SupabaseStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing media assets.
 * Handles upload to Supabase, database tracking, and deletion.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MediaAssetService {

    private final MediaAssetDao mediaAssetDao;
    private final AssetFolderDao assetFolderDao;
    private final SupabaseStorageService supabaseStorage;

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB

    /**
     * Upload multiple files to a specific folder in Supabase and save metadata to database.
     * Returns list of successfully uploaded assets and any failures.
     */
    @Transactional
    public UploadResponse uploadAll(String userEmail, List<MultipartFile> files, String folderPath) {
        List<UploadResponse.AssetDto> uploaded = new ArrayList<>();
        List<UploadResponse.FailedUpload> failed = new ArrayList<>();

        // Sanitize folder path
        if (folderPath == null) {
            folderPath = "";
        }
        folderPath = folderPath.trim();

        for (MultipartFile file : files) {
            try {
                // Validate file size
                if (file.getSize() > MAX_FILE_SIZE) {
                    throw new IllegalArgumentException("File exceeds 50 MB limit");
                }

                // Validate not empty
                if (file.isEmpty()) {
                    throw new IllegalArgumentException("File is empty");
                }

                // Sanitize filename
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || originalFilename.isEmpty()) {
                    originalFilename = "unnamed-file";
                }

                String safeName = sanitizeFilename(originalFilename);

                // Generate object path: social-posts/{userEmail}/{folderPath}/{timestamp}_{filename}
                String objectPath = "social-posts/" + userEmail + "/" 
                        + (folderPath.isEmpty() ? "" : folderPath + "/")
                        + System.currentTimeMillis() + "_" + safeName;

                // Upload to Supabase
                String publicUrl = supabaseStorage.upload(
                        objectPath, 
                        file.getBytes(), 
                        file.getContentType()
                );

                // Save to database
                MediaAsset asset = MediaAsset.builder()
                        .id(UUID.randomUUID().toString())
                        .userEmail(userEmail)
                        .fileName(originalFilename)
                        .mimeType(file.getContentType())
                        .sizeBytes(file.getSize())
                        .supabaseUrl(publicUrl)
                        .objectPath(objectPath)
                        .createdAt(Instant.now())
                        .tags(new ArrayList<>())
                        .folderPath(folderPath)
                        .build();

                mediaAssetDao.save(asset);
                log.info("Asset uploaded for user {} in folder '{}': {} ({})", userEmail, folderPath, originalFilename, asset.getId());

                // Return a fresh signed URL so the caller gets a working link even
                // when the bucket is private. Falls back to the stored URL on failure.
                uploaded.add(toAssetDto(asset, freshUrl(asset)));

            } catch (Exception e) {
                log.error("Failed to upload file for user {} in folder '{}': {}", userEmail, folderPath, e.getMessage(), e);
                failed.add(UploadResponse.FailedUpload.builder()
                        .fileName(file.getOriginalFilename())
                        .error(e.getMessage())
                        .build());
            }
        }

        return UploadResponse.builder()
                .uploaded(uploaded)
                .failed(failed)
                .build();
    }

    /**
     * Old uploadAll method for backward compatibility
     */
    @Transactional
    public UploadResponse uploadAll(String userEmail, List<MultipartFile> files) {
        return uploadAll(userEmail, files, "");
    }

    /**
     * List assets for a user with optional filtering.
     */
    public ListAssetsResponse list(String userEmail, String type, String search, int limit, int offset) {
        // Validate pagination params
        if (limit < 1 || limit > 200) limit = 100;
        if (offset < 0) offset = 0;

        int page = offset / limit;
        Pageable pageable = PageRequest.of(page, limit, Sort.by("createdAt").descending());

        Page<MediaAsset> assetPage;

        // Apply filters
        if (search != null && !search.trim().isEmpty()) {
            // Search by filename
            assetPage = mediaAssetDao.findByUserEmailAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(
                    userEmail, search, pageable);
        } else if (type != null && !type.isEmpty()) {
            // Filter by type (image or video)
            String mimeTypePattern = "^" + type + "/.*";
            assetPage = mediaAssetDao.findByUserEmailAndMimeTypeRegex(
                    userEmail, mimeTypePattern, pageable);
        } else {
            // No filter
            assetPage = mediaAssetDao.findByUserEmailOrderByCreatedAtDesc(userEmail, pageable);
        }

        // Refresh every URL in a single batch request. The stored `supabaseUrl`
        // is treated as a fallback only — for private buckets it may have been
        // a short-lived SAS URL whose signing key has since expired.
        List<MediaAsset> pageAssets = assetPage.getContent();
        Map<String, String> signedByPath = signedUrlsFor(pageAssets);

        List<ListAssetsResponse.AssetDto> assets = pageAssets.stream()
                .map((a) -> toListAssetDto(a, signedByPath.getOrDefault(a.getObjectPath(), a.getSupabaseUrl())))
                .collect(Collectors.toList());

        // Get total count
        long total = mediaAssetDao.countByUserEmail(userEmail);

        return ListAssetsResponse.builder()
                .assets(assets)
                .total(total)
                .build();
    }

    /**
     * Get a single asset by ID (with ownership verification).
     */
    public ListAssetsResponse.AssetDto getAsset(String userEmail, String id) {
        MediaAsset asset = mediaAssetDao.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, 
                        "Asset not found or you don't have permission to access it"
                ));

        return toListAssetDto(asset, freshUrl(asset));
    }

    /**
     * Delete asset from both Supabase and database.
     */
    @Transactional
    public DeleteResponse delete(String userEmail, String id) {
        MediaAsset asset = mediaAssetDao.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Asset not found or you don't have permission to delete it"
                ));

        // Delete from Supabase
        supabaseStorage.delete(asset.getObjectPath());

        // Delete from database
        mediaAssetDao.deleteByIdAndUserEmail(id, userEmail);

        log.info("Asset deleted for user {}: {} ({})", userEmail, asset.getFileName(), id);

        return DeleteResponse.builder()
                .success(true)
                .build();
    }

    /**
     * Count assets for a user.
     */
    public long count(String userEmail) {
        return mediaAssetDao.countByUserEmail(userEmail);
    }

    /**
     * List unique folders for a user at a specific level.
     * If parentFolder is empty, returns top-level folders.
     * If parentFolder is "Photos", returns folders inside "Photos".
     */
    public List<String> listFolders(String userEmail, String parentFolder) {
        if (parentFolder == null) {
            parentFolder = "";
        }
        parentFolder = parentFolder.trim();

        String prefix = parentFolder.isEmpty() ? "" : parentFolder + "/";
        
        // Create regex pattern for MongoDB query
        String regexPattern = "^" + (prefix.isEmpty() ? "" : prefix.replace(".", "\\."));

        // Get folders from asset_folders collection
        List<AssetFolder> dbFolders = assetFolderDao.findByUserEmailAndFolderPathStartingWith(userEmail, regexPattern);
        
        // Get folders implied by assets
        List<MediaAsset> assets = mediaAssetDao.findByUserEmailAndFolderPathStartingWith(userEmail, regexPattern);
        
        log.info("ListFolders - User: {}, ParentFolder: '{}', Prefix: '{}', Regex: '{}', DB folders found: {}, Assets found: {}", 
                 userEmail, parentFolder, prefix, regexPattern, dbFolders.size(), assets.size());
        
        // Log all found folders for debugging
        for (AssetFolder folder : dbFolders) {
            log.debug("  DB Folder: {}", folder.getFolderPath());
        }
        for (MediaAsset asset : assets) {
            log.debug("  Asset in folder: {}", asset.getFolderPath());
        }

        List<String> folders = new ArrayList<>();

        // Add folders from database
        for (AssetFolder dbFolder : dbFolders) {
            String folderPath = dbFolder.getFolderPath();
            if (folderPath.startsWith(prefix)) {
                // Extract the next folder level
                String remainder = folderPath.substring(prefix.length());
                if (remainder.contains("/")) {
                    String nextFolder = remainder.substring(0, remainder.indexOf("/"));
                    if (!folders.contains(nextFolder) && !nextFolder.isEmpty()) {
                        folders.add(nextFolder);
                        log.debug("Added DB folder: {}", nextFolder);
                    }
                } else {
                    // Direct child folder
                    if (!folders.contains(remainder) && !remainder.isEmpty()) {
                        folders.add(remainder);
                        log.debug("Added direct DB folder: {}", remainder);
                    }
                }
            }
        }

        // Add folders implied by assets
        for (MediaAsset asset : assets) {
            String folderPath = asset.getFolderPath();
            
            if (folderPath.startsWith(prefix)) {
                // Extract the next folder level
                String remainder = folderPath.substring(prefix.length());
                if (remainder.contains("/")) {
                    String nextFolder = remainder.substring(0, remainder.indexOf("/"));
                    if (!folders.contains(nextFolder) && !nextFolder.isEmpty()) {
                        folders.add(nextFolder);
                        log.debug("Added asset-based folder: {}", nextFolder);
                    }
                }
            }
        }
        
        log.info("ListFolders - Returning {} folders: {}", folders.size(), folders);
        return folders;
    }

    /**
     * Create a new folder (persist to database).
     */
    public void createFolder(String userEmail, String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Folder path cannot be empty");
        }

        folderPath = folderPath.trim();

        // Validate folder path format
        if (folderPath.contains("\\") || folderPath.contains("//") || folderPath.startsWith("/") || folderPath.endsWith("/")) {
            throw new IllegalArgumentException("Invalid folder path format");
        }

        // Check if folder already exists
        if (assetFolderDao.existsByUserEmailAndFolderPath(userEmail, folderPath)) {
            log.warn("Folder already exists for user {}: {}", userEmail, folderPath);
            return;
        }

        // Create and save folder
        AssetFolder folder = AssetFolder.builder()
                .id(UUID.randomUUID().toString())
                .userEmail(userEmail)
                .folderPath(folderPath)
                .createdAt(Instant.now())
                .build();

        assetFolderDao.save(folder);
        log.info("Folder created for user {}: {}", userEmail, folderPath);
    }

    /**
     * Delete a folder and all assets inside it.
     */
    @Transactional
    public void deleteFolder(String userEmail, String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Cannot delete root folder");
        }

        folderPath = folderPath.trim();

        // Delete from database folders
        assetFolderDao.deleteByUserEmailAndFolderPath(userEmail, folderPath);
        
        // Also delete all folders that are children of this folder
        List<AssetFolder> childFolders = assetFolderDao.findByUserEmailAndFolderPathStartingWith(userEmail, folderPath + "/");
        for (AssetFolder childFolder : childFolders) {
            assetFolderDao.delete(childFolder);
        }

        // Delete all assets in this folder and subfolders
        List<MediaAsset> assetsInFolder = mediaAssetDao.findByUserEmailAndFolderPathStartingWith(userEmail, folderPath);
        for (MediaAsset asset : assetsInFolder) {
            // Delete from Supabase
            supabaseStorage.delete(asset.getObjectPath());
            // Delete from database
            mediaAssetDao.delete(asset);
        }

        log.info("Folder deleted for user {}: {} (deleted {} assets)", userEmail, folderPath, assetsInFolder.size());
    }

    /**
     * Sanitize filename to remove unsafe characters.
     */
    private String sanitizeFilename(String filename) {
        return filename
                .replaceAll("[^\\w.\\-]", "_")  // Replace non-alphanumeric (except . and -) with _
                .replaceAll("_+", "_")           // Replace multiple underscores with single
                .toLowerCase();                   // Lowercase for consistency
    }

    /**
     * Convert MediaAsset to UploadResponse.AssetDto, overriding the stored URL
     * with a freshly-signed one.
     */
    private UploadResponse.AssetDto toAssetDto(MediaAsset asset, String urlOverride) {
        return UploadResponse.AssetDto.builder()
                .id(asset.getId())
                .fileName(asset.getFileName())
                .mimeType(asset.getMimeType())
                .sizeBytes(asset.getSizeBytes())
                .supabaseUrl(urlOverride != null ? urlOverride : asset.getSupabaseUrl())
                .objectPath(asset.getObjectPath())
                .createdAt(asset.getCreatedAt())
                .tags(asset.getTags())
                .build();
    }

    /**
     * Convert MediaAsset to ListAssetsResponse.AssetDto, overriding the stored URL.
     */
    private ListAssetsResponse.AssetDto toListAssetDto(MediaAsset asset, String urlOverride) {
        return ListAssetsResponse.AssetDto.builder()
                .id(asset.getId())
                .fileName(asset.getFileName())
                .mimeType(asset.getMimeType())
                .sizeBytes(asset.getSizeBytes())
                .supabaseUrl(urlOverride != null ? urlOverride : asset.getSupabaseUrl())
                .objectPath(asset.getObjectPath())
                .createdAt(asset.getCreatedAt())
                .tags(asset.getTags())
                .build();
    }

    /**
     * Create a fresh signed URL for one asset, falling back to the stored URL
     * if signing is unavailable (e.g. storage misconfigured, asset lacking objectPath).
     */
    private String freshUrl(MediaAsset asset) {
        if (asset.getObjectPath() == null || asset.getObjectPath().isBlank()) {
            return asset.getSupabaseUrl();
        }
        String signed = supabaseStorage.createSignedUrl(asset.getObjectPath());
        return signed != null ? signed : asset.getSupabaseUrl();
    }

    /**
     * Batch-sign URLs for many assets in one round-trip.
     * Returns a map keyed by {@code objectPath}; missing entries indicate signing failures.
     */
    private Map<String, String> signedUrlsFor(List<MediaAsset> assets) {
        if (assets == null || assets.isEmpty()) return new HashMap<>();
        List<String> paths = assets.stream()
                .map(MediaAsset::getObjectPath)
                .filter((p) -> p != null && !p.isBlank())
                .collect(Collectors.toList());
        if (paths.isEmpty()) return new HashMap<>();
        return supabaseStorage.createSignedUrls(paths);
    }
}
