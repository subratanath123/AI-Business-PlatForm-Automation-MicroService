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
 * eBay-specific enhancement job. Kept deliberately separate from
 * {@link ProductEnhancementJob} and {@link AmazonProductEnhancementJob}
 * so the nested {@code ProductItem} can carry eBay concepts (offerId,
 * categoryId, itemSpecifics, aspects, marketplace scoping) without
 * polluting the Shopify/Woo/Amazon data models.
 */
@Document(collection = "ebay_product_enhancement_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EbayProductEnhancementJob {

    @Id
    private String id;

    @Indexed
    private String userId;

    /** Always "EBAY" — kept for parity with the other platform jobs. */
    private String platform;

    /** PENDING, PROCESSING, ENHANCED, PUBLISHED, FAILED. */
    private String status;

    /** Marketplace this job is scoped to (e.g. EBAY_US). */
    private String marketplaceId;

    private List<EbayProductItem> rawProducts;

    private List<EbayProductItem> enhancedProducts;

    private String errorMessage;

    /** UPLOAD, SYNC, NOTIFICATION. */
    private String source;

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EbayProductItem {
        /** Stable local identifier — present for every item, including drafts. */
        private String localId;

        // ── eBay identifiers ────────────────────────────────────────────────
        /** Inventory item SKU — the stable seller-controlled id keyed in eBay. */
        private String sku;
        /** eBay-side Item ID (populated once an offer has been published). */
        private String itemId;
        /** Offer ID created for this SKU (required to publish/withdraw). */
        private String offerId;
        /** Marketplace this item is offered on (EBAY_US, EBAY_GB, …). */
        private String marketplaceId;
        /** eBay leaf category id used for the offer. */
        private String categoryId;

        // ── Raw product data ────────────────────────────────────────────────
        private String title;
        private String bodyHtml;          // long description
        private String subtitle;
        private String brand;
        private String mpn;               // Manufacturer Part Number
        private String upc;
        private String condition;         // NEW, USED_EXCELLENT, etc.
        private String conditionDescription;
        /** Free-form eBay "item specifics" — top-level key → values. */
        private java.util.Map<String, List<String>> aspects;

        // ── Pricing / inventory / images ────────────────────────────────────
        private String  price;
        private String  currency;         // USD, GBP, EUR …
        private Integer inventoryQuantity;
        /** Listing status on eBay: ACTIVE / INACTIVE / DRAFT. */
        private String  ebayStatus;
        /** Publicly reachable image URLs in display order (up to 24). */
        private List<String> images;

        // ── AI-enhanced values ──────────────────────────────────────────────
        private String enhancedTitle;
        private String enhancedBodyHtml;
        private String enhancedSubtitle;
        private String enhancedBrand;
        private java.util.Map<String, List<String>> enhancedAspects;

        // AI enhancement lifecycle: PENDING, ENHANCED, PUBLISHED, FAILED.
        private String status;

        // Sync lifecycle (mirrors the other platforms):
        //   DRAFT         → local only
        //   SYNCED        → in eBay, local copy matches
        //   PENDING_SYNC  → in eBay, local edits not yet pushed
        //   PUBLISHED     → just pushed to eBay
        private String lifecycleStatus;
    }
}
