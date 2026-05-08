package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.dashboard.*;
import net.ai.chatbot.service.dashboard.AIGenerationsDashboardService;
import net.ai.chatbot.service.dashboard.AssetsDashboardService;
import net.ai.chatbot.service.dashboard.DashboardService;
import net.ai.chatbot.service.dashboard.EcommerceDashboardService;
import net.ai.chatbot.service.dashboard.KpiStripService;
import net.ai.chatbot.service.dashboard.SocialDashboardService;
import net.ai.chatbot.service.dashboard.SubscriptionDashboardService;
import net.ai.chatbot.service.dashboard.TeamDashboardService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final KpiStripService kpiStripService;
    private final SocialDashboardService socialDashboardService;
    private final AIGenerationsDashboardService aiGenerationsDashboardService;
    private final EcommerceDashboardService ecommerceDashboardService;
    private final TeamDashboardService teamDashboardService;
    private final SubscriptionDashboardService subscriptionDashboardService;
    private final AssetsDashboardService assetsDashboardService;

    public DashboardController(DashboardService dashboardService,
                               KpiStripService kpiStripService,
                               SocialDashboardService socialDashboardService,
                               AIGenerationsDashboardService aiGenerationsDashboardService,
                               EcommerceDashboardService ecommerceDashboardService,
                               TeamDashboardService teamDashboardService,
                               SubscriptionDashboardService subscriptionDashboardService,
                               AssetsDashboardService assetsDashboardService) {
        this.dashboardService = dashboardService;
        this.kpiStripService = kpiStripService;
        this.socialDashboardService = socialDashboardService;
        this.aiGenerationsDashboardService = aiGenerationsDashboardService;
        this.ecommerceDashboardService = ecommerceDashboardService;
        this.teamDashboardService = teamDashboardService;
        this.subscriptionDashboardService = subscriptionDashboardService;
        this.assetsDashboardService = assetsDashboardService;
    }

    /**
     * Get comprehensive dashboard statistics
     * GET /v1/api/dashboard/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        try {
            log.info("Fetching dashboard stats for user: {}", AuthUtils.getEmail());
            DashboardStatsResponse stats = dashboardService.getDashboardStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching dashboard stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get overall statistics
     * GET /v1/api/dashboard/stats/overall
     */
    @GetMapping("/stats/overall")
    public ResponseEntity<OverallStats> getOverallStats() {
        try {
            log.info("Fetching overall stats for user: {}", AuthUtils.getEmail());
            OverallStats stats = dashboardService.getOverallStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching overall stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get chatbot statistics
     * GET /v1/api/dashboard/stats/chatbots
     */
    @GetMapping("/stats/chatbots")
    public ResponseEntity<ChatBotStats> getChatBotStats() {
        try {
            log.info("Fetching chatbot stats for user: {}", AuthUtils.getEmail());
            ChatBotStats stats = dashboardService.getChatBotStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching chatbot stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get conversation statistics
     * GET /v1/api/dashboard/stats/conversations
     */
    @GetMapping("/stats/conversations")
    public ResponseEntity<ConversationStats> getConversationStats() {
        try {
            log.info("Fetching conversation stats for user: {}", AuthUtils.getEmail());
            ConversationStats stats = dashboardService.getConversationStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching conversation stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get usage statistics
     * GET /v1/api/dashboard/stats/usage
     */
    @GetMapping("/stats/usage")
    public ResponseEntity<UsageStats> getUsageStats() {
        try {
            log.info("Fetching usage stats for user: {}", AuthUtils.getEmail());
            UsageStats stats = dashboardService.getUsageStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching usage stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get usage over time (time series data)
     * GET /v1/api/dashboard/stats/usage-over-time?days=30
     */
    @GetMapping("/stats/usage-over-time")
    public ResponseEntity<List<TimeSeriesData>> getUsageOverTime(
            @RequestParam(defaultValue = "30") int days) {
        try {
            log.info("Fetching usage over time for {} days for user: {}", days, AuthUtils.getEmail());
            List<TimeSeriesData> data = dashboardService.getUsageOverTime(days);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error fetching usage over time", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get top chatbots by activity
     * GET /v1/api/dashboard/top/chatbots?limit=10
     */
    @GetMapping("/top/chatbots")
    public ResponseEntity<List<TopChatBot>> getTopChatBots(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("Fetching top {} chatbots for user: {}", limit, AuthUtils.getEmail());
            List<TopChatBot> topChatBots = dashboardService.getTopChatBots(limit);
            return ResponseEntity.ok(topChatBots);
        } catch (Exception e) {
            log.error("Error fetching top chatbots", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get top active users
     * GET /v1/api/dashboard/top/users?limit=10
     */
    @GetMapping("/top/users")
    public ResponseEntity<List<UserActivity>> getTopActiveUsers(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("Fetching top {} active users for user: {}", limit, AuthUtils.getEmail());
            List<UserActivity> topUsers = dashboardService.getTopActiveUsers(limit);
            return ResponseEntity.ok(topUsers);
        } catch (Exception e) {
            log.error("Error fetching top active users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── New summary endpoints powering the redesigned dashboard ───────────

    /**
     * Compact KPI strip for the redesigned dashboard's hero section.
     * Single round-trip; cheap counts only.
     * GET /v1/api/dashboard/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<KpiStripSummary> getKpiStripSummary() {
        try {
            return ResponseEntity.ok(kpiStripService.getSummary());
        } catch (Exception e) {
            log.error("Error fetching KPI strip summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** GET /v1/api/dashboard/social/summary */
    @GetMapping("/social/summary")
    public ResponseEntity<SocialDashboardSummary> getSocialSummary() {
        try {
            return ResponseEntity.ok(socialDashboardService.getSummary());
        } catch (Exception e) {
            log.error("Error fetching social dashboard summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** GET /v1/api/dashboard/ai-generations/summary */
    @GetMapping("/ai-generations/summary")
    public ResponseEntity<AIGenerationsDashboardSummary> getAIGenerationsSummary() {
        try {
            return ResponseEntity.ok(aiGenerationsDashboardService.getSummary());
        } catch (Exception e) {
            log.error("Error fetching AI generations dashboard summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** GET /v1/api/dashboard/ecommerce/summary */
    @GetMapping("/ecommerce/summary")
    public ResponseEntity<EcommerceDashboardSummary> getEcommerceSummary() {
        try {
            return ResponseEntity.ok(ecommerceDashboardService.getSummary());
        } catch (Exception e) {
            log.error("Error fetching ecommerce dashboard summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** GET /v1/api/dashboard/team/summary */
    @GetMapping("/team/summary")
    public ResponseEntity<TeamDashboardSummary> getTeamSummary() {
        try {
            return ResponseEntity.ok(teamDashboardService.getSummary());
        } catch (Exception e) {
            log.error("Error fetching team dashboard summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** GET /v1/api/dashboard/subscription/summary */
    @GetMapping("/subscription/summary")
    public ResponseEntity<SubscriptionDashboardSummary> getSubscriptionSummary() {
        try {
            return ResponseEntity.ok(subscriptionDashboardService.getSummary());
        } catch (Exception e) {
            log.error("Error fetching subscription dashboard summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** GET /v1/api/dashboard/assets/summary */
    @GetMapping("/assets/summary")
    public ResponseEntity<AssetsDashboardSummary> getAssetsSummary() {
        try {
            return ResponseEntity.ok(assetsDashboardService.getSummary());
        } catch (Exception e) {
            log.error("Error fetching assets dashboard summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

