package net.ai.chatbot.dto.amazon;

import lombok.Data;

/**
 * Sets the default Amazon ProductType slug (e.g. "PRODUCT", "SHOES",
 * "BOOK") used when publishing drafts from the active store.
 */
@Data
public class AmazonSetDefaultProductTypeRequest {
    private String productType;
}
