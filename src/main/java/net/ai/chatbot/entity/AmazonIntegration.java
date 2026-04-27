package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A user's connected Amazon Seller Central account. Connected via
 * Login-with-Amazon (LWA) OAuth. Each integration holds a long-lived
 * refresh token used to mint short-lived SP-API access tokens.
 * <p>
 * Unlike Shopify/WooCommerce a single LWA authorization can grant access
 * to many marketplaces (regions); {@link #availableMarketplaceIds} captures
 * what the seller opted into and {@link #activeMarketplaceId} is the one
 * currently targeted by product operations.
 */
@Document(collection = "amazon_integrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmazonIntegration {

    @Id
    private String id;

    @Indexed
    private String userId;

    /** Amazon Seller Central selling-partner id (e.g. "A2EUQ1WTGCTBG2"). */
    private String sellerId;

    /** Human-readable name the user provided at connect time (or the sellerId if omitted). */
    private String storeName;

    /**
     * SP-API region the refresh token was minted in.
     * One of: NA (us/ca/mx/br), EU (uk/de/fr/es/it/nl/se/pl/tr/ae/in/sa/eg),
     * FE (jp/au/sg).
     */
    private String region;

    /** Long-lived refresh token, encrypted at rest. */
    private String encryptedRefreshToken;

    /** List of marketplace ids the seller has granted access to. */
    private List<String> availableMarketplaceIds;

    /** The marketplace currently used for writes (product create/update/sync). */
    private String activeMarketplaceId;

    /**
     * Default Amazon ProductType slug used when publishing a draft that
     * doesn't explicitly set one (e.g. "PRODUCT", "SHOES", "BOOK").
     */
    private String defaultProductType;

    /** SP-API Notifications destination id (AWS SQS) — present when SQS is wired. */
    private String notificationDestinationId;

    /** Most recent Notifications subscription id for PRODUCT_TYPE_DEFINITIONS_CHANGE / LISTINGS_ITEM_ISSUES_CHANGE / ANY_OFFER_CHANGED etc. */
    private List<String> notificationSubscriptionIds;

    private boolean sqsEnabled;

    private boolean connected;

    /** True for the store currently selected as the user's active integration. */
    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;
}
