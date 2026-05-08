package net.ai.chatbot.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.dashboard.AssetsDashboardSummary;
import net.ai.chatbot.entity.MediaAsset;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates the user's media library into a coarse breakdown for the
 * dashboard "Assets" card. {@code media_assets} is keyed by
 * {@code userEmail}.
 */
@Service
@Slf4j
public class AssetsDashboardService {

    private final MongoTemplate mongoTemplate;

    public AssetsDashboardService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AssetsDashboardSummary getSummary() {
        String userEmail = AuthUtils.getEmail();
        if (userEmail == null || userEmail.isBlank()) {
            return AssetsDashboardSummary.builder()
                    .total(0).byMimeBucket(Map.of()).uploads30d(0).build();
        }

        long total = mongoTemplate.count(
                new Query(Criteria.where("userEmail").is(userEmail)),
                MediaAsset.class);

        long uploads30d = mongoTemplate.count(
                new Query(Criteria.where("userEmail").is(userEmail)
                        .and("createdAt").gte(Instant.now().minus(30, ChronoUnit.DAYS))),
                MediaAsset.class);

        Map<String, Long> byBucket = bucketByMime(userEmail);

        return AssetsDashboardSummary.builder()
                .total(total)
                .byMimeBucket(byBucket)
                .uploads30d(uploads30d)
                .build();
    }

    private Map<String, Long> bucketByMime(String userEmail) {
        // Aggregate by the leading "type" portion of the MIME (e.g. "image" out
        // of "image/png"); fall back to "other" when the field is absent or
        // unparseable. Doing the bucketing in $project keeps the group small
        // (≤ 5 buckets) regardless of the document count.
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userEmail").is(userEmail)),
                Aggregation.project()
                        .and("mimeType").as("mimeType"),
                Aggregation.group("mimeType").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, MediaAsset.class, Map.class);

        Map<String, Long> bucketed = new LinkedHashMap<>();
        bucketed.put("image", 0L);
        bucketed.put("video", 0L);
        bucketed.put("audio", 0L);
        bucketed.put("document", 0L);
        bucketed.put("other", 0L);

        for (Map<?, ?> row : results.getMappedResults()) {
            Object mimeObj = row.get("_id");
            Object countObj = row.get("count");
            if (!(countObj instanceof Number n)) continue;
            String bucket = bucketize(mimeObj == null ? null : String.valueOf(mimeObj));
            bucketed.merge(bucket, n.longValue(), Long::sum);
        }
        return bucketed;
    }

    private static String bucketize(String mime) {
        if (mime == null) return "other";
        String lower = mime.toLowerCase();
        if (lower.startsWith("image/")) return "image";
        if (lower.startsWith("video/")) return "video";
        if (lower.startsWith("audio/")) return "audio";
        if (lower.startsWith("application/pdf")
                || lower.startsWith("application/msword")
                || lower.startsWith("application/vnd.openxmlformats-officedocument")
                || lower.startsWith("application/vnd.ms-")
                || lower.startsWith("text/")) {
            return "document";
        }
        return "other";
    }
}
