package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI Generations summary across all media studios:
 * Image, Video, Photo Studio, Product Photo, Product Studio, Face Swap.
 *
 * <p>Counts are owner-scoped via {@code userEmail} on each *Job collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIGenerationsDashboardSummary {

    /**
     * Total jobs per feature in the requested window (default: lifetime).
     * Keys: image, video, photoStudio, productPhoto, productStudio, faceSwap.
     */
    private Map<String, Long> totalsByFeature;

    /** Sum of {@code totalsByFeature} for the same window. */
    private long totalGenerations;

    /**
     * Per-feature status histogram for the window. Outer key is the feature
     * name, inner map is the status (pending / processing / completed / failed)
     * to count.
     */
    private Map<String, Map<String, Long>> statusByFeature;

    /**
     * Success rate per feature in the window, computed as
     * {@code completed / (completed + failed)} (range 0.0–1.0).
     * Features with no terminal-state jobs report {@code 0.0}.
     */
    private Map<String, Double> successRateByFeature;

    /**
     * Daily counts for the last 30 days, one entry per day, oldest → newest.
     * Each entry has the calendar date (YYYY-MM-DD) and a count per feature.
     */
    private List<DayBreakdown> perDay30d;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayBreakdown {
        private String date;
        private long image;
        private long video;
        private long photoStudio;
        private long productPhoto;
        private long productStudio;
        private long faceSwap;
    }
}
