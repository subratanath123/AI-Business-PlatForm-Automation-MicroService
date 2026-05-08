package net.ai.chatbot.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.dashboard.EcommerceDashboardSummary;
import net.ai.chatbot.entity.AliExpressIntegration;
import net.ai.chatbot.entity.AliExpressProductEnhancementJob;
import net.ai.chatbot.entity.AmazonIntegration;
import net.ai.chatbot.entity.AmazonProductEnhancementJob;
import net.ai.chatbot.entity.EbayIntegration;
import net.ai.chatbot.entity.EbayProductEnhancementJob;
import net.ai.chatbot.entity.ProductEnhancementJob;
import net.ai.chatbot.entity.ShopifyIntegration;
import net.ai.chatbot.entity.WooCommerceIntegration;
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
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregates connected stores and product-enhancement jobs across all five
 * supported e-commerce platforms into a single
 * {@link EcommerceDashboardSummary}. All collections are scoped by
 * {@code userId} (Clerk JWT {@code sub}).
 *
 * <p>Note: Shopify and WooCommerce share {@code product_enhancement_jobs}
 * and are distinguished by the {@code platform} field on each document;
 * Amazon, eBay, and AliExpress have dedicated job collections.
 */
@Service
@Slf4j
public class EcommerceDashboardService {

    private static final String SHOPIFY = "shopify";
    private static final String WOOCOMMERCE = "woocommerce";
    private static final String AMAZON = "amazon";
    private static final String EBAY = "ebay";
    private static final String ALIEXPRESS = "aliexpress";

    private final MongoTemplate mongoTemplate;

    public EcommerceDashboardService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public EcommerceDashboardSummary getSummary() {
        String userId = AuthUtils.getUserId();
        if (userId == null || userId.isBlank()) {
            return emptySummary();
        }

        Map<String, Long> integrationsByPlatform = new LinkedHashMap<>();
        integrationsByPlatform.put(SHOPIFY, countConnected(userId, ShopifyIntegration.class));
        integrationsByPlatform.put(WOOCOMMERCE, countConnected(userId, WooCommerceIntegration.class));
        integrationsByPlatform.put(AMAZON, countConnected(userId, AmazonIntegration.class));
        integrationsByPlatform.put(EBAY, countConnected(userId, EbayIntegration.class));
        integrationsByPlatform.put(ALIEXPRESS, countConnected(userId, AliExpressIntegration.class));
        long totalConnectedStores = integrationsByPlatform.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Map<String, Long>> jobsByStatusByPlatform = new LinkedHashMap<>();
        // Shopify + WooCommerce share the same collection but split on platform.
        jobsByStatusByPlatform.put(SHOPIFY,
                statusBreakdownForSharedJobs(userId, "SHOPIFY"));
        jobsByStatusByPlatform.put(WOOCOMMERCE,
                statusBreakdownForSharedJobs(userId, "WOOCOMMERCE"));
        jobsByStatusByPlatform.put(AMAZON,
                statusBreakdown(userId, AmazonProductEnhancementJob.class));
        jobsByStatusByPlatform.put(EBAY,
                statusBreakdown(userId, EbayProductEnhancementJob.class));
        jobsByStatusByPlatform.put(ALIEXPRESS,
                statusBreakdown(userId, AliExpressProductEnhancementJob.class));

        long totalJobs = jobsByStatusByPlatform.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToLong(Long::longValue)
                .sum();

        return EcommerceDashboardSummary.builder()
                .integrationsByPlatform(integrationsByPlatform)
                .totalConnectedStores(totalConnectedStores)
                .jobsByStatusByPlatform(jobsByStatusByPlatform)
                .totalJobs(totalJobs)
                .perWeek8w(perWeek(userId, 8))
                .build();
    }

    private long countConnected(String userId, Class<?> integrationClass) {
        return mongoTemplate.count(
                new Query(Criteria.where("userId").is(userId).and("connected").is(true)),
                integrationClass);
    }

    private Map<String, Long> statusBreakdownForSharedJobs(String userId, String platform) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId).and("platform").is(platform)),
                Aggregation.group("status").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, ProductEnhancementJob.class, Map.class);
        return toStatusMap(results);
    }

    private Map<String, Long> statusBreakdown(String userId, Class<?> jobClass) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)),
                Aggregation.group("status").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, jobClass, Map.class);
        return toStatusMap(results);
    }

    private Map<String, Long> toStatusMap(AggregationResults<Map> results) {
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

    private List<EcommerceDashboardSummary.WeekBreakdown> perWeek(String userId, int weeks) {
        // ISO Monday-based week buckets: oldest first → newest last.
        WeekFields wf = WeekFields.of(Locale.ROOT);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startOfThisWeek = today.with(wf.dayOfWeek(), 1L);
        LocalDate windowStart = startOfThisWeek.minusWeeks(weeks - 1L);
        Instant windowStartInstant = windowStart.atStartOfDay(ZoneOffset.UTC).toInstant();

        // Build empty buckets first so the API is shape-stable even with no data.
        Map<String, EcommerceDashboardSummary.WeekBreakdown> buckets = new LinkedHashMap<>();
        LocalDate cursor = windowStart;
        for (int i = 0; i < weeks; i++) {
            String key = cursor.toString();
            buckets.put(key, EcommerceDashboardSummary.WeekBreakdown.builder()
                    .weekStart(key)
                    .shopify(0).woocommerce(0).amazon(0).ebay(0).aliexpress(0)
                    .build());
            cursor = cursor.plusWeeks(1);
        }

        accumulateSharedJobsByWeek(userId, "SHOPIFY", windowStartInstant, buckets, SHOPIFY);
        accumulateSharedJobsByWeek(userId, "WOOCOMMERCE", windowStartInstant, buckets, WOOCOMMERCE);
        accumulateJobsByWeek(userId, AmazonProductEnhancementJob.class, windowStartInstant, buckets, AMAZON);
        accumulateJobsByWeek(userId, EbayProductEnhancementJob.class, windowStartInstant, buckets, EBAY);
        accumulateJobsByWeek(userId, AliExpressProductEnhancementJob.class, windowStartInstant, buckets, ALIEXPRESS);

        return new LinkedList<>(buckets.values());
    }

    private void accumulateSharedJobsByWeek(String userId, String platform, Instant windowStart,
                                            Map<String, EcommerceDashboardSummary.WeekBreakdown> buckets,
                                            String platformKey) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)
                        .and("platform").is(platform)
                        .and("createdAt").gte(windowStart)),
                Aggregation.project()
                        .andExpression("dateToString('%Y-%m-%d', $createdAt)").as("date"),
                Aggregation.group("date").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, ProductEnhancementJob.class, Map.class);
        applyToBuckets(results, buckets, platformKey);
    }

    private void accumulateJobsByWeek(String userId, Class<?> jobClass, Instant windowStart,
                                      Map<String, EcommerceDashboardSummary.WeekBreakdown> buckets,
                                      String platformKey) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)
                        .and("createdAt").gte(windowStart)),
                Aggregation.project()
                        .andExpression("dateToString('%Y-%m-%d', $createdAt)").as("date"),
                Aggregation.group("date").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, jobClass, Map.class);
        applyToBuckets(results, buckets, platformKey);
    }

    private void applyToBuckets(AggregationResults<Map> results,
                                Map<String, EcommerceDashboardSummary.WeekBreakdown> buckets,
                                String platformKey) {
        WeekFields wf = WeekFields.of(Locale.ROOT);
        for (Map<?, ?> row : results.getMappedResults()) {
            Object dateObj = row.get("_id");
            Object countObj = row.get("count");
            if (!(dateObj instanceof String dateStr) || !(countObj instanceof Number n)) continue;

            LocalDate d;
            try {
                d = LocalDate.parse(dateStr);
            } catch (Exception ex) {
                continue;
            }
            LocalDate weekStart = d.with(wf.dayOfWeek(), 1L);
            String weekKey = weekStart.toString();
            EcommerceDashboardSummary.WeekBreakdown bucket = buckets.get(weekKey);
            if (bucket == null) continue;

            long count = n.longValue();
            switch (platformKey) {
                case SHOPIFY -> bucket.setShopify(bucket.getShopify() + count);
                case WOOCOMMERCE -> bucket.setWoocommerce(bucket.getWoocommerce() + count);
                case AMAZON -> bucket.setAmazon(bucket.getAmazon() + count);
                case EBAY -> bucket.setEbay(bucket.getEbay() + count);
                case ALIEXPRESS -> bucket.setAliexpress(bucket.getAliexpress() + count);
                default -> { /* ignored */ }
            }
        }
    }

    /**
     * Lightweight count for the KPI strip — sums connected stores across all
     * five platforms in five small COUNT queries.
     */
    public long countAllConnectedStores() {
        String userId = AuthUtils.getUserId();
        if (userId == null || userId.isBlank()) return 0L;
        long total = 0L;
        for (Class<?> cls : List.of(
                ShopifyIntegration.class,
                WooCommerceIntegration.class,
                AmazonIntegration.class,
                EbayIntegration.class,
                AliExpressIntegration.class)) {
            total += countConnected(userId, cls);
        }
        return total;
    }

    private EcommerceDashboardSummary emptySummary() {
        Map<String, Long> integrations = new LinkedHashMap<>();
        integrations.put(SHOPIFY, 0L);
        integrations.put(WOOCOMMERCE, 0L);
        integrations.put(AMAZON, 0L);
        integrations.put(EBAY, 0L);
        integrations.put(ALIEXPRESS, 0L);

        Map<String, Map<String, Long>> jobs = new LinkedHashMap<>();
        for (String p : integrations.keySet()) jobs.put(p, Map.of());

        return EcommerceDashboardSummary.builder()
                .integrationsByPlatform(integrations)
                .totalConnectedStores(0)
                .jobsByStatusByPlatform(jobs)
                .totalJobs(0)
                .perWeek8w(List.of())
                .build();
    }
}
