package net.ai.chatbot.dto.shopify;

import lombok.Data;

@Data
public class OAuthConnectRequest {
    private String shopDomain;
    private String accessToken;
    private String shopName;
}
