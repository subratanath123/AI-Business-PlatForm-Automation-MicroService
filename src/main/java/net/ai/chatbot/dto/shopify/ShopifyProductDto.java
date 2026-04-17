package net.ai.chatbot.dto.shopify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyProductDto {
    // ── Raw fields from Shopify ──────────────────────────────────────────────
    private String shopifyId;
    private String title;
    private String bodyHtml;
    private String vendor;
    private String productType;
    private String tags;
    private String handle;
    private String seoTitle;        // title_tag metafield
    private String seoDescription;  // description_tag metafield
    private String status;          // active / draft

    // ── AI-enhanced fields ───────────────────────────────────────────────────
    private String enhancedTitle;
    private String enhancedBodyHtml;
    private String enhancedTags;
    private String enhancedProductType;
    private String enhancedHandle;
    private String enhancedSeoTitle;
    private String enhancedSeoDescription;
}
