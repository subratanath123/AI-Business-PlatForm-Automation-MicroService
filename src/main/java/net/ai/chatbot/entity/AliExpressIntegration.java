package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Connected AliExpress seller account (TOP session / OAuth refresh token).
 */
@Document(collection = "aliexpress_integrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliExpressIntegration {

    @Id
    private String id;

    @Indexed
    private String userId;

    /** Stable seller id from the token response (account / seller id). */
    private String sellerId;

    /** Seller nick from AliExpress (display). */
    private String sellerLoginId;

    private String storeName;

    /** Long-lived refresh token, encrypted at rest. */
    private String encryptedRefreshToken;

    /** Default locale for product.edit subject/description blocks (e.g. en, es). */
    private String defaultContentLocale;

    private boolean connected;

    /** Currently selected store when the user has several connections. */
    private boolean active;

    /** Reserved for future message-push wiring; not used yet. */
    private boolean notificationsEnabled;

    private Instant createdAt;

    private Instant updatedAt;
}
