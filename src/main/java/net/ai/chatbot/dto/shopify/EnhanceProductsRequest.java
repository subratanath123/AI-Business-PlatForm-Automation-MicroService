package net.ai.chatbot.dto.shopify;

import lombok.Data;

import java.util.List;

@Data
public class EnhanceProductsRequest {
    private String jobId;
    private List<ShopifyProductDto> products;
}
