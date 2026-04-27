package net.ai.chatbot.dto.ebay;

import lombok.Data;

import java.util.List;

@Data
public class EbayPushProductsRequest {
    private String jobId;
    /** Local ids (EbayProductItem.localId) of the products to push — not SKUs or itemIds. */
    private List<String> productIds;
}
