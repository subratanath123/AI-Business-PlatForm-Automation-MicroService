package net.ai.chatbot.dto.aliexpress;

import lombok.Data;

import java.util.List;

@Data
public class AliExpressPushProductsRequest {
    private String jobId;
    private List<String> productIds;
}
