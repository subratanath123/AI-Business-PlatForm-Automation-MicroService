package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "shopify_integrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyIntegration {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String shopDomain;

    private String shopName;

    private String encryptedAccessToken;

    private String webhookId;

    private boolean webhookEnabled;

    private boolean connected;

    /** True for the store currently selected as the user's active integration. */
    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;
}
