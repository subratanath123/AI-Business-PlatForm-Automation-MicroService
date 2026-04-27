package net.ai.chatbot.dto.shopify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyProductDto {
    // ── Raw fields from Shopify ──────────────────────────────────────────────
    private String shopifyId;
    /** WooCommerce product id (populated when this DTO came from a Woo store). */
    private String wooId;
    private String title;
    private String bodyHtml;
    private String vendor;
    private String productType;
    private String tags;
    private String handle;
    private String seoTitle;        // title_tag metafield
    private String seoDescription;  // description_tag metafield
    private String status;          // active / draft / archived

    // ── Default-variant + image fields (used when creating a new product) ────
    // These are optional; only populated by the draft-publish flow so that
    // ShopifyApiService.createProduct can set a price, SKU, inventory, and
    // initial images in the same POST.
    private String  price;
    private String  compareAtPrice;
    private String  sku;
    private Integer inventoryQuantity;
    private java.util.List<String> images;

    // ── AI-enhanced fields ───────────────────────────────────────────────────
    private String enhancedTitle;
    private String enhancedBodyHtml;
    private String enhancedTags;
    private String enhancedProductType;
    private String enhancedHandle;
    private String enhancedSeoTitle;
    private String enhancedSeoDescription;
}
