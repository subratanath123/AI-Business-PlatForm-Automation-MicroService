package net.ai.chatbot.dto.shopify;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConnectShopifyRequest {

    @NotBlank(message = "Shop domain is required (e.g. my-store.myshopify.com)")
    private String shopDomain;

    @NotBlank(message = "Admin API key is required")
    private String apiKey;
}
