package net.ai.chatbot.dto.amazon;

import lombok.Data;

import java.util.List;

/**
 * Body of {@code PUT /v1/api/amazon/products/{sku}} (and the equivalent
 * draft endpoint). Every field is optional; only non-null fields are
 * written back to Amazon.
 */
@Data
public class AmazonDirectProductUpdateRequest {
    private String title;
    private String bodyHtml;
    private String brand;
    private String category;
    private List<String> bulletPoints;
    private String searchKeywords;
    private String condition;
    private String productType;

    private String status;             // ACTIVE / INACTIVE
    private String price;
    private String listPrice;
    private String sellerSku;
    private Integer inventoryQuantity;
    private List<String> images;
}
