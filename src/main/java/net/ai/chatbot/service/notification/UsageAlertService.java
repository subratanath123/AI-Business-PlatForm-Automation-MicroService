package net.ai.chatbot.service.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.UserSubscriptionDao;
import net.ai.chatbot.dao.notification.BillingAlertConfigDao;
import net.ai.chatbot.dao.notification.NotificationDao;
import net.ai.chatbot.dto.admin.UserSubscription;
import net.ai.chatbot.dto.notification.BillingAlertConfig;
import net.ai.chatbot.dto.notification.Notification;
import net.ai.chatbot.dto.notification.NotificationType;
import net.ai.chatbot.service.subscription.PlanLimitValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scheduled service that checks usage against thresholds and creates
 * in-app {@link Notification}s and/or sends alert emails.
 *
 * <p>Default run: once per hour.
 * Deduplication window: 24 hours — so a user gets at most one alert per
 * metric per threshold per day.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UsageAlertService {

    private final UserSubscriptionDao userSubscriptionDao;
    private final PlanLimitValidator planLimitValidator;
    private final NotificationDao notificationDao;
    private final BillingAlertConfigDao billingAlertConfigDao;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:}")
    private String mailFrom;
    @Value("${spring.mail.username:}")
    private String springMailUsername;
    @Value("${app.frontend-url:https://subratapc.net}")
    private String frontendUrl;

    private static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    // ── metrics we track ────────────────────────────────────────────────
    private static final List<MetricDef> METRICS = List.of(
        new MetricDef("MESSAGES",        "Messages",        "messages this month", "/subscription"),
        new MetricDef("CHATBOTS",        "Chatbots",        "chatbots",            "/ai-chatbots"),
        new MetricDef("SOCIAL_ACCOUNTS", "Social Accounts", "social accounts",     "/social-media")
    );

    // ════════════════════════════════════════════════════════════════════
    // SCHEDULED JOB
    // ════════════════════════════════════════════════════════════════════

    @Scheduled(fixedDelayString = "${notifications.usage-check-interval-ms:3600000}")
    public void checkAllUsersUsage() {
        log.debug("Running usage alert check");
        int page = 0, size = 200, processed = 0;

        while (true) {
            List<UserSubscription> batch = userSubscriptionDao.findAll(page++, size, null);
            if (batch.isEmpty()) break;

            for (UserSubscription sub : batch) {
                try {
                    checkUserUsage(sub);
                    processed++;
                } catch (Exception e) {
                    log.warn("Error checking usage for user {}: {}", sub.getUserId(), e.getMessage());
                }
            }
            if (batch.size() < size) break;
        }
        log.debug("Usage alert check done — checked {} subscriptions", processed);
    }

    // ════════════════════════════════════════════════════════════════════
    // PER-USER CHECK (also callable directly for on-demand checks)
    // ════════════════════════════════════════════════════════════════════

    public void checkUserUsage(UserSubscription sub) {
        String userId = sub.getUserId();
        BillingAlertConfig cfg = billingAlertConfigDao.findOrCreate(userId);
        if (!cfg.isInAppEnabled() && !cfg.isEmailEnabled()) return;

        PlanLimitValidator.UsageSummary usage = planLimitValidator.getUserUsageSummary(userId);

        List<Integer> defaultThresholds = new ArrayList<>();
        if (cfg.isAlertAt50Enabled()) defaultThresholds.add(50);
        if (cfg.isAlertAt90Enabled()) defaultThresholds.add(90);

        for (MetricDef metric : METRICS) {
            int pct = getUsagePercent(usage, metric.key());
            if (pct < 0) continue; // unlimited or not tracked

            // Default thresholds
            for (int threshold : defaultThresholds) {
                if (pct >= threshold) {
                    maybeFireAlert(userId, sub.getUserEmail(), cfg, metric, pct, threshold,
                        cfg.isInAppEnabled(), cfg.isEmailEnabled());
                }
            }

            // Custom rules for this metric
            for (BillingAlertConfig.AlertRule rule : cfg.getCustomRules()) {
                if (metric.key().equalsIgnoreCase(rule.getMetric()) && pct >= rule.getThresholdPercent()) {
                    maybeFireAlert(userId, sub.getUserEmail(), cfg, metric, pct, rule.getThresholdPercent(),
                        rule.isInAppEnabled(), rule.isEmailEnabled());
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // INTERNAL
    // ════════════════════════════════════════════════════════════════════

    private void maybeFireAlert(String userId, String email,
                                BillingAlertConfig cfg,
                                MetricDef metric, int actualPct, int threshold,
                                boolean inApp, boolean sendEmail) {

        String dedupeKey = metric.key() + ":" + threshold;
        if (notificationDao.existsRecent(userId, dedupeKey, DEDUP_WINDOW)) return;

        NotificationType type = actualPct >= 90 ? NotificationType.USAGE_CRITICAL : NotificationType.USAGE_WARNING;
        String title   = metric.label() + " usage at " + actualPct + "%";
        String message = "You have used " + actualPct + "% of your " + metric.description()
                + ". Consider upgrading your plan to avoid interruptions.";

        if (inApp) {
            Notification n = Notification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .actionUrl(metric.actionUrl())
                    .read(false)
                    .metadata(Map.of(
                        "metric", metric.key(),
                        "percent", actualPct,
                        "threshold", threshold,
                        "dedupeKey", dedupeKey
                    ))
                    .build();
            notificationDao.save(n);
            log.info("Created usage alert for user {} — {} {}%", userId, metric.key(), actualPct);
        }

        if (sendEmail && StringUtils.hasText(email)) {
            sendAlertEmail(email, title, message, metric, actualPct);
        }
    }

    private void sendAlertEmail(String email, String subject, String bodyText,
                                MetricDef metric, int pct) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) return;
        String from = StringUtils.hasText(mailFrom) ? mailFrom : springMailUsername;
        if (!StringUtils.hasText(from)) return;

        String colour = pct >= 90 ? "#dc2626" : "#f59e0b";
        String icon   = pct >= 90 ? "🚨" : "⚠️";

        String html = "<html><body style=\"font-family:system-ui,sans-serif;background:#f8fafc;margin:0;padding:0;\">" +
            "<div style=\"max-width:560px;margin:40px auto;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);\">" +
            "<div style=\"background:" + colour + ";padding:28px 36px;\">" +
            "<h1 style=\"margin:0;color:#fff;font-size:20px;\">" + icon + " " + esc(subject) + "</h1>" +
            "</div>" +
            "<div style=\"padding:32px 36px;\">" +
            "<p style=\"font-size:15px;color:#475569;line-height:1.7;\">" + esc(bodyText) + "</p>" +
            "<a href=\"" + esc(frontendUrl + metric.actionUrl()) + "\" style=\"display:inline-block;margin-top:20px;padding:12px 24px;" +
            "background:linear-gradient(135deg,#2563eb,#7c3aed);color:#fff;text-decoration:none;border-radius:10px;font-weight:700;font-size:14px;\">Manage Plan →</a>" +
            "</div>" +
            "<div style=\"padding:16px 36px;background:#f8fafc;border-top:1px solid #e2e8f0;font-size:12px;color:#94a3b8;\">JadeAI — You received this because you have usage alerts enabled.</div>" +
            "</div></body></html>";

        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(from);
            h.setTo(email);
            h.setSubject(subject);
            h.setText(bodyText, html);
            sender.send(msg);
        } catch (MessagingException e) {
            log.warn("Failed to send usage alert email to {}: {}", email, e.getMessage());
        }
    }

    private int getUsagePercent(PlanLimitValidator.UsageSummary u, String metric) {
        return switch (metric) {
            case "MESSAGES"        -> ratio(u.getMessagesUsed(),       u.getMessagesLimit());
            case "CHATBOTS"        -> ratio(u.getChatbotsUsed(),       u.getChatbotsLimit());
            case "SOCIAL_ACCOUNTS" -> ratio(u.getSocialAccountsUsed(), u.getSocialAccountsLimit());
            default                -> -1;
        };
    }

    private static int ratio(int used, Integer limit) {
        if (limit == null || limit <= 0) return -1; // unlimited
        return Math.min(100, (int) Math.round(used * 100.0 / limit));
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    record MetricDef(String key, String label, String description, String actionUrl) {}
}
