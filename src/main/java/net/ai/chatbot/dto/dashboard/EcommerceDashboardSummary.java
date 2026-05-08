package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * E-commerce performance summary for the dashboard. Aggregates connected
 * stores, enhancement-job lifecycle status, and weekly throughput across
 * the five supported platforms (Shopify, WooCommerce, Amazon, eBay,
 * AliExpress).
 *
 * <p>Job counts are owner-scoped via {@code userId} on each
 * {@code *_product_enhancement_jobs} collection. Note that Shopify and
 * WooCommerce both share {@code product_enhancement_jobs} and are
 * distinguished by the {@code platform} field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcommerceDashboardSummary {

    /**
     * Number of active connected stores per platform. Keys are lowercased:
     * {@code shopify}, {@code woocommerce}, {@code amazon}, {@code ebay},
     * {@code aliexpress}.
     */
    private Map<String, Long> integrationsByPlatform;

    /** Sum of {@code integrationsByPlatform}. */
    private long totalConnectedStores;

    /**
     * Enhancement-job status histogram, nested as {@code platform → status →
     * count}. Status values come from each entity's enum:
     * PENDING, PROCESSING, ENHANCED, PUBLISHED, FAILED.
     */
    private Map<String, Map<String, Long>> jobsByStatusByPlatform;

    /** Sum of all status buckets across all platforms. */
    private long totalJobs;

    /**
     * Weekly throughput for the last 8 ISO weeks (oldest → newest), one entry
     * per week, with a per-platform count of jobs created in that week.
     */
    private List<WeekBreakdown> perWeek8w;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekBreakdown {
        /** Monday of the ISO week, formatted YYYY-MM-DD. */
        private String weekStart;
        private long shopify;
        private long woocommerce;
        private long amazon;
        private long ebay;
        private long aliexpress;
    }
}
