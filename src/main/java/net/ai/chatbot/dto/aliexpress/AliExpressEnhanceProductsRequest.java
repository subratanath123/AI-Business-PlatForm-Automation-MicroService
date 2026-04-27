package net.ai.chatbot.dto.aliexpress;

import lombok.Data;

import java.util.List;

@Data
public class AliExpressEnhanceProductsRequest {
    private List<AliExpressProductDto> products;
}
