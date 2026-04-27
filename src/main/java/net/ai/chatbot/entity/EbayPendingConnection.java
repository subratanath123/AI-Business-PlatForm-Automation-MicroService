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
 * Short-lived record used to correlate the eBay OAuth 2.0 authorization
 * redirect with the browser-side finalize call. Created when the user
 * clicks "Connect"; the {@code state} param is stamped on both the
 * authorize URL and the frontend callback URL so another user can't
 * smuggle in someone else's authorization.
 */
@Document(collection = "ebay_pending_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EbayPendingConnection {

    @Id
    private String id;

    @Indexed(unique = true)
    private String state;

    @Indexed
    private String userId;

    /** PRODUCTION | SANDBOX */
    private String environment;

    private Instant createdAt;

    private Instant updatedAt;
}
