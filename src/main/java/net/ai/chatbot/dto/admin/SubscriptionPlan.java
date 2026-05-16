package net.ai.chatbot.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "subscription_plans")
public class SubscriptionPlan {

    @Id
    private String id;

    @Indexed(unique = true)
    private String planCode;

    private String name;
    private String description;
    private String badge;
    private String badgeColor;

    @Builder.Default
    private PricingType pricingType = PricingType.MONTHLY;

    private BigDecimal monthlyPrice;
    private BigDecimal annualPrice;
    private String priceNote;

    @Builder.Default
    private String currency = "USD";

    private Integer trialDays;

    @Field("isActive")
    @JsonProperty("isActive")
    @Builder.Default
    private boolean isActive = true;

    @Field("isFeatured")
    @JsonProperty("isFeatured")
    @Builder.Default
    private boolean isFeatured = false;

    private Integer displayOrder;

    // ═══════════════════════════════════════════════════════════════════════════
    // PLAN LIMITS - These are the actual enforced limits
    // ═══════════════════════════════════════════════════════════════════════════

    // Chatbot limits
    @Builder.Default
    private Integer maxChatbots = 1;
    
    @Builder.Default
    private Integer maxMessagesPerMonth = 500;
    
    @Builder.Default
    private Integer maxKnowledgeBasePages = 10;

    // Social media limits
    @Builder.Default
    private Integer maxSocialAccounts = 0;
    
    @Builder.Default
    private Integer maxPostsPerMonth = 0;

    // Content limits
    @Builder.Default
    private Integer maxAiContentGenerationsPerMonth = 0;
    
    @Builder.Default
    private Integer maxAiImagesPerMonth = 0;

    // API and integrations
    @Builder.Default
    private Boolean apiAccessEnabled = false;
    
    @Builder.Default
    private Integer maxApiCallsPerMonth = 0;

    // Features flags
    @Builder.Default
    private Boolean aiContentAssistantEnabled = false;
    
    @Builder.Default
    private Boolean customBrandingEnabled = false;
    
    @Builder.Default
    private Boolean prioritySupportEnabled = false;
    
    @Builder.Default
    private Boolean advancedAnalyticsEnabled = false;
    
    @Builder.Default
    private Boolean exportEnabled = false;
    
    @Builder.Default
    private Boolean ssoEnabled = false;
    
    @Builder.Default
    private Boolean slaEnabled = false;

    @Builder.Default
    private String analyticsLevel = "Basic";

    // For unlimited values, use -1
    public static final int UNLIMITED = -1;

    // ═══════════════════════════════════════════════════════════════════════════
    // DISPLAY FEATURES - For showing in the pricing page
    // ═══════════════════════════════════════════════════════════════════════════

    @Builder.Default
    private List<PlanFeature> displayFeatures = new ArrayList<>();

    // Payment gateway IDs
    private String stripeProductId;
    private String stripePriceId;
    private String stripeAnnualPriceId;
    private String paypalPlanId;
    private String paypalAnnualPlanId;

    // Metadata
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PlanFeature {
        private String label;
        private String value;
        private Boolean included;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean isUnlimitedChatbots() {
        return maxChatbots != null && maxChatbots == UNLIMITED;
    }

    public boolean isUnlimitedMessages() {
        return maxMessagesPerMonth != null && maxMessagesPerMonth == UNLIMITED;
    }

    public boolean isUnlimitedPages() {
        return maxKnowledgeBasePages != null && maxKnowledgeBasePages == UNLIMITED;
    }

    public boolean isUnlimitedSocialAccounts() {
        return maxSocialAccounts != null && maxSocialAccounts == UNLIMITED;
    }

    public boolean isUnlimitedPosts() {
        return maxPostsPerMonth != null && maxPostsPerMonth == UNLIMITED;
    }

    public String getFormattedLimit(Integer limit) {
        if (limit == null || limit == 0) return "Not included";
        if (limit == UNLIMITED) return "Unlimited";
        return String.format("%,d", limit);
    }
}
