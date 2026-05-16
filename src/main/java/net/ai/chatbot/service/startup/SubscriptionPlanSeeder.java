package net.ai.chatbot.service.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.SubscriptionPlanDao;
import net.ai.chatbot.dto.admin.PricingType;
import net.ai.chatbot.dto.admin.SubscriptionPlan;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds default subscription plans into the database on application startup.
 * Only creates plans that don't already exist (by planCode).
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class SubscriptionPlanSeeder implements ApplicationRunner {

    private final SubscriptionPlanDao subscriptionPlanDao;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking subscription plans...");
        
        List<SubscriptionPlan> defaultPlans = createDefaultPlans();
        int created = 0;
        
        for (SubscriptionPlan plan : defaultPlans) {
            if (!subscriptionPlanDao.existsByPlanCode(plan.getPlanCode())) {
                subscriptionPlanDao.save(plan);
                log.info("Created subscription plan: {} ({})", plan.getName(), plan.getPlanCode());
                created++;
            }
        }
        
        if (created > 0) {
            log.info("Seeded {} subscription plans", created);
        } else {
            log.info("All subscription plans already exist ({} plans)", subscriptionPlanDao.count());
        }
    }

    private List<SubscriptionPlan> createDefaultPlans() {
        return List.of(
            createFreePlan(),
            createStarterPlan(),
            createProPlan(),
            createEnterprisePlan()
        );
    }

    private SubscriptionPlan createFreePlan() {
        return SubscriptionPlan.builder()
                .planCode("FREE")
                .name("Free")
                .description("Try the platform with no credit card required.")
                .pricingType(PricingType.MONTHLY)
                .monthlyPrice(BigDecimal.ZERO)
                .annualPrice(BigDecimal.ZERO)
                .currency("USD")
                .displayOrder(1)
                .isActive(true)
                .isFeatured(false)
                // Limits
                .maxChatbots(1)
                .maxMessagesPerMonth(500)
                .maxKnowledgeBasePages(10)
                .maxSocialAccounts(0)
                .maxPostsPerMonth(0)
                .maxAiContentGenerationsPerMonth(0)
                .maxAiImagesPerMonth(0)
                .apiAccessEnabled(false)
                .maxApiCallsPerMonth(0)
                // Features
                .aiContentAssistantEnabled(false)
                .customBrandingEnabled(false)
                .prioritySupportEnabled(false)
                .advancedAnalyticsEnabled(false)
                .exportEnabled(false)
                .ssoEnabled(false)
                .slaEnabled(false)
                .analyticsLevel("Basic")
                // Display features
                .displayFeatures(List.of(
                    feature("AI Chatbots", "1 chatbot", true),
                    feature("Messages / month", "500", true),
                    feature("Knowledge base pages", "10", true),
                    feature("Social media accounts", null, false),
                    feature("AI Content Assistant", null, false),
                    feature("Post scheduling", null, false),
                    feature("Analytics & stats", "Basic", true),
                    feature("API access", null, false),
                    feature("Priority support", null, false),
                    feature("Custom branding", null, false)
                ))
                .build();
    }

    private SubscriptionPlan createStarterPlan() {
        return SubscriptionPlan.builder()
                .planCode("STARTER")
                .name("Starter")
                .description("Perfect for small teams and growing projects.")
                .pricingType(PricingType.MONTHLY)
                .monthlyPrice(new BigDecimal("29"))
                .annualPrice(new BigDecimal("23"))
                .currency("USD")
                .trialDays(14)
                .displayOrder(2)
                .isActive(true)
                .isFeatured(false)
                // Limits
                .maxChatbots(5)
                .maxMessagesPerMonth(10000)
                .maxKnowledgeBasePages(100)
                .maxSocialAccounts(2)
                .maxPostsPerMonth(30)
                .maxAiContentGenerationsPerMonth(100)
                .maxAiImagesPerMonth(50)
                .apiAccessEnabled(false)
                .maxApiCallsPerMonth(0)
                // Features
                .aiContentAssistantEnabled(true)
                .customBrandingEnabled(false)
                .prioritySupportEnabled(false)
                .advancedAnalyticsEnabled(false)
                .exportEnabled(false)
                .ssoEnabled(false)
                .slaEnabled(false)
                .analyticsLevel("Standard")
                // Display features
                .displayFeatures(List.of(
                    feature("AI Chatbots", "5 chatbots", true),
                    feature("Messages / month", "10,000", true),
                    feature("Knowledge base pages", "100", true),
                    feature("Social media accounts", "2 accounts", true),
                    feature("AI Content Assistant", null, true),
                    feature("Post scheduling", "30 posts/mo", true),
                    feature("Analytics & stats", "Standard", true),
                    feature("API access", null, false),
                    feature("Priority support", null, false),
                    feature("Custom branding", null, false)
                ))
                .build();
    }

    private SubscriptionPlan createProPlan() {
        return SubscriptionPlan.builder()
                .planCode("PRO")
                .name("Pro")
                .description("Everything you need to scale AI-powered engagement.")
                .badge("Most Popular")
                .badgeColor("#f59e0b")
                .pricingType(PricingType.MONTHLY)
                .monthlyPrice(new BigDecimal("79"))
                .annualPrice(new BigDecimal("63"))
                .currency("USD")
                .trialDays(14)
                .displayOrder(3)
                .isActive(true)
                .isFeatured(true)
                // Limits
                .maxChatbots(25)
                .maxMessagesPerMonth(100000)
                .maxKnowledgeBasePages(1000)
                .maxSocialAccounts(10)
                .maxPostsPerMonth(SubscriptionPlan.UNLIMITED)
                .maxAiContentGenerationsPerMonth(500)
                .maxAiImagesPerMonth(200)
                .apiAccessEnabled(true)
                .maxApiCallsPerMonth(10000)
                // Features
                .aiContentAssistantEnabled(true)
                .customBrandingEnabled(true)
                .prioritySupportEnabled(false)
                .advancedAnalyticsEnabled(true)
                .exportEnabled(false)
                .ssoEnabled(false)
                .slaEnabled(false)
                .analyticsLevel("Advanced")
                // Display features
                .displayFeatures(List.of(
                    feature("AI Chatbots", "25 chatbots", true),
                    feature("Messages / month", "100,000", true),
                    feature("Knowledge base pages", "1,000", true),
                    feature("Social media accounts", "10 accounts", true),
                    feature("AI Content Assistant", null, true),
                    feature("Post scheduling", "Unlimited", true),
                    feature("Analytics & stats", "Advanced", true),
                    feature("API access", null, true),
                    feature("Priority support", null, false),
                    feature("Custom branding", null, true)
                ))
                .build();
    }

    private SubscriptionPlan createEnterprisePlan() {
        return SubscriptionPlan.builder()
                .planCode("ENTERPRISE")
                .name("Enterprise")
                .description("Unlimited scale with dedicated support and SLA.")
                .badge("Custom")
                .badgeColor("#8b5cf6")
                .pricingType(PricingType.MONTHLY)
                .priceNote("Custom pricing")
                .currency("USD")
                .displayOrder(4)
                .isActive(true)
                .isFeatured(false)
                // Limits - all unlimited
                .maxChatbots(SubscriptionPlan.UNLIMITED)
                .maxMessagesPerMonth(SubscriptionPlan.UNLIMITED)
                .maxKnowledgeBasePages(SubscriptionPlan.UNLIMITED)
                .maxSocialAccounts(SubscriptionPlan.UNLIMITED)
                .maxPostsPerMonth(SubscriptionPlan.UNLIMITED)
                .maxAiContentGenerationsPerMonth(SubscriptionPlan.UNLIMITED)
                .maxAiImagesPerMonth(SubscriptionPlan.UNLIMITED)
                .apiAccessEnabled(true)
                .maxApiCallsPerMonth(SubscriptionPlan.UNLIMITED)
                // Features - all enabled
                .aiContentAssistantEnabled(true)
                .customBrandingEnabled(true)
                .prioritySupportEnabled(true)
                .advancedAnalyticsEnabled(true)
                .exportEnabled(true)
                .ssoEnabled(true)
                .slaEnabled(true)
                .analyticsLevel("Full + Export")
                // Display features
                .displayFeatures(List.of(
                    feature("AI Chatbots", "Unlimited", true),
                    feature("Messages / month", "Unlimited", true),
                    feature("Knowledge base pages", "Unlimited", true),
                    feature("Social media accounts", "Unlimited", true),
                    feature("AI Content Assistant", null, true),
                    feature("Post scheduling", "Unlimited", true),
                    feature("Analytics & stats", "Full + Export", true),
                    feature("API access", null, true),
                    feature("Priority support", "Dedicated CSM", true),
                    feature("Custom branding", null, true)
                ))
                .build();
    }

    private SubscriptionPlan.PlanFeature feature(String label, String value, boolean included) {
        return SubscriptionPlan.PlanFeature.builder()
                .label(label)
                .value(value)
                .included(included)
                .build();
    }
}
