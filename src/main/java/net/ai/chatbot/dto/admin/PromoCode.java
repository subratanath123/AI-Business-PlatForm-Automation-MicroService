package net.ai.chatbot.dto.admin;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "promo_codes")
public class PromoCode {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String name;
    private String description;

    @Builder.Default
    private DiscountType discountType = DiscountType.PERCENTAGE;

    private BigDecimal discountValue;

    private BigDecimal maxDiscountAmount;
    private BigDecimal minPurchaseAmount;

    @Builder.Default
    private boolean isActive = true;

    private Instant validFrom;
    private Instant validUntil;

    private Integer maxUsageCount;

    @Builder.Default
    private Integer currentUsageCount = 0;

    private Integer maxUsagePerUser;

    @Builder.Default
    private List<String> applicablePlanIds = new ArrayList<>();

    @Builder.Default
    private List<String> excludedPlanIds = new ArrayList<>();

    @Builder.Default
    private List<String> usedByUserIds = new ArrayList<>();

    private boolean isFirstTimeOnly;
    private boolean isReferralCode;
    private String referrerUserId;

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    public enum DiscountType {
        PERCENTAGE,
        FIXED_AMOUNT,
        FREE_TRIAL_DAYS
    }
}
