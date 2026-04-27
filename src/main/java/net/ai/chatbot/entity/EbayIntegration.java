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
 * A user's connected eBay seller account. Connected via eBay OAuth 2.0
 * (user token grant) using the seller's RuName as {@code redirect_uri}.
 * A single authorization can be used against many marketplaces
 * (EBAY_US, EBAY_GB, EBAY_DE, …) — {@link #availableMarketplaceIds}
 * captures what the seller participates in and
 * {@link #activeMarketplaceId} is the one currently targeted by writes.
 */
@Document(collection = "ebay_integrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EbayIntegration {

    @Id
    private String id;

    @Indexed
    private String userId;

    /** Stable seller identity returned by eBay (the {@code userId} in the Identity API). */
    private String sellerId;

    /** Human-readable store/eIAS name provided at connect time or the sellerId as a fallback. */
    private String storeName;

    /**
     * eBay environment the tokens belong to. One of: PRODUCTION | SANDBOX.
     * Drives both the auth host and the REST host.
     */
    private String environment;

    /** Long-lived refresh token, encrypted at rest. */
    private String encryptedRefreshToken;

    /** Space-separated OAuth scopes granted at consent time. */
    private String scopes;

    /** Marketplaces the seller has indicated they operate in (e.g. EBAY_US, EBAY_GB). */
    private List<String> availableMarketplaceIds;

    /** The marketplace currently targeted by product sync / publish. */
    private String activeMarketplaceId;

    /** Default eBay categoryId used when publishing drafts that don't specify one. */
    private String defaultCategoryId;

    /** Default merchantLocationKey (warehouse/fulfillment location) for offers. */
    private String defaultMerchantLocationKey;

    /** Default payment/return/fulfillment policy ids for offers, per marketplace. */
    private String defaultFulfillmentPolicyId;
    private String defaultPaymentPolicyId;
    private String defaultReturnPolicyId;

    /** Notification API destination id (webhook) registered with eBay. */
    private String notificationDestinationId;

    /** Subscription ids (eg. "INVENTORY_ITEM.CREATED:..."). */
    private List<String> notificationSubscriptionIds;

    /** eBay-speak for "webhooks enabled" — drives the "Auto-enhance" toggle. */
    private boolean notificationsEnabled;

    private boolean connected;

    /** True for the store currently selected as the user's active integration. */
    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;
}
