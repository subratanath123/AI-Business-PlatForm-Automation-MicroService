package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "ai_image_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIImageJob {
    @Id
    private String id;
    
    @Indexed
    private String userEmail;

    /** Clerk user id (JWT sub). The local folder name is a sanitized form of this id. */
    private String userId;
    
    private String prompt;
    private String size;
    private String quality;
    private String style;
    
    private String status; // pending, processing, completed, failed
    
    private String imageUrl;
    private String assetId;
    private String error;
    
    @Indexed
    private Instant createdAt;
    private Instant completedAt;
}
