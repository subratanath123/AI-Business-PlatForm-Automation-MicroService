package net.ai.chatbot.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.SubscriptionPlanDao;
import net.ai.chatbot.dao.admin.UserSubscriptionDao;
import net.ai.chatbot.dto.admin.SubscriptionPlan;
import net.ai.chatbot.dto.admin.SubscriptionStatus;
import net.ai.chatbot.dto.admin.UserSubscription;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanLimitValidator {

    private final UserSubscriptionDao userSubscriptionDao;
    private final SubscriptionPlanDao subscriptionPlanDao;
    private final MongoTemplate mongoTemplate;

    /**
     * Get the current active subscription for a user, or return a default free plan limits.
     */
    public SubscriptionPlan getUserPlanLimits(String userId) {
        Optional<UserSubscription> subscription = userSubscriptionDao.findActiveByUserId(userId);
        
        if (subscription.isPresent() && isSubscriptionValid(subscription.get())) {
            return subscriptionPlanDao.findById(subscription.get().getPlanId())
                    .orElse(getFreePlanDefaults());
        }
        
        return getFreePlanDefaults();
    }

    /**
     * Check if a subscription is still valid (not expired, not cancelled).
     */
    public boolean isSubscriptionValid(UserSubscription subscription) {
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED ||
            subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            return false;
        }
        
        if (subscription.getEndDate() != null && subscription.getEndDate().isBefore(Instant.now())) {
            return false;
        }
        
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHATBOT LIMITS
    // ═══════════════════════════════════════════════════════════════════════════

    public LimitCheckResult canCreateChatbot(String userId) {
        SubscriptionPlan plan = getUserPlanLimits(userId);
        int currentCount = countUserChatbots(userId);
        
        if (plan.isUnlimitedChatbots()) {
            return LimitCheckResult.allowed();
        }
        
        int limit = plan.getMaxChatbots() != null ? plan.getMaxChatbots() : 1;
        if (currentCount >= limit) {
            return LimitCheckResult.denied(
                "Chatbot limit reached",
                String.format("Your %s plan allows %d chatbots. You currently have %d.", 
                    plan.getName(), limit, currentCount),
                "maxChatbots",
                limit,
                currentCount
            );
        }
        
        return LimitCheckResult.allowed(limit - currentCount);
    }

    public LimitCheckResult canSendMessage(String userId) {
        SubscriptionPlan plan = getUserPlanLimits(userId);
        int currentCount = countUserMessagesThisMonth(userId);
        
        if (plan.isUnlimitedMessages()) {
            return LimitCheckResult.allowed();
        }
        
        int limit = plan.getMaxMessagesPerMonth() != null ? plan.getMaxMessagesPerMonth() : 500;
        if (currentCount >= limit) {
            return LimitCheckResult.denied(
                "Monthly message limit reached",
                String.format("Your %s plan allows %,d messages per month. You've used %,d.", 
                    plan.getName(), limit, currentCount),
                "maxMessagesPerMonth",
                limit,
                currentCount
            );
        }
        
        return LimitCheckResult.allowed(limit - currentCount);
    }

    public LimitCheckResult canAddKnowledgeBasePage(String userId, String chatbotId) {
        SubscriptionPlan plan = getUserPlanLimits(userId);
        int currentCount = countChatbotKnowledgeBasePages(chatbotId);
        
        if (plan.isUnlimitedPages()) {
            return LimitCheckResult.allowed();
        }
        
        int limit = plan.getMaxKnowledgeBasePages() != null ? plan.getMaxKnowledgeBasePages() : 10;
        if (currentCount >= limit) {
            return LimitCheckResult.denied(
                "Knowledge base page limit reached",
                String.format("Your %s plan allows %,d knowledge base pages per chatbot. This chatbot has %,d.", 
                    plan.getName(), limit, currentCount),
                "maxKnowledgeBasePages",
                limit,
                currentCount
            );
        }
        
        return LimitCheckResult.allowed(limit - currentCount);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SOCIAL MEDIA LIMITS
    // ═══════════════════════════════════════════════════════════════════════════

    public LimitCheckResult canConnectSocialAccount(String userId) {
        SubscriptionPlan plan = getUserPlanLimits(userId);
        int currentCount = countUserSocialAccounts(userId);
        
        int limit = plan.getMaxSocialAccounts() != null ? plan.getMaxSocialAccounts() : 0;
        
        if (limit == 0) {
            return LimitCheckResult.denied(
                "Social accounts not available",
                String.format("Your %s plan does not include social media accounts. Please upgrade to access this feature.", 
                    plan.getName()),
                "maxSocialAccounts",
                0,
                currentCount
            );
        }
        
        if (plan.isUnlimitedSocialAccounts()) {
            return LimitCheckResult.allowed();
        }
        
        if (currentCount >= limit) {
            return LimitCheckResult.denied(
                "Social account limit reached",
                String.format("Your %s plan allows %d social accounts. You've connected %d.", 
                    plan.getName(), limit, currentCount),
                "maxSocialAccounts",
                limit,
                currentCount
            );
        }
        
        return LimitCheckResult.allowed(limit - currentCount);
    }

    public LimitCheckResult canSchedulePost(String userId) {
        SubscriptionPlan plan = getUserPlanLimits(userId);
        int currentCount = countUserPostsThisMonth(userId);
        
        int limit = plan.getMaxPostsPerMonth() != null ? plan.getMaxPostsPerMonth() : 0;
        
        if (limit == 0) {
            return LimitCheckResult.denied(
                "Post scheduling not available",
                String.format("Your %s plan does not include post scheduling. Please upgrade to access this feature.", 
                    plan.getName()),
                "maxPostsPerMonth",
                0,
                currentCount
            );
        }
        
        if (plan.isUnlimitedPosts()) {
            return LimitCheckResult.allowed();
        }
        
        if (currentCount >= limit) {
            return LimitCheckResult.denied(
                "Monthly post limit reached",
                String.format("Your %s plan allows %d posts per month. You've scheduled %d.", 
                    plan.getName(), limit, currentCount),
                "maxPostsPerMonth",
                limit,
                currentCount
            );
        }
        
        return LimitCheckResult.allowed(limit - currentCount);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FEATURE CHECKS
    // ═══════════════════════════════════════════════════════════════════════════

    public LimitCheckResult canUseAiContentAssistant(String userId) {
        SubscriptionPlan plan = getUserPlanLimits(userId);
        
        if (Boolean.TRUE.equals(plan.getAiContentAssistantEnabled())) {
            return LimitCheckResult.allowed();
        }
        
        return LimitCheckResult.denied(
            "AI Content Assistant not available",
            String.format("Your %s plan does not include AI Content Assistant. Please upgrade to access this feature.", 
                plan.getName()),
            "aiContentAssistantEnabled",
            0,
            0
        );
    }

    public LimitCheckResult canUseApi(String userId) {
        SubscriptionPlan plan = getUserPlanLimits(userId);
        
        if (Boolean.TRUE.equals(plan.getApiAccessEnabled())) {
            int currentCalls = countUserApiCallsThisMonth(userId);
            int limit = plan.getMaxApiCallsPerMonth() != null ? plan.getMaxApiCallsPerMonth() : 0;
            
            if (limit == SubscriptionPlan.UNLIMITED || currentCalls < limit) {
                return LimitCheckResult.allowed(limit == SubscriptionPlan.UNLIMITED ? -1 : limit - currentCalls);
            }
            
            return LimitCheckResult.denied(
                "API call limit reached",
                String.format("Your %s plan allows %,d API calls per month. You've used %,d.", 
                    plan.getName(), limit, currentCalls),
                "maxApiCallsPerMonth",
                limit,
                currentCalls
            );
        }
        
        return LimitCheckResult.denied(
            "API access not available",
            String.format("Your %s plan does not include API access. Please upgrade to access this feature.", 
                plan.getName()),
            "apiAccessEnabled",
            0,
            0
        );
    }

    public LimitCheckResult canUseCustomBranding(String userId) {
        SubscriptionPlan plan = getUserPlanLimits(userId);
        
        if (Boolean.TRUE.equals(plan.getCustomBrandingEnabled())) {
            return LimitCheckResult.allowed();
        }
        
        return LimitCheckResult.denied(
            "Custom branding not available",
            String.format("Your %s plan does not include custom branding. Please upgrade to remove branding.", 
                plan.getName()),
            "customBrandingEnabled",
            0,
            0
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // USAGE SUMMARY
    // ═══════════════════════════════════════════════════════════════════════════

    public UsageSummary getUserUsageSummary(String userId) {
        SubscriptionPlan plan = getUserPlanLimits(userId);
        
        return UsageSummary.builder()
                .planName(plan.getName())
                .planCode(plan.getPlanCode())
                // Chatbots
                .chatbotsUsed(countUserChatbots(userId))
                .chatbotsLimit(plan.getMaxChatbots())
                // Messages
                .messagesUsed(countUserMessagesThisMonth(userId))
                .messagesLimit(plan.getMaxMessagesPerMonth())
                // Knowledge base
                .knowledgePagesLimit(plan.getMaxKnowledgeBasePages())
                // Social
                .socialAccountsUsed(countUserSocialAccounts(userId))
                .socialAccountsLimit(plan.getMaxSocialAccounts())
                .postsUsed(countUserPostsThisMonth(userId))
                .postsLimit(plan.getMaxPostsPerMonth())
                // Features
                .aiContentAssistantEnabled(Boolean.TRUE.equals(plan.getAiContentAssistantEnabled()))
                .apiAccessEnabled(Boolean.TRUE.equals(plan.getApiAccessEnabled()))
                .customBrandingEnabled(Boolean.TRUE.equals(plan.getCustomBrandingEnabled()))
                .advancedAnalyticsEnabled(Boolean.TRUE.equals(plan.getAdvancedAnalyticsEnabled()))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COUNT METHODS (implement based on your collections)
    // ═══════════════════════════════════════════════════════════════════════════

    private int countUserChatbots(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        return (int) mongoTemplate.count(query, "chatbots");
    }

    private int countUserMessagesThisMonth(String userId) {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        Instant startOfMonth = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfMonth = currentMonth.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("createdAt").gte(startOfMonth).lt(endOfMonth));
        return (int) mongoTemplate.count(query, "chat_messages");
    }

    private int countChatbotKnowledgeBasePages(String chatbotId) {
        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        return (int) mongoTemplate.count(query, "knowledge_base_pages");
    }

    private int countUserSocialAccounts(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        return (int) mongoTemplate.count(query, "social_accounts");
    }

    private int countUserPostsThisMonth(String userId) {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        Instant startOfMonth = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfMonth = currentMonth.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("createdAt").gte(startOfMonth).lt(endOfMonth));
        return (int) mongoTemplate.count(query, "social_posts");
    }

    private int countUserApiCallsThisMonth(String userId) {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        Instant startOfMonth = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfMonth = currentMonth.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("timestamp").gte(startOfMonth).lt(endOfMonth));
        return (int) mongoTemplate.count(query, "api_calls");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEFAULT FREE PLAN
    // ═══════════════════════════════════════════════════════════════════════════

    private SubscriptionPlan getFreePlanDefaults() {
        return SubscriptionPlan.builder()
                .planCode("FREE")
                .name("Free")
                .maxChatbots(1)
                .maxMessagesPerMonth(500)
                .maxKnowledgeBasePages(10)
                .maxSocialAccounts(0)
                .maxPostsPerMonth(0)
                .maxAiContentGenerationsPerMonth(0)
                .maxAiImagesPerMonth(0)
                .apiAccessEnabled(false)
                .aiContentAssistantEnabled(false)
                .customBrandingEnabled(false)
                .prioritySupportEnabled(false)
                .advancedAnalyticsEnabled(false)
                .analyticsLevel("Basic")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class LimitCheckResult {
        private boolean allowed;
        private String errorTitle;
        private String errorMessage;
        private String limitField;
        private int limit;
        private int currentUsage;
        private int remaining;

        public static LimitCheckResult allowed() {
            return LimitCheckResult.builder().allowed(true).remaining(-1).build();
        }

        public static LimitCheckResult allowed(int remaining) {
            return LimitCheckResult.builder().allowed(true).remaining(remaining).build();
        }

        public static LimitCheckResult denied(String title, String message, String field, int limit, int current) {
            return LimitCheckResult.builder()
                    .allowed(false)
                    .errorTitle(title)
                    .errorMessage(message)
                    .limitField(field)
                    .limit(limit)
                    .currentUsage(current)
                    .remaining(0)
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class UsageSummary {
        private String planName;
        private String planCode;
        
        private int chatbotsUsed;
        private Integer chatbotsLimit;
        
        private int messagesUsed;
        private Integer messagesLimit;
        
        private Integer knowledgePagesLimit;
        
        private int socialAccountsUsed;
        private Integer socialAccountsLimit;
        
        private int postsUsed;
        private Integer postsLimit;
        
        private boolean aiContentAssistantEnabled;
        private boolean apiAccessEnabled;
        private boolean customBrandingEnabled;
        private boolean advancedAnalyticsEnabled;
    }
}
