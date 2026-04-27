package net.ai.chatbot.dto.ebay;

import lombok.Data;

import java.util.List;

@Data
public class EbayEnhanceProductsRequest {
    private List<EbayProductDto> products;
}
