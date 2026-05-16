package net.ai.chatbot.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.SubscriptionPlanDao;
import net.ai.chatbot.dao.admin.UserSubscriptionDao;
import net.ai.chatbot.dto.admin.PricingType;
import net.ai.chatbot.dto.admin.SubscriptionPlan;
import net.ai.chatbot.dto.admin.SubscriptionStatus;
import net.ai.chatbot.dto.admin.UserSubscription;
import net.ai.chatbot.dto.admin.request.CreatePlanRequest;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanDao subscriptionPlanDao;
    private final UserSubscriptionDao userSubscriptionDao;

    public SubscriptionPlan createPlan(CreatePlanRequest request) {
        if (subscriptionPlanDao.existsByPlanCode(request.getPlanCode())) {
            throw new RuntimeException("Plan code already exists: " + request.getPlanCode());
        }

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .planCode(request.getPlanCode())
                .name(request.getName())
                .description(request.getDescription())
                .badge(request.getBadge())
                .badgeColor(request.getBadgeColor())
                .pricingType(request.getPricingType() != null ? request.getPricingType() : PricingType.MONTHLY)
                .monthlyPrice(request.getMonthlyPrice())
                .annualPrice(request.getAnnualPrice())
                .priceNote(request.getPriceNote())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .trialDays(request.getTrialDays())
                .isActive(request.isActive())
                .isFeatured(request.isFeatured())
                .displayOrder(request.getDisplayOrder())
                // Limits
                .maxChatbots(request.getMaxChatbots())
                .maxMessagesPerMonth(request.getMaxMessagesPerMonth())
                .maxKnowledgeBasePages(request.getMaxKnowledgeBasePages())
                .maxSocialAccounts(request.getMaxSocialAccounts())
                .maxPostsPerMonth(request.getMaxPostsPerMonth())
                .maxAiContentGenerationsPerMonth(request.getMaxAiContentGenerationsPerMonth())
                .maxAiImagesPerMonth(request.getMaxAiImagesPerMonth())
                .maxApiCallsPerMonth(request.getMaxApiCallsPerMonth())
                // Features
                .apiAccessEnabled(request.getApiAccessEnabled())
                .aiContentAssistantEnabled(request.getAiContentAssistantEnabled())
                .customBrandingEnabled(request.getCustomBrandingEnabled())
                .prioritySupportEnabled(request.getPrioritySupportEnabled())
                .advancedAnalyticsEnabled(request.getAdvancedAnalyticsEnabled())
                .exportEnabled(request.getExportEnabled())
                .ssoEnabled(request.getSsoEnabled())
                .slaEnabled(request.getSlaEnabled())
                .analyticsLevel(request.getAnalyticsLevel())
                // Display features
                .displayFeatures(request.getDisplayFeatures())
                // Payment
                .stripeProductId(request.getStripeProductId())
                .stripePriceId(request.getStripePriceId())
                .stripeAnnualPriceId(request.getStripeAnnualPriceId())
                .paypalPlanId(request.getPaypalPlanId())
                .paypalAnnualPlanId(request.getPaypalAnnualPlanId())
                .createdBy(AuthUtils.getEmail())
                .build();

        log.info("Creating subscription plan: {}", plan.getPlanCode());
        return subscriptionPlanDao.save(plan);
    }

    public SubscriptionPlan getPlanById(String id) {
        return subscriptionPlanDao.findById(id)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + id));
    }

    public SubscriptionPlan getPlanByCode(String planCode) {
        return subscriptionPlanDao.findByPlanCode(planCode)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planCode));
    }

    public List<SubscriptionPlan> getAllPlans() {
        return subscriptionPlanDao.findAll();
    }

    public List<SubscriptionPlan> getActivePlans() {
        return subscriptionPlanDao.findAllActive();
    }

    public List<SubscriptionPlan> getPlansByPricingType(PricingType pricingType) {
        return subscriptionPlanDao.findByPricingType(pricingType);
    }

    public List<SubscriptionPlan> getFeaturedPlans() {
        return subscriptionPlanDao.findFeatured();
    }

    public SubscriptionPlan updatePlan(String id, CreatePlanRequest request) {
        SubscriptionPlan plan = getPlanById(id);

        if (!plan.getPlanCode().equals(request.getPlanCode()) &&
                subscriptionPlanDao.existsByPlanCode(request.getPlanCode())) {
            throw new RuntimeException("Plan code already exists: " + request.getPlanCode());
        }

        plan.setPlanCode(request.getPlanCode());
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setBadge(request.getBadge());
        plan.setBadgeColor(request.getBadgeColor());
        plan.setPricingType(request.getPricingType() != null ? request.getPricingType() : PricingType.MONTHLY);
        plan.setMonthlyPrice(request.getMonthlyPrice());
        plan.setAnnualPrice(request.getAnnualPrice());
        plan.setPriceNote(request.getPriceNote());
        plan.setCurrency(request.getCurrency());
        plan.setTrialDays(request.getTrialDays());
        plan.setActive(request.isActive());
        plan.setFeatured(request.isFeatured());
        plan.setDisplayOrder(request.getDisplayOrder());
        // Limits
        plan.setMaxChatbots(request.getMaxChatbots());
        plan.setMaxMessagesPerMonth(request.getMaxMessagesPerMonth());
        plan.setMaxKnowledgeBasePages(request.getMaxKnowledgeBasePages());
        plan.setMaxSocialAccounts(request.getMaxSocialAccounts());
        plan.setMaxPostsPerMonth(request.getMaxPostsPerMonth());
        plan.setMaxAiContentGenerationsPerMonth(request.getMaxAiContentGenerationsPerMonth());
        plan.setMaxAiImagesPerMonth(request.getMaxAiImagesPerMonth());
        plan.setMaxApiCallsPerMonth(request.getMaxApiCallsPerMonth());
        // Features
        plan.setApiAccessEnabled(request.getApiAccessEnabled());
        plan.setAiContentAssistantEnabled(request.getAiContentAssistantEnabled());
        plan.setCustomBrandingEnabled(request.getCustomBrandingEnabled());
        plan.setPrioritySupportEnabled(request.getPrioritySupportEnabled());
        plan.setAdvancedAnalyticsEnabled(request.getAdvancedAnalyticsEnabled());
        plan.setExportEnabled(request.getExportEnabled());
        plan.setSsoEnabled(request.getSsoEnabled());
        plan.setSlaEnabled(request.getSlaEnabled());
        plan.setAnalyticsLevel(request.getAnalyticsLevel());
        // Display features
        plan.setDisplayFeatures(request.getDisplayFeatures());
        // Payment
        plan.setStripeProductId(request.getStripeProductId());
        plan.setStripePriceId(request.getStripePriceId());
        plan.setStripeAnnualPriceId(request.getStripeAnnualPriceId());
        plan.setPaypalPlanId(request.getPaypalPlanId());
        plan.setPaypalAnnualPlanId(request.getPaypalAnnualPlanId());

        log.info("Updating subscription plan: {}", plan.getPlanCode());
        return subscriptionPlanDao.save(plan);
    }

    public void setActive(String id, boolean isActive) {
        log.info("Setting plan {} active status to {}", id, isActive);
        subscriptionPlanDao.setActive(id, isActive);
    }

    public void deletePlan(String id) {
        long activeSubscriptions = userSubscriptionDao.countByPlanId(id);
        if (activeSubscriptions > 0) {
            throw new RuntimeException("Cannot delete plan with active subscriptions: " + activeSubscriptions);
        }
        log.info("Deleting plan: {}", id);
        subscriptionPlanDao.delete(id);
    }

    public UserSubscription getUserSubscription(String userId) {
        return userSubscriptionDao.findActiveByUserId(userId).orElse(null);
    }

    public List<UserSubscription> getUserSubscriptionHistory(String userId) {
        return userSubscriptionDao.findByUserId(userId);
    }

    public UserSubscription subscribeUserToPlan(String userId, String userEmail, String planId, String promoCode) {
        SubscriptionPlan plan = getPlanById(planId);

        userSubscriptionDao.findActiveByUserId(userId).ifPresent(existing -> {
            existing.setStatus(SubscriptionStatus.CANCELLED);
            existing.setCancelledAt(Instant.now());
            existing.setCancellationReason("Upgraded to new plan");
            userSubscriptionDao.save(existing);
        });

        Instant now = Instant.now();
        Instant endDate = calculateEndDate(now, plan.getPricingType());

        // Build usage limits map from plan
        Map<String, Integer> usageLimits = new HashMap<>();
        if (plan.getMaxChatbots() != null) usageLimits.put("maxChatbots", plan.getMaxChatbots());
        if (plan.getMaxMessagesPerMonth() != null) usageLimits.put("maxMessagesPerMonth", plan.getMaxMessagesPerMonth());
        if (plan.getMaxKnowledgeBasePages() != null) usageLimits.put("maxKnowledgeBasePages", plan.getMaxKnowledgeBasePages());
        if (plan.getMaxSocialAccounts() != null) usageLimits.put("maxSocialAccounts", plan.getMaxSocialAccounts());
        if (plan.getMaxPostsPerMonth() != null) usageLimits.put("maxPostsPerMonth", plan.getMaxPostsPerMonth());

        UserSubscription subscription = UserSubscription.builder()
                .userId(userId)
                .userEmail(userEmail)
                .planId(planId)
                .planCode(plan.getPlanCode())
                .planName(plan.getName())
                .status(SubscriptionStatus.ACTIVE)
                .amountPaid(plan.getMonthlyPrice() != null ? plan.getMonthlyPrice() : BigDecimal.ZERO)
                .currency(plan.getCurrency())
                .pricingType(plan.getPricingType())
                .startDate(now)
                .endDate(endDate)
                .autoRenew(true)
                .usageLimits(usageLimits)
                .promoCodeUsed(promoCode)
                .build();

        if (plan.getTrialDays() != null && plan.getTrialDays() > 0) {
            subscription.setStatus(SubscriptionStatus.TRIAL);
            subscription.setTrialEndDate(now.plus(plan.getTrialDays(), ChronoUnit.DAYS));
        }

        log.info("Subscribing user {} to plan {}", userEmail, plan.getPlanCode());
        return userSubscriptionDao.save(subscription);
    }

    private Instant calculateEndDate(Instant startDate, PricingType pricingType) {
        return switch (pricingType) {
            case MONTHLY -> startDate.plus(30, ChronoUnit.DAYS);
            case YEARLY -> startDate.plus(365, ChronoUnit.DAYS);
            case LIFETIME -> startDate.plus(100 * 365, ChronoUnit.DAYS);
            case PREPAID -> startDate.plus(30, ChronoUnit.DAYS);
        };
    }

    public void cancelSubscription(String subscriptionId, String reason) {
        UserSubscription subscription = userSubscriptionDao.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());
        subscription.setCancellationReason(reason);

        log.info("Cancelling subscription {} for user {}", subscriptionId, subscription.getUserEmail());
        userSubscriptionDao.save(subscription);
    }
}
