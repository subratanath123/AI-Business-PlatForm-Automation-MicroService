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

    // ── Optional draft-editor fields (single default variant + images) ───────
    // Only used by the draft editor today. When persisted via updateDraft()
    // these are stored on the local ProductItem and later flushed to Shopify
    // when the draft is published.
    private String  price;
    private String  compareAtPrice;
    private String  sku;
    private Integer inventoryQuantity;
    private java.util.List<String> images;
}
