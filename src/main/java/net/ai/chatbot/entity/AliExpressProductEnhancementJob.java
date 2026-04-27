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

@Document(collection = "aliexpress_product_enhancement_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliExpressProductEnhancementJob {

    @Id
    private String id;

    @Indexed
    private String userId;

    /** Always ALIEXPRESS — parity with other platform jobs. */
    private String platform;

    /** PENDING, PROCESSING, ENHANCED, PUBLISHED, FAILED. */
    private String status;

    private List<AliExpressProductItem> rawProducts;

    private List<AliExpressProductItem> enhancedProducts;

    private String errorMessage;

    /** SYNC, UPLOAD, NOTIFICATION. */
    private String source;

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AliExpressProductItem {
        private String localId;

        /** AliExpress product id (numeric string). */
        private String productId;

        /**
         * Same as {@link #productId} — kept so the shared enhancer UI can route
         * to {@code /product/{shopifyId}} without a platform-specific fork.
         */
        private String shopifyId;

        private String skuCode;

        private String title;
        private String bodyHtml;

        private String brandName;
        private String categoryId;

        private String price;
        private String currency;

        private Integer inventoryQuantity;

        /** onSelling, offline, auditing, … from sync. */
        private String aeStatus;

        private List<String> images;

        private String enhancedTitle;
        private String enhancedBodyHtml;
        private String enhancedBrandName;

        private String status;

        private String lifecycleStatus;
    }
}
