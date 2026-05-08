package net.ai.chatbot.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.dashboard.SocialDashboardSummary;
import net.ai.chatbot.entity.social.SocialAccount;
import net.ai.chatbot.entity.social.SocialPost;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
 * Aggregates Social Media Suite data ({@code social_accounts} +
 * {@code social_posts}) into a single {@link SocialDashboardSummary}.
 * All data is scoped by the JWT {@code sub} (Clerk user id).
 */
@Service
@Slf4j
public class SocialDashboardService {

    private final MongoTemplate mongoTemplate;

    public SocialDashboardService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public SocialDashboardSummary getSummary() {
        String userId = AuthUtils.getUserId();
        if (userId == null || userId.isBlank()) {
            return emptySummary();
        }

        Map<String, Long> accountsByPlatform = countAccountsByPlatform(userId);
        long totalConnectedAccounts = accountsByPlatform.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Long> postsByStatus = countPostsByStatus(userId);
        long totalPosts = postsByStatus.values().stream().mapToLong(Long::longValue).sum();

        Instant now = Instant.now();
        Instant weekAgo = now.minus(7, ChronoUnit.DAYS);

        long postsScheduledUpcoming = mongoTemplate.count(
                new Query(Criteria.where("userId").is(userId)
                        .and("status").is("scheduled")
                        .and("scheduledAt").gt(now)),
                SocialPost.class);

        long postsPublished7d = mongoTemplate.count(
                new Query(Criteria.where("userId").is(userId)
                        .and("status").is("published")
                        .and("publishedAt").gte(weekAgo)),
                SocialPost.class);

        return SocialDashboardSummary.builder()
                .accountsByPlatform(accountsByPlatform)
                .totalConnectedAccounts(totalConnectedAccounts)
                .postsByStatus(postsByStatus)
                .totalPosts(totalPosts)
                .postsScheduledUpcoming(postsScheduledUpcoming)
                .postsPublished7d(postsPublished7d)
                .scheduledNext14d(scheduledCalendar(userId, 14))
                .build();
    }

    private Map<String, Long> countAccountsByPlatform(String userId) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)),
                Aggregation.group("platform").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, SocialAccount.class, Map.class);
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map<?, ?> row : results.getMappedResults()) {
            Object platform = row.get("_id");
            Object count = row.get("count");
            if (platform != null && count instanceof Number n) {
                out.put(String.valueOf(platform), n.longValue());
            }
        }
        return out;
    }

    private Map<String, Long> countPostsByStatus(String userId) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)),
                Aggregation.group("status").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, SocialPost.class, Map.class);
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

    private List<SocialDashboardSummary.DayCount> scheduledCalendar(String userId, int days) {
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowEnd = todayStart.plus(days, ChronoUnit.DAYS);

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)
                        .and("status").is("scheduled")
                        .and("scheduledAt").gte(todayStart).lt(windowEnd)),
                Aggregation.project()
                        .andExpression("dateToString('%Y-%m-%d', $scheduledAt)").as("date"),
                Aggregation.group("date").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, SocialPost.class, Map.class);

        Map<String, Long> byDate = new LinkedHashMap<>();
        for (Map<?, ?> row : results.getMappedResults()) {
            Object date = row.get("_id");
            Object count = row.get("count");
            if (date != null && count instanceof Number n) {
                byDate.put(String.valueOf(date), n.longValue());
            }
        }

        // Build a contiguous day-by-day list so the UI never has to fill gaps.
        List<SocialDashboardSummary.DayCount> out = new LinkedList<>();
        LocalDate cursor = LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i < days; i++) {
            String iso = cursor.toString();
            out.add(SocialDashboardSummary.DayCount.builder()
                    .date(iso)
                    .count(byDate.getOrDefault(iso, 0L))
                    .build());
            cursor = cursor.plusDays(1);
        }
        return out;
    }

    private SocialDashboardSummary emptySummary() {
        return SocialDashboardSummary.builder()
                .accountsByPlatform(Map.of())
                .totalConnectedAccounts(0)
                .postsByStatus(Map.of())
                .totalPosts(0)
                .postsScheduledUpcoming(0)
                .postsPublished7d(0)
                .scheduledNext14d(List.of())
                .build();
    }
}
