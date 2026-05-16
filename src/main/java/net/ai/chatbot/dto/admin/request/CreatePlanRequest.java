package net.ai.chatbot.dto.admin.request;

import lombok.*;
import net.ai.chatbot.dto.admin.PricingType;
import net.ai.chatbot.dto.admin.SubscriptionPlan;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreatePlanRequest {

    @NotBlank(message = "Plan code is required")
    private String planCode;

    @NotBlank(message = "Plan name is required")
    private String name;

    private String description;
    private String badge;
    private String badgeColor;

    @Builder.Default
    private PricingType pricingType = PricingType.MONTHLY;

    private BigDecimal monthlyPrice;
    private BigDecimal annualPrice;
    private String priceNote;
    private String currency;
    private Integer trialDays;
    private boolean isActive;
    private boolean isFeatured;
    private Integer displayOrder;

    // Plan limits
    private Integer maxChatbots;
    private Integer maxMessagesPerMonth;
    private Integer maxKnowledgeBasePages;
    private Integer maxSocialAccounts;
    private Integer maxPostsPerMonth;
    private Integer maxAiContentGenerationsPerMonth;
    private Integer maxAiImagesPerMonth;
    private Integer maxApiCallsPerMonth;

    // Feature flags
    private Boolean apiAccessEnabled;
    private Boolean aiContentAssistantEnabled;
    private Boolean customBrandingEnabled;
    private Boolean prioritySupportEnabled;
    private Boolean advancedAnalyticsEnabled;
    private Boolean exportEnabled;
    private Boolean ssoEnabled;
    private Boolean slaEnabled;
    private String analyticsLevel;

    // Display features
    private List<SubscriptionPlan.PlanFeature> displayFeatures;

    // Payment gateway IDs
    private String stripeProductId;
    private String stripePriceId;
    private String stripeAnnualPriceId;
    private String paypalPlanId;
    private String paypalAnnualPlanId;
}
