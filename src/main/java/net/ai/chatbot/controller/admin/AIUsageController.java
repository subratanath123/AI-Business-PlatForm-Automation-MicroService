package net.ai.chatbot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.AIUsageRecord;
import net.ai.chatbot.service.admin.AIUsageService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for AI usage analytics and monitoring.
 */
@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/admin/ai-usage")
@RequiredArgsConstructor
public class AIUsageController {

    private final AIUsageService aiUsageService;

    /**
     * Get comprehensive dashboard statistics.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Map<String, Object> stats = aiUsageService.getDashboardStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching AI usage dashboard stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get usage breakdown by feature type.
     */
    @GetMapping("/by-feature")
    public ResponseEntity<List<Map<String, Object>>> getUsageByFeature() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Map<String, Object>> usage = aiUsageService.getUsageByFeature();
            return ResponseEntity.ok(usage);
        } catch (Exception e) {
            log.error("Error fetching usage by feature", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get usage breakdown by model.
     */
    @GetMapping("/by-model")
    public ResponseEntity<List<Map<String, Object>>> getUsageByModel() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Map<String, Object>> usage = aiUsageService.getUsageByModel();
            return ResponseEntity.ok(usage);
        } catch (Exception e) {
            log.error("Error fetching usage by model", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get daily usage trend.
     */
    @GetMapping("/daily")
    public ResponseEntity<List<Map<String, Object>>> getDailyUsage(
            @RequestParam(defaultValue = "30") int days) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Map<String, Object>> usage = aiUsageService.getDailyUsage(days);
            return ResponseEntity.ok(usage);
        } catch (Exception e) {
            log.error("Error fetching daily usage", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get hourly usage for today.
     */
    @GetMapping("/hourly")
    public ResponseEntity<List<Map<String, Object>>> getHourlyUsage() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Map<String, Object>> usage = aiUsageService.getHourlyUsageToday();
            return ResponseEntity.ok(usage);
        } catch (Exception e) {
            log.error("Error fetching hourly usage", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get top users by token usage.
     */
    @GetMapping("/top-users")
    public ResponseEntity<List<Map<String, Object>>> getTopUsers(
            @RequestParam(defaultValue = "20") int limit) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Map<String, Object>> users = aiUsageService.getTopUsers(limit);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("Error fetching top users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get top users for a specific feature.
     */
    @GetMapping("/top-users/{featureType}")
    public ResponseEntity<List<Map<String, Object>>> getTopUsersByFeature(
            @PathVariable String featureType,
            @RequestParam(defaultValue = "20") int limit) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<Map<String, Object>> users = aiUsageService.getTopUsersByFeature(featureType, limit);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("Error fetching top users by feature", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get usage statistics for a specific user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserStats(@PathVariable String userId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Map<String, Object> stats = aiUsageService.getUserStats(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching user stats for {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get recent usage records.
     */
    @GetMapping("/records")
    public ResponseEntity<List<AIUsageRecord>> getRecentRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<AIUsageRecord> records = aiUsageService.getRecentRecords(page, size);
            return ResponseEntity.ok(records);
        } catch (Exception e) {
            log.error("Error fetching recent records", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get cost projection for the month.
     */
    @GetMapping("/cost-projection")
    public ResponseEntity<Map<String, Object>> getCostProjection() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Map<String, Object> projection = aiUsageService.getCostProjection();
            return ResponseEntity.ok(projection);
        } catch (Exception e) {
            log.error("Error fetching cost projection", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get feature labels for display.
     */
    @GetMapping("/feature-labels")
    public ResponseEntity<Map<String, String>> getFeatureLabels() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(aiUsageService.getFeatureLabels());
    }
}
