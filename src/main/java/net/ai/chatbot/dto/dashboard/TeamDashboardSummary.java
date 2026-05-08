package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Team membership summary for the signed-in owner. Members and roles come
 * from the {@code team_memberships} collection, scoped to documents whose
 * {@code ownerEmail} equals the current user's email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDashboardSummary {

    /** Total active team members for the current owner. */
    private long size;

    /** Members grouped by role (ADMIN / EDITOR / VIEWER). */
    private Map<String, Long> byRole;

    /** Most recent invites (limit 5), newest first. */
    private List<RecentMember> recent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentMember {
        private String memberEmail;
        private String role;
        private Instant createdAt;
    }
}
