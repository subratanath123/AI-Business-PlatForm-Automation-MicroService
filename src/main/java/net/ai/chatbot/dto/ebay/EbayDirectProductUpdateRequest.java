package net.ai.chatbot.dto.ebay;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Body of {@code PUT /v1/api/ebay/products/{sku}} (and the equivalent
 * draft endpoint). Every field is optional; only non-null fields are
 * written back to eBay.
 */
@Data
public class EbayDirectProductUpdateRequest {
    private String title;
    private String bodyHtml;
    private String subtitle;
    private String brand;
    private String mpn;
    private String upc;
    private String condition;
    private String conditionDescription;
    private String categoryId;
    private Map<String, List<String>> aspects;

    private String status;             // ACTIVE / INACTIVE / DRAFT
    private String price;
    private String currency;
    private String sku;
    private Integer inventoryQuantity;
    private List<String> images;

    /** Offer-level policies — optional, fall back to the store defaults. */
    private String fulfillmentPolicyId;
    private String paymentPolicyId;
    private String returnPolicyId;
    private String merchantLocationKey;
}
