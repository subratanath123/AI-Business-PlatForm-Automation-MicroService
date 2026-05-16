package net.ai.chatbot.dto.admin;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "user_subscriptions")
public class UserSubscription {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String userEmail;

    @Indexed
    private String planId;

    private String planCode;
    private String planName;

    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    private BigDecimal amountPaid;
    private String currency;

    private PricingType pricingType;

    private Instant startDate;
    private Instant endDate;
    private Instant trialEndDate;

    private Instant cancelledAt;
    private String cancellationReason;

    private boolean autoRenew;

    private String stripeSubscriptionId;
    private String stripeCustomerId;
    private String paypalSubscriptionId;

    private String promoCodeUsed;
    private BigDecimal discountAmount;

    @Builder.Default
    private Map<String, Integer> usageLimits = new HashMap<>();

    @Builder.Default
    private Map<String, Integer> currentUsage = new HashMap<>();

    private Instant createdAt;
    private Instant updatedAt;
}
