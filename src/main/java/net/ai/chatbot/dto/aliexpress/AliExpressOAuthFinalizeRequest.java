package net.ai.chatbot.dto.aliexpress;

import lombok.Data;

@Data
public class AliExpressOAuthFinalizeRequest {
    private String state;
    private String code;
    private String storeName;
}
