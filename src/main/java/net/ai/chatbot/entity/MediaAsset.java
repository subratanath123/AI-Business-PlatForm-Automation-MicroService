package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Media asset stored in Supabase Storage.
 * Backend uploads files to Supabase and tracks metadata here.
 */
@Document(collection = "media_assets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset {

    @Id
    private String id;

    /**
     * User email from Clerk JWT (indexed for fast queries)
     */
    @Indexed
    private String userEmail;

    /**
     * Clerk user id (JWT sub) when the upload was authenticated.
     * Local files live under a sanitized form of this id.
     */
    private String userId;

    /**
     * Original filename for display
     */
    private String fileName;

    private String fileType;
    private long fileSize;

    /**
     * MIME type (e.g., image/jpeg, video/mp4)
     */
    private String mimeType;

    /**
     * File size in bytes
     */
    private Long sizeBytes;

    /**
     * Full public CDN URL from Supabase
     * Return this to frontend
     */
    private String supabaseUrl;

    /**
     * Bucket-relative path (used for deletion)
     * Format: social-posts/{userEmail}/{timestamp}_{filename}
     */
    private String objectPath;

    /**
     * Upload timestamp
     */
    private Instant createdAt;
    private Instant uploadedAt;

    /**
     * Optional tags for organization
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * Folder path for hierarchical organization (e.g., "My Photos/Vacation")
     * Empty string means root level
     */
    @Builder.Default
    private String folderPath = "";

    /**
     * Folder path for hierarchical organization (e.g., "My Photos/Vacation")
     * Empty string means root level
     */
    @Builder.Default
    private String source = "";
}
