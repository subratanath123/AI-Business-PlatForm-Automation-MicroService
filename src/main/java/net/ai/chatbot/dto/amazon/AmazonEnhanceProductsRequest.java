package net.ai.chatbot.dto.amazon;

import lombok.Data;

import java.util.List;

@Data
public class AmazonEnhanceProductsRequest {
    private List<AmazonProductDto> products;
}
