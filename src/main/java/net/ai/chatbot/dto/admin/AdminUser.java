package net.ai.chatbot.dto.admin;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "user")
public class AdminUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String userName;
    private String designation;
    private String picture;

    @Builder.Default
    private UserRole role = UserRole.USER;

    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private boolean isBlocked = false;

    private String blockReason;
    private Instant blockedAt;
    private String blockedBy;

    private String country;
    private String timezone;

    private String currentPlanId;
    private String currentPlanName;
    private SubscriptionStatus subscriptionStatus;

    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder.Default
    private List<String> permissions = new ArrayList<>();

    private String referredBy;
    private Integer referralCount;

    private String stripeCustomerId;
    private String paypalCustomerId;
}
