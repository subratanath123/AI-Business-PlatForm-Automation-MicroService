package net.ai.chatbot.controller.mediaasset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.mediaasset.DeleteResponse;
import net.ai.chatbot.dto.mediaasset.ListAssetsResponse;
import net.ai.chatbot.dto.mediaasset.UploadResponse;
import net.ai.chatbot.service.mediaasset.MediaAssetService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Media Assets API - Backend-owned Supabase uploads.
 * 
 * All endpoints require JWT authentication.
 * Files are uploaded to Supabase by the backend, not the frontend.
 * 
 * Base URL: /v1/api/assets
 */
@RestController
@RequestMapping("/v1/api/assets")
@RequiredArgsConstructor
@Slf4j
public class MediaAssetController {

    private final MediaAssetService mediaAssetService;

    /**
     * Upload one or more files to a specific folder.
     * 
     * POST /v1/api/assets/upload?folderPath=Photos%2FVacation
     * Content-Type: multipart/form-data
     * 
     * Form field: files (one or more files)
     * Query param: folderPath (optional, defaults to root)
     * 
     * Example cURL:
     * curl -X POST "http://localhost:8080/v1/api/assets/upload?folderPath=Photos" \
     *   -H "Authorization: Bearer <jwt>" \
     *   -F "files=@photo.jpg" \
     *   -F "files=@video.mp4"
     * 
     * Response:
     * {
     *   "uploaded": [
     *     {
     *       "id": "550e8400-...",
     *       "fileName": "photo.jpg",
     *       "mimeType": "image/jpeg",
     *       "sizeBytes": 204800,
     *       "supabaseUrl": "https://xxx.supabase.co/.../photo.jpg",
     *       "objectPath": "social-posts/user@example.com/Photos/123_photo.jpg",
     *       "createdAt": "2024-01-15T10:00:00Z",
     *       "tags": [],
     *       "folderPath": "Photos"
     *     }
     *   ],
     *   "failed": []
     * }
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false, defaultValue = "") String folderPath) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized upload attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body("No files provided");
        }

        UploadResponse response = mediaAssetService.uploadAll(userEmail, AuthUtils.getUserId(), files, folderPath);
        return ResponseEntity.ok(response);
    }

    /**
     * List assets for the authenticated user.
     * 
     * GET /v1/api/assets?type=image&search=photo&limit=100&offset=0
     * 
     * Query parameters:
     * - type: Filter by MIME type prefix (image, video)
     * - search: Case-insensitive filename contains
     * - limit: Max items to return (default 100, max 200)
     * - offset: Pagination offset (default 0)
     * 
     * Response:
     * {
     *   "assets": [...],
     *   "total": 42
     * }
     */
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized list attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        ListAssetsResponse response = mediaAssetService.list(userEmail, type, search, limit, offset);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single asset by ID.
     * 
     * GET /v1/api/assets/{id}
     * 
     * Response: Same shape as a single item from list endpoint
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAsset(@PathVariable String id) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized get asset attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        ListAssetsResponse.AssetDto asset = mediaAssetService.getAsset(userEmail, id);
        return ResponseEntity.ok(asset);
    }

    /**
     * Delete an asset from both Supabase and database.
     * 
     * DELETE /v1/api/assets/{id}
     * 
     * Response:
     * {
     *   "success": true
     * }
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized delete attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        DeleteResponse response = mediaAssetService.delete(userEmail, id);
        return ResponseEntity.ok(response);
    }

    /**
     * List folders at a specific level.
     * 
     * GET /v1/api/assets/folders?parentFolder=Photos
     * 
     * Query parameters:
     * - parentFolder: Parent folder path (optional, defaults to root)
     * 
     * Response:
     * [
     *   "Vacation",
     *   "Work",
     *   "Archive"
     * ]
     */
    @GetMapping("/folders")
    public ResponseEntity<?> listFolders(
            @RequestParam(required = false, defaultValue = "") String parentFolder) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized list folders attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        java.util.List<String> folders = mediaAssetService.listFolders(userEmail, parentFolder);
        return ResponseEntity.ok(folders);
    }

    /**
     * Create a new folder (virtual - implicitly created when assets are uploaded).
     * 
     * POST /v1/api/assets/folders
     * 
     * Request body:
     * {
     *   "folderPath": "Photos/Vacation"
     * }
     * 
     * Response:
     * {
     *   "success": true,
     *   "folderPath": "Photos/Vacation"
     * }
     */
    @PostMapping("/folders")
    public ResponseEntity<?> createFolder(@RequestBody FolderRequest request) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized create folder attempt - no user email");
            return ResponseEntity.status(401).body(new java.util.HashMap<String, Object>() {{
                put("error", "Unauthorized");
                put("message", "User email not found in token");
            }});
        }

        if (request == null || request.getFolderPath() == null || request.getFolderPath().trim().isEmpty()) {
            log.warn("Invalid folder path: {}", request == null ? "null request" : request.getFolderPath());
            return ResponseEntity.badRequest().body(new java.util.HashMap<String, Object>() {{
                put("error", "Invalid folder path");
                put("message", "Folder path cannot be empty");
            }});
        }

        try {
            String folderPath = request.getFolderPath().trim();
            log.info("Creating folder for user {}: {}", userEmail, folderPath);
            mediaAssetService.createFolder(userEmail, folderPath);
            log.info("Folder created successfully for user {}: {}", userEmail, folderPath);
            return ResponseEntity.ok(new FolderResponse(true, folderPath));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid folder format for user {}: {}", userEmail, e.getMessage());
            return ResponseEntity.badRequest().body(new java.util.HashMap<String, Object>() {{
                put("error", "Invalid folder format");
                put("message", e.getMessage());
            }});
        } catch (Exception e) {
            log.error("Error creating folder for user {}: {}", userEmail, e.getMessage(), e);
            return ResponseEntity.status(500).body(new java.util.HashMap<String, Object>() {{
                put("error", "Internal server error");
                put("message", e.getMessage());
            }});
        }
    }

    /**
     * Delete a folder and all assets inside it.
     * 
     * DELETE /v1/api/assets/folders?folderPath=Photos%2FVacation
     * 
     * Query parameters:
     * - folderPath: Folder path to delete (required)
     * 
     * Response:
     * {
     *   "success": true
     * }
     */
    @DeleteMapping("/folders")
    public ResponseEntity<?> deleteFolder(
            @RequestParam String folderPath) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized delete folder attempt");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        if (folderPath == null || folderPath.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Folder path cannot be empty");
        }

        try {
            mediaAssetService.deleteFolder(userEmail, folderPath);
            return ResponseEntity.ok(new FolderResponse(true, folderPath));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Helper DTOs for folder operations
    public static class FolderRequest {
        private String folderPath;

        public FolderRequest() {}
        public FolderRequest(String folderPath) {
            this.folderPath = folderPath;
        }

        public String getFolderPath() {
            return folderPath;
        }

        public void setFolderPath(String folderPath) {
            this.folderPath = folderPath;
        }
    }

    public static class FolderResponse {
        private boolean success;
        private String folderPath;

        public FolderResponse(boolean success, String folderPath) {
            this.success = success;
            this.folderPath = folderPath;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFolderPath() {
            return folderPath;
        }
    }
}
