package net.ai.chatbot.dto.amazon;

import lombok.Data;

import java.util.List;

@Data
public class AmazonPushProductsRequest {
    private String jobId;
    /** Local ids (AmazonProductItem.localId) of the products to push — not ASINs or SKUs. */
    private List<String> productIds;
}
