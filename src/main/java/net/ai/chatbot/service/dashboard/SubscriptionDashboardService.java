package net.ai.chatbot.service.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.SubscriptionPlanDao;
import net.ai.chatbot.dao.admin.UserSubscriptionDao;
import net.ai.chatbot.dto.admin.SubscriptionPlan;
import net.ai.chatbot.dto.admin.UserSubscription;
import net.ai.chatbot.dto.dashboard.SubscriptionDashboardSummary;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Reads the active subscription for the current user directly from the
 * {@code user_subscriptions} collection. Always up-to-date regardless of
 * how the subscription was created (Stripe webhook, free plan activation, etc.).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionDashboardService {

    private final UserSubscriptionDao userSubscriptionDao;
    private final SubscriptionPlanDao subscriptionPlanDao;

    public SubscriptionDashboardSummary getSummary() {
        String userId = AuthUtils.getUserId();
        if (userId == null || userId.isBlank()) {
            return SubscriptionDashboardSummary.builder().hasSubscription(false).build();
        }

        UserSubscription sub = userSubscriptionDao.findActiveByUserId(userId).orElse(null);

        if (sub == null) {
            return SubscriptionDashboardSummary.builder().hasSubscription(false).build();
        }

        String planName = sub.getPlanName();
        String planCode = sub.getPlanCode();
        if ((planName == null || planName.isBlank()) && sub.getPlanId() != null) {
            SubscriptionPlan plan = subscriptionPlanDao.findById(sub.getPlanId()).orElse(null);
            if (plan != null) {
                if (planName == null || planName.isBlank()) {
                    planName = plan.getName();
                }
                if (planCode == null || planCode.isBlank()) {
                    planCode = plan.getPlanCode();
                }
            }
        }

        return SubscriptionDashboardSummary.builder()
                .hasSubscription(true)
                .planId(sub.getPlanId())
                .planName(planName)
                .planCode(planCode)
                .billingStatus(sub.getStatus() != null ? sub.getStatus().name() : null)
                .currentPeriodEnd(formatInstant(sub.getEndDate()))
                .build();
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return null;
        return DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(instant);
    }
}
