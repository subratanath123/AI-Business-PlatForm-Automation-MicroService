package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Social Media Suite summary for the dashboard. Combines connected-account
 * breakdown, post status distribution, recent publishing activity, and a
 * 14-day forward-looking calendar density bucket list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialDashboardSummary {

    /** Connected social accounts grouped by platform (facebook / twitter / linkedin). */
    private Map<String, Long> accountsByPlatform;

    /** Total connected accounts across all platforms. */
    private long totalConnectedAccounts;

    /** Posts grouped by status (scheduled / pending_publish / published). */
    private Map<String, Long> postsByStatus;

    /** Total posts the user has ever scheduled or published. */
    private long totalPosts;

    /** Posts currently scheduled for the future (status = scheduled, scheduledAt > now). */
    private long postsScheduledUpcoming;

    /** Posts published in the trailing 7 days. */
    private long postsPublished7d;

    /**
     * Daily counts of scheduled posts for the next 14 days.
     * Each entry has {@code date} (YYYY-MM-DD) and {@code count}. Days with
     * zero scheduled posts are still included so the frontend can render a
     * dense calendar strip without gaps.
     */
    private List<DayCount> scheduledNext14d;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayCount {
        private String date;
        private long count;
    }
}
