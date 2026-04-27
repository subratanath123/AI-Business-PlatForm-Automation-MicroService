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
 * A user's connected WooCommerce store. Mirrors {@link ShopifyIntegration}
 * but uses WooCommerce REST API consumer-key / consumer-secret authentication
 * (generated via the WC Auth redirect flow) instead of a single OAuth token.
 */
@Document(collection = "woocommerce_integrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WooCommerceIntegration {

    @Id
    private String id;

    @Indexed
    private String userId;

    /** Base site URL, e.g. "https://mystore.com". No trailing slash, no /wp-json suffix. */
    private String storeUrl;

    /** Human-readable site title; populated from /wp-json during connect. */
    private String storeName;

    /** WooCommerce REST consumer key (ck_xxx), encrypted at rest. */
    private String encryptedConsumerKey;

    /** WooCommerce REST consumer secret (cs_xxx), encrypted at rest. */
    private String encryptedConsumerSecret;

    /** WC webhook resource id when the product-created webhook is registered. */
    private String webhookId;

    private boolean webhookEnabled;

    private boolean connected;

    /** True for the store currently selected as the user's active integration. */
    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;
}
