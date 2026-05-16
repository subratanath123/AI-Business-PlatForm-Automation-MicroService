package net.ai.chatbot.dao.admin;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.admin.AIUsageRecord;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class AIUsageDao {

    private final MongoTemplate mongoTemplate;

    public AIUsageRecord save(AIUsageRecord record) {
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(Instant.now());
        }
        return mongoTemplate.save(record);
    }

    public long countTotal() {
        return mongoTemplate.count(new Query(), AIUsageRecord.class);
    }

    public long countByFeature(String featureType) {
        Query query = new Query(Criteria.where("featureType").is(featureType));
        return mongoTemplate.count(query, AIUsageRecord.class);
    }

    public long countToday() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Query query = new Query(Criteria.where("createdAt").gte(startOfDay));
        return mongoTemplate.count(query, AIUsageRecord.class);
    }

    public long countThisMonth() {
        LocalDate now = LocalDate.now();
        Instant startOfMonth = now.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Query query = new Query(Criteria.where("createdAt").gte(startOfMonth));
        return mongoTemplate.count(query, AIUsageRecord.class);
    }

    public long countByUserToday(String userId) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Query query = new Query(Criteria.where("userId").is(userId).and("createdAt").gte(startOfDay));
        return mongoTemplate.count(query, AIUsageRecord.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getTotalTokenUsage() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group()
                        .sum("inputTokens").as("totalInputTokens")
                        .sum("outputTokens").as("totalOutputTokens")
                        .sum("totalTokens").as("totalTokens")
                        .sum("estimatedCost").as("totalCost")
                        .count().as("totalRequests")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        Map result = results.getUniqueMappedResult();
        if (result == null) {
            return Map.of(
                    "totalInputTokens", 0L,
                    "totalOutputTokens", 0L,
                    "totalTokens", 0L,
                    "totalCost", BigDecimal.ZERO,
                    "totalRequests", 0L
            );
        }
        return (Map<String, Object>) result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getTokenUsageForPeriod(Instant start, Instant end) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(start).lte(end)),
                Aggregation.group()
                        .sum("inputTokens").as("totalInputTokens")
                        .sum("outputTokens").as("totalOutputTokens")
                        .sum("totalTokens").as("totalTokens")
                        .sum("estimatedCost").as("totalCost")
                        .count().as("totalRequests")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        Map result = results.getUniqueMappedResult();
        if (result == null) {
            return Map.of(
                    "totalInputTokens", 0L,
                    "totalOutputTokens", 0L,
                    "totalTokens", 0L,
                    "totalCost", BigDecimal.ZERO,
                    "totalRequests", 0L
            );
        }
        return (Map<String, Object>) result;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUsageByFeature() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("featureType")
                        .sum("inputTokens").as("inputTokens")
                        .sum("outputTokens").as("outputTokens")
                        .sum("totalTokens").as("totalTokens")
                        .sum("estimatedCost").as("cost")
                        .count().as("requests"),
                Aggregation.sort(Sort.Direction.DESC, "requests"),
                Aggregation.project("inputTokens", "outputTokens", "totalTokens", "cost", "requests")
                        .and("_id").as("featureType")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map m : results.getMappedResults()) {
            output.add((Map<String, Object>) m);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUsageByModel() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("model").exists(true).ne(null)),
                Aggregation.group("model")
                        .sum("inputTokens").as("inputTokens")
                        .sum("outputTokens").as("outputTokens")
                        .sum("totalTokens").as("totalTokens")
                        .sum("estimatedCost").as("cost")
                        .count().as("requests"),
                Aggregation.sort(Sort.Direction.DESC, "requests"),
                Aggregation.project("inputTokens", "outputTokens", "totalTokens", "cost", "requests")
                        .and("_id").as("model")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map m : results.getMappedResults()) {
            output.add((Map<String, Object>) m);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDailyUsage(int days) {
        Instant startDate = Instant.now().minus(days, ChronoUnit.DAYS);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(startDate)),
                Aggregation.project()
                        .andExpression("dateToString('%Y-%m-%d', createdAt)").as("date")
                        .and("totalTokens").as("tokens")
                        .and("estimatedCost").as("cost"),
                Aggregation.group("date")
                        .sum("tokens").as("tokens")
                        .sum("cost").as("cost")
                        .count().as("requests"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project("tokens", "cost", "requests")
                        .and("_id").as("date")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map m : results.getMappedResults()) {
            output.add((Map<String, Object>) m);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTopUsersByUsage(int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("userId", "userEmail")
                        .sum("inputTokens").as("inputTokens")
                        .sum("outputTokens").as("outputTokens")
                        .sum("totalTokens").as("totalTokens")
                        .sum("estimatedCost").as("cost")
                        .count().as("requests"),
                Aggregation.sort(Sort.Direction.DESC, "totalTokens"),
                Aggregation.limit(limit),
                Aggregation.project("inputTokens", "outputTokens", "totalTokens", "cost", "requests")
                        .and("_id.userId").as("userId")
                        .and("_id.userEmail").as("userEmail")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map m : results.getMappedResults()) {
            output.add((Map<String, Object>) m);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTopUsersByFeature(String featureType, int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("featureType").is(featureType)),
                Aggregation.group("userId", "userEmail")
                        .sum("totalTokens").as("totalTokens")
                        .sum("estimatedCost").as("cost")
                        .count().as("requests"),
                Aggregation.sort(Sort.Direction.DESC, "requests"),
                Aggregation.limit(limit),
                Aggregation.project("totalTokens", "cost", "requests")
                        .and("_id.userId").as("userId")
                        .and("_id.userEmail").as("userEmail")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map m : results.getMappedResults()) {
            output.add((Map<String, Object>) m);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserUsageStats(String userId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)),
                Aggregation.group()
                        .sum("inputTokens").as("totalInputTokens")
                        .sum("outputTokens").as("totalOutputTokens")
                        .sum("totalTokens").as("totalTokens")
                        .sum("estimatedCost").as("totalCost")
                        .count().as("totalRequests")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        Map result = results.getUniqueMappedResult();
        if (result == null) {
            return Map.of(
                    "totalInputTokens", 0L,
                    "totalOutputTokens", 0L,
                    "totalTokens", 0L,
                    "totalCost", BigDecimal.ZERO,
                    "totalRequests", 0L
            );
        }
        return (Map<String, Object>) result;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUserFeatureBreakdown(String userId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)),
                Aggregation.group("featureType")
                        .sum("totalTokens").as("tokens")
                        .sum("estimatedCost").as("cost")
                        .count().as("requests"),
                Aggregation.sort(Sort.Direction.DESC, "requests"),
                Aggregation.project("tokens", "cost", "requests")
                        .and("_id").as("featureType")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map m : results.getMappedResults()) {
            output.add((Map<String, Object>) m);
        }
        return output;
    }

    public List<AIUsageRecord> getRecentRecords(int page, int size) {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, AIUsageRecord.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getHourlyUsageToday() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(startOfDay)),
                Aggregation.project()
                        .andExpression("hour(createdAt)").as("hour")
                        .and("totalTokens").as("tokens")
                        .and("estimatedCost").as("cost"),
                Aggregation.group("hour")
                        .sum("tokens").as("tokens")
                        .sum("cost").as("cost")
                        .count().as("requests"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project("tokens", "cost", "requests")
                        .and("_id").as("hour")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "ai_usage_records", Map.class);

        Map<Integer, Map<String, Object>> hourMap = new HashMap<>();
        for (Map m : results.getMappedResults()) {
            int hour = ((Number) m.get("hour")).intValue();
            hourMap.put(hour, (Map<String, Object>) m);
        }

        List<Map<String, Object>> output = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            if (hourMap.containsKey(h)) {
                output.add(hourMap.get(h));
            } else {
                Map<String, Object> empty = new HashMap<>();
                empty.put("hour", h);
                empty.put("tokens", 0);
                empty.put("cost", 0);
                empty.put("requests", 0);
                output.add(empty);
            }
        }
        return output;
    }
}
