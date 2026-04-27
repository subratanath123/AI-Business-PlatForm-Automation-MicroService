package net.ai.chatbot.dto.ebay;

import lombok.Data;

/**
 * Persist the seller-scoped defaults used when publishing drafts:
 * category, location, and the fulfillment / payment / return policies
 * required by the eBay Inventory API to publish an offer.
 */
@Data
public class EbaySetDefaultsRequest {
    private String categoryId;
    private String merchantLocationKey;
    private String fulfillmentPolicyId;
    private String paymentPolicyId;
    private String returnPolicyId;
}
