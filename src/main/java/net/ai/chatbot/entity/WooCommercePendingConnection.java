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
 * Short-lived record used to shuttle WooCommerce REST credentials from the
 * public WC-Auth callback (a server-to-server POST from the user's store)
 * to the authenticated "finalize" call made by the user's browser.
 * <p>
 * The document is keyed by a random {@code nonce} that both the browser and
 * the store callback share. Records older than a few minutes should be
 * treated as expired by the finalize flow.
 */
@Document(collection = "woocommerce_pending_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WooCommercePendingConnection {

    @Id
    private String id;

    /** Random handshake token embedded in both the callback_url and return_url. */
    @Indexed(unique = true)
    private String nonce;

    /** Clerk user id the connection belongs to. */
    @Indexed
    private String userId;

    /** Store URL the user entered (no trailing slash). */
    private String storeUrl;

    /** Encrypted consumer key written by the public callback. */
    private String encryptedConsumerKey;

    /** Encrypted consumer secret written by the public callback. */
    private String encryptedConsumerSecret;

    /** Scope returned by WC ("read" or "read_write"). */
    private String keyPermissions;

    /** True once WC has posted the credentials; false while we're still waiting. */
    private boolean credentialsReceived;

    private Instant createdAt;

    private Instant updatedAt;
}
