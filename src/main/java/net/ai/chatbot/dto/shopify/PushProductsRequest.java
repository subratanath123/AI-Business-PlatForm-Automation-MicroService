package net.ai.chatbot.dto.shopify;

import lombok.Data;

import java.util.List;

@Data
public class PushProductsRequest {
    private String jobId;
    private List<String> productIds;
}
