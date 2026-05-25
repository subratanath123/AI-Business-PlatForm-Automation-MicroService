package net.ai.chatbot.dto.notification;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-user billing alert preferences.
 * One document per user in the {@code billing_alert_configs} collection.
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "billing_alert_configs")
public class BillingAlertConfig {

    @Id
    @Indexed(unique = true)
    private String userId;

    /** Auto-alert when any metric crosses 50 %. Always on by default. */
    @Builder.Default
    private boolean alertAt50Enabled = true;

    /** Auto-alert when any metric crosses 90 %. Always on by default. */
    @Builder.Default
    private boolean alertAt90Enabled = true;

    /** Send in-app notifications (bell icon). */
    @Builder.Default
    private boolean inAppEnabled = true;

    /** Send email notifications for alerts. */
    @Builder.Default
    private boolean emailEnabled = true;

    /** Custom user-defined alert rules. */
    @Builder.Default
    private List<AlertRule> customRules = new ArrayList<>();

    private Instant updatedAt;

    // ── nested ──────────────────────────────────────────────────────────

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AlertRule {
        /** e.g. MESSAGES, CHATBOTS, AI_CONTENT, SOCIAL_ACCOUNTS, API_CALLS */
        private String metric;
        /** 1–99: percentage threshold that triggers this rule */
        private int thresholdPercent;
        private boolean emailEnabled;
        private boolean inAppEnabled;
    }
}
