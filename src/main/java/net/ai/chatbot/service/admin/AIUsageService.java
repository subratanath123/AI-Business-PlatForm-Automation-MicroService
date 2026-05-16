package net.ai.chatbot.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.AIUsageDao;
import net.ai.chatbot.dto.admin.AIUsageRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for tracking and analyzing AI feature usage across the platform.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIUsageService {

    private final AIUsageDao aiUsageDao;

    private static final Map<String, BigDecimal> MODEL_COSTS_PER_1K_TOKENS = new HashMap<>();
    
    static {
        MODEL_COSTS_PER_1K_TOKENS.put("gpt-4", new BigDecimal("0.03"));
        MODEL_COSTS_PER_1K_TOKENS.put("gpt-4-turbo", new BigDecimal("0.01"));
        MODEL_COSTS_PER_1K_TOKENS.put("gpt-4o", new BigDecimal("0.005"));
        MODEL_COSTS_PER_1K_TOKENS.put("gpt-4o-mini", new BigDecimal("0.00015"));
        MODEL_COSTS_PER_1K_TOKENS.put("gpt-3.5-turbo", new BigDecimal("0.0015"));
        MODEL_COSTS_PER_1K_TOKENS.put("claude-3-opus", new BigDecimal("0.015"));
        MODEL_COSTS_PER_1K_TOKENS.put("claude-3-sonnet", new BigDecimal("0.003"));
        MODEL_COSTS_PER_1K_TOKENS.put("claude-3-haiku", new BigDecimal("0.00025"));
        MODEL_COSTS_PER_1K_TOKENS.put("dall-e-3", new BigDecimal("0.04"));
        MODEL_COSTS_PER_1K_TOKENS.put("stable-diffusion", new BigDecimal("0.002"));
        MODEL_COSTS_PER_1K_TOKENS.put("default", new BigDecimal("0.002"));
    }

    /**
     * Record an AI usage event.
     */
    public AIUsageRecord recordUsage(String userId, String userEmail, String featureType,
                                      String model, int inputTokens, int outputTokens,
                                      int durationMs, boolean success, String errorMessage) {
        int totalTokens = inputTokens + outputTokens;
        BigDecimal cost = calculateCost(model, totalTokens);

        AIUsageRecord record = AIUsageRecord.builder()
                .userId(userId)
                .userEmail(userEmail)
                .featureType(featureType)
                .model(model)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .estimatedCost(cost)
                .currency("USD")
                .durationMs(durationMs)
                .success(success)
                .errorMessage(errorMessage)
                .requestId(java.util.UUID.randomUUID().toString())
                .build();

        return aiUsageDao.save(record);
    }

    /**
     * Record usage for image/video generation (no token counts).
     */
    public AIUsageRecord recordGenerationUsage(String userId, String userEmail, String featureType,
                                                String model, int durationMs, boolean success,
                                                BigDecimal cost, String errorMessage) {
        AIUsageRecord record = AIUsageRecord.builder()
                .userId(userId)
                .userEmail(userEmail)
                .featureType(featureType)
                .model(model)
                .inputTokens(0)
                .outputTokens(0)
                .totalTokens(0)
                .estimatedCost(cost != null ? cost : BigDecimal.ZERO)
                .currency("USD")
                .durationMs(durationMs)
                .success(success)
                .errorMessage(errorMessage)
                .requestId(java.util.UUID.randomUUID().toString())
                .build();

        return aiUsageDao.save(record);
    }

    private BigDecimal calculateCost(String model, int totalTokens) {
        BigDecimal costPer1k = MODEL_COSTS_PER_1K_TOKENS.getOrDefault(
                model != null ? model.toLowerCase() : "default",
                MODEL_COSTS_PER_1K_TOKENS.get("default")
        );
        return costPer1k.multiply(BigDecimal.valueOf(totalTokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    /**
     * Get comprehensive dashboard statistics.
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        Map<String, Object> totalUsage = aiUsageDao.getTotalTokenUsage();
        stats.put("totalTokens", totalUsage.get("totalTokens"));
        stats.put("totalInputTokens", totalUsage.get("totalInputTokens"));
        stats.put("totalOutputTokens", totalUsage.get("totalOutputTokens"));
        stats.put("totalCost", totalUsage.get("totalCost"));
        stats.put("totalRequests", totalUsage.get("totalRequests"));

        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Map<String, Object> todayUsage = aiUsageDao.getTokenUsageForPeriod(startOfDay, Instant.now());
        stats.put("todayTokens", todayUsage.get("totalTokens"));
        stats.put("todayCost", todayUsage.get("totalCost"));
        stats.put("todayRequests", todayUsage.get("totalRequests"));

        LocalDate now = LocalDate.now();
        Instant startOfMonth = now.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<String, Object> monthUsage = aiUsageDao.getTokenUsageForPeriod(startOfMonth, Instant.now());
        stats.put("monthTokens", monthUsage.get("totalTokens"));
        stats.put("monthCost", monthUsage.get("totalCost"));
        stats.put("monthRequests", monthUsage.get("totalRequests"));

        return stats;
    }

    /**
     * Get usage breakdown by feature type.
     */
    public List<Map<String, Object>> getUsageByFeature() {
        return aiUsageDao.getUsageByFeature();
    }

    /**
     * Get usage breakdown by model.
     */
    public List<Map<String, Object>> getUsageByModel() {
        return aiUsageDao.getUsageByModel();
    }

    /**
     * Get daily usage trend.
     */
    public List<Map<String, Object>> getDailyUsage(int days) {
        return aiUsageDao.getDailyUsage(days);
    }

    /**
     * Get hourly usage for today.
     */
    public List<Map<String, Object>> getHourlyUsageToday() {
        return aiUsageDao.getHourlyUsageToday();
    }

    /**
     * Get top users by token usage.
     */
    public List<Map<String, Object>> getTopUsers(int limit) {
        return aiUsageDao.getTopUsersByUsage(limit);
    }

    /**
     * Get top users for a specific feature.
     */
    public List<Map<String, Object>> getTopUsersByFeature(String featureType, int limit) {
        return aiUsageDao.getTopUsersByFeature(featureType, limit);
    }

    /**
     * Get usage statistics for a specific user.
     */
    public Map<String, Object> getUserStats(String userId) {
        Map<String, Object> stats = new HashMap<>();
        
        Map<String, Object> usage = aiUsageDao.getUserUsageStats(userId);
        stats.putAll(usage);
        
        List<Map<String, Object>> featureBreakdown = aiUsageDao.getUserFeatureBreakdown(userId);
        stats.put("featureBreakdown", featureBreakdown);
        
        return stats;
    }

    /**
     * Get recent usage records.
     */
    public List<AIUsageRecord> getRecentRecords(int page, int size) {
        return aiUsageDao.getRecentRecords(page, size);
    }

    /**
     * Get estimated monthly cost projection based on current usage.
     */
    public Map<String, Object> getCostProjection() {
        Map<String, Object> projection = new HashMap<>();
        
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        int daysInMonth = now.lengthOfMonth();
        
        Instant startOfMonth = now.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<String, Object> monthUsage = aiUsageDao.getTokenUsageForPeriod(startOfMonth, Instant.now());
        
        Object costObj = monthUsage.get("totalCost");
        BigDecimal currentCost = BigDecimal.ZERO;
        if (costObj instanceof BigDecimal) {
            currentCost = (BigDecimal) costObj;
        } else if (costObj instanceof Number) {
            currentCost = BigDecimal.valueOf(((Number) costObj).doubleValue());
        }
        
        BigDecimal dailyAverage = dayOfMonth > 0 
                ? currentCost.divide(BigDecimal.valueOf(dayOfMonth), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        
        BigDecimal projectedMonthly = dailyAverage.multiply(BigDecimal.valueOf(daysInMonth));
        
        projection.put("currentMonthCost", currentCost);
        projection.put("dailyAverage", dailyAverage);
        projection.put("projectedMonthly", projectedMonthly);
        projection.put("daysElapsed", dayOfMonth);
        projection.put("daysInMonth", daysInMonth);
        
        return projection;
    }

    /**
     * Get feature type labels for display.
     */
    public Map<String, String> getFeatureLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("CHAT", "AI Chat");
        labels.put("CONTENT", "Content Generation");
        labels.put("IMAGE", "Image Generation");
        labels.put("VIDEO", "Video Generation");
        labels.put("PHOTO_STUDIO", "Photo Studio");
        labels.put("PRODUCT_STUDIO", "Product Studio");
        labels.put("FACE_SWAP", "Face Swap");
        labels.put("CODE", "Code Generation");
        labels.put("CHATBOT", "Chatbot Responses");
        labels.put("TEMPLATE", "Template Generation");
        return labels;
    }
}
