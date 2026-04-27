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
 * Amazon-specific enhancement job. Kept deliberately separate from
 * {@link ProductEnhancementJob} so the nested {@code ProductItem}
 * can carry Amazon concepts (ASIN, seller SKU, marketplace, product
 * type, bullet points, search keywords) without polluting the
 * Shopify/Woo data model.
 */
@Document(collection = "amazon_product_enhancement_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmazonProductEnhancementJob {

    @Id
    private String id;

    @Indexed
    private String userId;

    /** Always "AMAZON" — kept for parity with ProductEnhancementJob payloads. */
    private String platform;

    /** PENDING, PROCESSING, ENHANCED, PUBLISHED, FAILED. */
    private String status;

    /** Marketplace this job is scoped to (e.g. ATVPDKIKX0DER for US). */
    private String marketplaceId;

    private List<AmazonProductItem> rawProducts;

    private List<AmazonProductItem> enhancedProducts;

    private String errorMessage;

    /** UPLOAD, SYNC, SQS_NOTIFICATION. */
    private String source;

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmazonProductItem {
        /** Stable local identifier — present for every item, including drafts. */
        private String localId;

        // ── Amazon identifiers ───────────────────────────────────────────────
        /** Amazon Standard Identification Number (catalog-level product id). */
        private String asin;
        /** Seller's own SKU (the listing id within the seller's catalog). */
        private String sellerSku;
        /** Amazon marketplace id (e.g. ATVPDKIKX0DER for US). */
        private String marketplaceId;
        /**
         * Amazon ProductType slug used by Listings Items API
         * (e.g. "PRODUCT", "SHOES", "BOOK"). Required on publish.
         */
        private String productType;

        // ── Raw product data ────────────────────────────────────────────────
        private String title;             // item_name
        private String bodyHtml;          // product_description (HTML tolerated)
        private String brand;             // brand
        private String category;          // product_category (free-text)
        /** Amazon allows up to 5 bullet-point feature lines. */
        private List<String> bulletPoints;
        /** Backend search keywords (generic_keyword). */
        private String searchKeywords;
        /** Listing condition: new_new, used_good, etc. Default "new_new". */
        private String condition;

        // ── Pricing / inventory / images (used at publish time) ─────────────
        private String  price;
        private String  listPrice;        // "was" price
        private Integer inventoryQuantity;
        /** Amazon listing status: ACTIVE or INACTIVE. */
        private String  amazonStatus;
        /** Publicly reachable image URLs in display order. */
        private List<String> images;

        // ── AI-enhanced values ──────────────────────────────────────────────
        private String enhancedTitle;
        private String enhancedBodyHtml;
        private String enhancedBrand;
        private String enhancedCategory;
        private List<String> enhancedBulletPoints;
        private String enhancedSearchKeywords;

        // AI enhancement lifecycle: PENDING, ENHANCED, PUBLISHED, FAILED.
        private String status;

        // Sync lifecycle (mirrors Shopify/Woo):
        //   DRAFT         → local only
        //   SYNCED        → in Amazon, local copy matches
        //   PENDING_SYNC  → in Amazon, local edits not yet pushed
        //   PUBLISHED     → just pushed to Amazon
        private String lifecycleStatus;
    }
}
