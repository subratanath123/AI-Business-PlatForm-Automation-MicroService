package net.ai.chatbot.dto.ebay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Platform-agnostic carrier for eBay product data. Mirrors the Amazon
 * DTO but carries eBay-native concepts (SKU, itemId, offerId, categoryId,
 * aspects / item specifics) rather than Amazon concepts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EbayProductDto {

    // ── Identifiers ──────────────────────────────────────────────────────────
    private String sku;
    private String itemId;
    private String offerId;
    private String marketplaceId;
    private String categoryId;

    // ── Raw fields ───────────────────────────────────────────────────────────
    private String title;
    private String bodyHtml;
    private String subtitle;
    private String brand;
    private String mpn;
    private String upc;
    private String condition;
    private String conditionDescription;
    /** Item specifics: top-level key → list of allowed values. */
    private Map<String, List<String>> aspects;

    private String status; // ACTIVE / INACTIVE / DRAFT

    // ── Default-offer fields (used when creating a new listing) ──────────────
    private String  price;
    private String  currency;
    private Integer inventoryQuantity;
    private List<String> images;

    // ── AI-enhanced fields ───────────────────────────────────────────────────
    private String enhancedTitle;
    private String enhancedBodyHtml;
    private String enhancedSubtitle;
    private String enhancedBrand;
    private Map<String, List<String>> enhancedAspects;
}
