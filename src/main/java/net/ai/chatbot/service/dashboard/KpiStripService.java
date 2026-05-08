package net.ai.chatbot.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.dashboard.KpiStripSummary;
import net.ai.chatbot.dto.dashboard.SubscriptionDashboardSummary;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.social.SocialPost;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the compact KPI strip rendered at the top of the dashboard. Each
 * tile is computed with a small, indexed COUNT or DISTINCT — collectively
 * cheap enough to serve as the dashboard's first paint.
 */
@Service
@Slf4j
public class KpiStripService {

    private final MongoTemplate mongoTemplate;
    private final ChatBotDao chatBotDao;
    private final AIGenerationsDashboardService aiGenerationsDashboardService;
    private final EcommerceDashboardService ecommerceDashboardService;
    private final SubscriptionDashboardService subscriptionDashboardService;

    public KpiStripService(MongoTemplate mongoTemplate,
                           ChatBotDao chatBotDao,
                           AIGenerationsDashboardService aiGenerationsDashboardService,
                           EcommerceDashboardService ecommerceDashboardService,
                           SubscriptionDashboardService subscriptionDashboardService) {
        this.mongoTemplate = mongoTemplate;
        this.chatBotDao = chatBotDao;
        this.aiGenerationsDashboardService = aiGenerationsDashboardService;
        this.ecommerceDashboardService = ecommerceDashboardService;
        this.subscriptionDashboardService = subscriptionDashboardService;
    }

    public KpiStripSummary getSummary() {
        String email = AuthUtils.getEmail();
        String userId = AuthUtils.getUserId();
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        long activeChatbots = countActiveChatbots(email);
        List<String> userChatBotIds = userChatBotIds(email);

        long conversations7d = userChatBotIds.isEmpty() ? 0L : mongoTemplate.count(
                new Query(Criteria.where("chatbotId").in(userChatBotIds)
                        .and("createdAt").gte(weekAgo)),
                UserChatHistory.class);

        long messages7d = conversations7d; // Each UserChatHistory row is one message.

        long postsScheduled = userId == null ? 0L : mongoTemplate.count(
                new Query(Criteria.where("userId").is(userId)
                        .and("status").is("scheduled")
                        .and("scheduledAt").gt(Instant.now())),
                SocialPost.class);

        long postsPublished7d = userId == null ? 0L : mongoTemplate.count(
                new Query(Criteria.where("userId").is(userId)
                        .and("status").is("published")
                        .and("publishedAt").gte(weekAgo)),
                SocialPost.class);

        long aiGenerations30d = aiGenerationsDashboardService.countCreatedInLastDays(30);
        long connectedStores = ecommerceDashboardService.countAllConnectedStores();
        SubscriptionDashboardSummary subscription = subscriptionDashboardService.getSummary();

        return KpiStripSummary.builder()
                .activeChatbots(activeChatbots)
                .conversations7d(conversations7d)
                .messages7d(messages7d)
                .postsScheduled(postsScheduled)
                .postsPublished7d(postsPublished7d)
                .aiGenerations30d(aiGenerations30d)
                .connectedStores(connectedStores)
                .planId(subscription.getPlanId())
                .billingStatus(subscription.getBillingStatus())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .build();
    }

    private long countActiveChatbots(String email) {
        if (email == null || email.isBlank()) return 0L;
        // Mirrors the existing DashboardService convention: anything that's
        // not explicitly FAILED or DELETED counts as active. We tolerate null
        // status (legacy rows) by treating it as active too.
        return mongoTemplate.count(
                new Query(Criteria.where("createdBy").is(email)
                        .and("status").nin("FAILED", "DELETED")),
                ChatBot.class);
    }

    private List<String> userChatBotIds(String email) {
        if (email == null || email.isBlank()) return List.of();
        return chatBotDao.findByCreatedBy(email).stream()
                .map(ChatBot::getId)
                .collect(Collectors.toList());
    }
}
