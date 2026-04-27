package net.ai.chatbot.dto.amazon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Platform-agnostic carrier for Amazon product data. Mirrors
 * {@link net.ai.chatbot.dto.shopify.ShopifyProductDto} but carries
 * Amazon-native concepts (ASIN, seller SKU, marketplace id, bullet
 * points, search keywords) rather than Shopify/Woo concepts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmazonProductDto {

    // ── Identifiers ──────────────────────────────────────────────────────────
    private String asin;
    private String sellerSku;
    private String marketplaceId;
    private String productType;

    // ── Raw fields ───────────────────────────────────────────────────────────
    private String title;
    private String bodyHtml;
    private String brand;
    private String category;
    private List<String> bulletPoints;
    private String searchKeywords;
    private String condition;

    private String status; // ACTIVE / INACTIVE

    // ── Default-variant + image fields (used when creating a new listing) ────
    private String  price;
    private String  listPrice;
    private Integer inventoryQuantity;
    private List<String> images;

    // ── AI-enhanced fields ───────────────────────────────────────────────────
    private String enhancedTitle;
    private String enhancedBodyHtml;
    private String enhancedBrand;
    private String enhancedCategory;
    private List<String> enhancedBulletPoints;
    private String enhancedSearchKeywords;
}
