package net.ai.chatbot.dto.admin;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Records content moderation violations for audit and user tracking.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "content_violations")
public class ContentViolation {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String userEmail;

    @Indexed
    private String violationType; // VIOLENCE, ADULT, HATE_SPEECH, KEYWORD, etc.

    private String featureType; // IMAGE, VIDEO, CONTENT, CODE, CHAT

    private String inputContent; // The content that triggered the violation (truncated)

    private List<String> triggeredKeywords;

    private List<String> triggeredCategories;

    private String actionTaken; // BLOCKED, WARNED, LOGGED

    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    @Builder.Default
    private boolean reviewed = false;

    private String reviewedBy;
    private Instant reviewedAt;
    private String reviewNotes;

    // If false positive, admin can mark it
    @Builder.Default
    private boolean falsePositive = false;

    private String ipAddress;
    private String userAgent;
    private String sessionId;

    @Indexed
    private Instant createdAt;

    @Builder.Default
    private List<String> metadata = new ArrayList<>();
}
