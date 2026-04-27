package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "aliexpress_pending_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliExpressPendingConnection {

    @Id
    private String id;

    @Indexed(unique = true)
    private String state;

    @Indexed
    private String userId;

    private Instant createdAt;

    private Instant updatedAt;
}
