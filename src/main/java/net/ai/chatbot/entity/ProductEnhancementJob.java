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

@Document(collection = "product_enhancement_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEnhancementJob {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String platform; // SHOPIFY

    private String status; // PENDING, PROCESSING, ENHANCED, PUBLISHED, FAILED

    private List<ProductItem> rawProducts;

    private List<ProductItem> enhancedProducts;

    private String errorMessage;

    private String source; // UPLOAD, SYNC, WEBHOOK

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductItem {
        // Stable local identifier — present for every item (including drafts that
        // have not yet been pushed to Shopify). Backfilled on read if missing.
        private String localId;

        // ── Raw values ──────────────────────────────────────────────────────
        private String shopifyId;
        private String title;
        private String bodyHtml;
        private String vendor;
        private String productType;
        private String tags;
        private String handle;
        private String seoTitle;
        private String seoDescription;

        // ── Shopify-ready draft fields (single default variant) ─────────────
        // These live on the local draft so the editor can capture all the
        // information needed to create a real Shopify product on publish —
        // without having to round-trip through the ShopifyProductDto first.
        private String  price;             // e.g. "19.99" (string to preserve trailing zeros)
        private String  compareAtPrice;    // e.g. "24.99" — optional "was" price
        private String  sku;               // stock keeping unit
        private Integer inventoryQuantity; // on-hand quantity for the default variant
        // Target Shopify status on publish: active | draft | archived. Drafts
        // default to "active" but the user may explicitly choose "draft" so it
        // gets created as a Shopify Draft Product.
        private String  shopifyStatus;

        // Local image library references. Each entry is a publicly reachable
        // URL (typically from our Supabase-backed asset library) that Shopify
        // will ingest when the product is created. Order = display order.
        private java.util.List<String> images;

        // ── AI-enhanced values ───────────────────────────────────────────────
        private String enhancedTitle;
        private String enhancedBodyHtml;
        private String enhancedTags;
        private String enhancedProductType;
        private String enhancedHandle;
        private String enhancedSeoTitle;
        private String enhancedSeoDescription;

        // AI enhancement lifecycle: PENDING, ENHANCED, PUBLISHED, FAILED
        private String status;

        // Sync lifecycle (independent of AI status):
        //   DRAFT         → uploaded locally, never pushed to Shopify
        //   SYNCED        → exists in Shopify and local copy matches
        //   PENDING_SYNC  → exists in Shopify but local copy has unpushed edits
        //   PUBLISHED     → draft was just pushed to Shopify (transitions to SYNCED on next read)
        private String lifecycleStatus;
    }
}
