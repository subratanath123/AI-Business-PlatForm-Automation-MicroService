package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "product_enhancement_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEnhancementJob {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String platform; // SHOPIFY

    private String status; // PENDING, PROCESSING, ENHANCED, PUBLISHED, FAILED

    private List<ProductItem> rawProducts;

    private List<ProductItem> enhancedProducts;

    private String errorMessage;

    private String source; // UPLOAD, SYNC, WEBHOOK

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductItem {
        // ── Raw values ──────────────────────────────────────────────────────
        private String shopifyId;
        private String title;
        private String bodyHtml;
        private String vendor;
        private String productType;
        private String tags;
        private String handle;
        private String seoTitle;
        private String seoDescription;

        // ── AI-enhanced values ───────────────────────────────────────────────
        private String enhancedTitle;
        private String enhancedBodyHtml;
        private String enhancedTags;
        private String enhancedProductType;
        private String enhancedHandle;
        private String enhancedSeoTitle;
        private String enhancedSeoDescription;

        private String status; // PENDING, ENHANCED, PUBLISHED, FAILED
    }
}
