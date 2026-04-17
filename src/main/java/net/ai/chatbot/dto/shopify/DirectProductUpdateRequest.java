package net.ai.chatbot.dto.shopify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for directly updating a Shopify product's mutable fields.
 * Immutable system fields (id, created_at, variants.id, etc.) are never sent here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectProductUpdateRequest {
    private String title;
    private String bodyHtml;
    private String vendor;
    private String productType;
    private String tags;
    private String handle;
    private String status;          // active | draft | archived
    private String seoTitle;        // global.title_tag metafield
    private String seoDescription;  // global.description_tag metafield
}
