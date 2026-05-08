package net.ai.chatbot.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.dashboard.AIGenerationsDashboardSummary;
import net.ai.chatbot.entity.AIImageJob;
import net.ai.chatbot.entity.AIPhotoStudioJob;
import net.ai.chatbot.entity.AIProductPhotoJob;
import net.ai.chatbot.entity.AIProductStudioJob;
import net.ai.chatbot.entity.AIVideoJob;
import net.ai.chatbot.entity.FaceSwapJob;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Aggregates AI generation activity across the six media-studio collections
 * into a single {@link AIGenerationsDashboardSummary}. All collections are
 * keyed by {@code userEmail} (legacy convention).
 */
@Service
@Slf4j
public class AIGenerationsDashboardService {

    /** Stable feature key → entity class. Order is preserved for the strip. */
    private static final Map<String, Class<?>> FEATURES = new LinkedHashMap<>();
    static {
        FEATURES.put("image", AIImageJob.class);
        FEATURES.put("video", AIVideoJob.class);
        FEATURES.put("photoStudio", AIPhotoStudioJob.class);
        FEATURES.put("productPhoto", AIProductPhotoJob.class);
        FEATURES.put("productStudio", AIProductStudioJob.class);
        FEATURES.put("faceSwap", FaceSwapJob.class);
    }

    private final MongoTemplate mongoTemplate;

    public AIGenerationsDashboardService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AIGenerationsDashboardSummary getSummary() {
        String userEmail = AuthUtils.getEmail();
        if (userEmail == null || userEmail.isBlank()) {
            return emptySummary();
        }

        Map<String, Long> totalsByFeature = new LinkedHashMap<>();
        Map<String, Map<String, Long>> statusByFeature = new LinkedHashMap<>();
        Map<String, Double> successRateByFeature = new LinkedHashMap<>();

        long total = 0L;
        for (Map.Entry<String, Class<?>> entry : FEATURES.entrySet()) {
            String key = entry.getKey();
            Class<?> cls = entry.getValue();

            Map<String, Long> byStatus = countByStatus(userEmail, cls);
            statusByFeature.put(key, byStatus);

            long featureTotal = byStatus.values().stream().mapToLong(Long::longValue).sum();
            totalsByFeature.put(key, featureTotal);
            total += featureTotal;

            long completed = byStatus.getOrDefault("completed", 0L);
            long failed = byStatus.getOrDefault("failed", 0L);
            long terminal = completed + failed;
            successRateByFeature.put(key, terminal == 0 ? 0.0 : (double) completed / (double) terminal);
        }

        return AIGenerationsDashboardSummary.builder()
                .totalsByFeature(totalsByFeature)
                .totalGenerations(total)
                .statusByFeature(statusByFeature)
                .successRateByFeature(successRateByFeature)
                .perDay30d(perDayBreakdown(userEmail, 30))
                .build();
    }

    private Map<String, Long> countByStatus(String userEmail, Class<?> cls) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userEmail").is(userEmail)),
                Aggregation.group("status").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, cls, Map.class);
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map<?, ?> row : results.getMappedResults()) {
            Object status = row.get("_id");
            Object count = row.get("count");
            if (status != null && count instanceof Number n) {
                out.put(String.valueOf(status), n.longValue());
            }
        }
        return out;
    }

    private List<AIGenerationsDashboardSummary.DayBreakdown> perDayBreakdown(String userEmail, int days) {
        Instant windowStart = LocalDate.now(ZoneOffset.UTC)
                .minusDays(days - 1L)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        // feature -> (date -> count)
        Map<String, Map<String, Long>> perFeature = new LinkedHashMap<>();
        for (Map.Entry<String, Class<?>> entry : FEATURES.entrySet()) {
            perFeature.put(entry.getKey(), countByDate(userEmail, entry.getValue(), windowStart));
        }

        List<AIGenerationsDashboardSummary.DayBreakdown> out = new LinkedList<>();
        LocalDate cursor = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            String iso = cursor.toString();
            out.add(AIGenerationsDashboardSummary.DayBreakdown.builder()
                    .date(iso)
                    .image(perFeature.get("image").getOrDefault(iso, 0L))
                    .video(perFeature.get("video").getOrDefault(iso, 0L))
                    .photoStudio(perFeature.get("photoStudio").getOrDefault(iso, 0L))
                    .productPhoto(perFeature.get("productPhoto").getOrDefault(iso, 0L))
                    .productStudio(perFeature.get("productStudio").getOrDefault(iso, 0L))
                    .faceSwap(perFeature.get("faceSwap").getOrDefault(iso, 0L))
                    .build());
            cursor = cursor.plusDays(1);
        }
        return out;
    }

    private Map<String, Long> countByDate(String userEmail, Class<?> cls, Instant windowStart) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userEmail").is(userEmail)
                        .and("createdAt").gte(windowStart)),
                Aggregation.project()
                        .andExpression("dateToString('%Y-%m-%d', $createdAt)").as("date"),
                Aggregation.group("date").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, cls, Map.class);
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map<?, ?> row : results.getMappedResults()) {
            Object date = row.get("_id");
            Object count = row.get("count");
            if (date != null && count instanceof Number n) {
                out.put(String.valueOf(date), n.longValue());
            }
        }
        return out;
    }

    /**
     * Total AI generations created in the trailing N days. Exposed for the
     * KPI strip so it doesn't have to call the heavier full summary.
     */
    public long countCreatedInLastDays(int days) {
        String userEmail = AuthUtils.getEmail();
        if (userEmail == null || userEmail.isBlank()) {
            return 0L;
        }
        Instant windowStart = Instant.now().minus(days, ChronoUnit.DAYS);
        long total = 0L;
        for (Class<?> cls : FEATURES.values()) {
            total += mongoTemplate.count(
                    new org.springframework.data.mongodb.core.query.Query(
                            Criteria.where("userEmail").is(userEmail)
                                    .and("createdAt").gte(windowStart)),
                    cls);
        }
        return total;
    }

    private AIGenerationsDashboardSummary emptySummary() {
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, Map<String, Long>> status = new LinkedHashMap<>();
        Map<String, Double> rates = new LinkedHashMap<>();
        for (String key : FEATURES.keySet()) {
            totals.put(key, 0L);
            status.put(key, Map.of());
            rates.put(key, 0.0);
        }
        return AIGenerationsDashboardSummary.builder()
                .totalsByFeature(totals)
                .totalGenerations(0)
                .statusByFeature(status)
                .successRateByFeature(rates)
                .perDay30d(List.of())
                .build();
    }
}
