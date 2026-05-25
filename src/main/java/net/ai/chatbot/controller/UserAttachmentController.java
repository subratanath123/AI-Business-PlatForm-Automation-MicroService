package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Attachment;
import net.ai.chatbot.dto.AttachmentStorageResult;
import net.ai.chatbot.dto.FileMetadata;
import net.ai.chatbot.service.AttachmentStorageService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Controller for user-specific file upload/download operations.
 * All operations scoped to authenticated user (from JWT).
 */
@RestController
@RequestMapping("/v1/api/user/attachments")
@Slf4j
public class UserAttachmentController {

    @Autowired
    private AttachmentStorageService attachmentStorageService;

    /**
     * Upload file for authenticated user.
     * Files stored under user's ID (from JWT).
     * 
     * POST /v1/api/user/attachments/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<AttachmentStorageResult> uploadFile(
            @RequestParam("file") MultipartFile file) throws Exception {

        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        log.info("User {} uploading: {}, size: {}", userId, file.getOriginalFilename(), file.getSize());

        Attachment attachment = Attachment.builder()
                .name(file.getOriginalFilename())
                .chatbotId(userId) // Using chatbotId field to store userId
                .type(file.getContentType())
                .size(file.getSize())
                .length(file.getSize())
                .data(file.getBytes())
                .uploadedAt(new Date())
                .build();

        AttachmentStorageResult result = attachmentStorageService.storeAttachmentInMongoDB(attachment, userId);
        result.setDownloadUrl("/v1/api/user/attachments/download/" + result.getFileId());

        log.info("User {} uploaded file: {}", userId, result.getFileId());

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Download file by fileId (user-specific).
     * Only returns file if it belongs to authenticated user.
     * 
     * GET /v1/api/user/attachments/download/{fileId}
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId) {

        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        log.info("User {} downloading file: {}", userId, fileId);

        FileMetadata metadata = attachmentStorageService.getFileMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] content = attachmentStorageService.getFileContent(fileId);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getFileName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, metadata.getMimeType())
                .body(content);
    }

    /**
     * Get file metadata (user-specific).
     * 
     * GET /v1/api/user/attachments/metadata/{fileId}
     */
    @GetMapping("/metadata/{fileId}")
    public ResponseEntity<FileMetadata> getFileMetadata(@PathVariable String fileId) {

        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        FileMetadata metadata = attachmentStorageService.getFileMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(metadata);
    }

    /**
     * List all attachments for authenticated user.
     * 
     * GET /v1/api/user/attachments
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listAttachments() {

        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        List<FileMetadata> files = attachmentStorageService.listAttachments(userId);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "totalFiles", files.size(),
                "files", files
        ));
    }

    /**
     * Delete attachment (user-specific).
     * 
     * DELETE /v1/api/user/attachments/{fileId}
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteAttachment(@PathVariable String fileId) {

        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        log.info("User {} deleting file: {}", userId, fileId);

        boolean deleted = attachmentStorageService.deleteAttachment(fileId, userId);

        return ResponseEntity.ok(Map.of(
                "success", deleted,
                "message", deleted ? "File deleted" : "File not found"
        ));
    }
}
