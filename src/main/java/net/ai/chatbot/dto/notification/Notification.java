package net.ai.chatbot.dto.notification;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    @Indexed
    private String userId;

    private NotificationType type;
    private String title;
    private String message;

    @Builder.Default
    private boolean read = false;

    private String actionUrl;

    private Map<String, Object> metadata;

    private Instant createdAt;
}
