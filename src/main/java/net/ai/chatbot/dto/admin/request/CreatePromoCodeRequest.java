package net.ai.chatbot.dto.admin.request;

import lombok.*;
import net.ai.chatbot.dto.admin.PromoCode.DiscountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreatePromoCodeRequest {

    @NotBlank(message = "Promo code is required")
    private String code;

    private String name;
    private String description;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be positive")
    private BigDecimal discountValue;

    private BigDecimal maxDiscountAmount;
    private BigDecimal minPurchaseAmount;

    private Instant validFrom;
    private Instant validUntil;

    private Integer maxUsageCount;
    private Integer maxUsagePerUser;

    private List<String> applicablePlanIds;
    private List<String> excludedPlanIds;

    private boolean isFirstTimeOnly;
    private boolean isReferralCode;
    private String referrerUserId;
}
