package net.ai.chatbot.dto.woocommerce;

import lombok.Data;

import java.util.List;

@Data
public class WooPushProductsRequest {
    private String jobId;
    /** Local ids (ProductItem.localId) of the products to push — not Woo ids. */
    private List<String> productIds;
}
