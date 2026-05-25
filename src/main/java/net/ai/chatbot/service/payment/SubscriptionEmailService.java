package net.ai.chatbot.service.payment;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.UserSubscription;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Sends transactional emails for subscription lifecycle events.
 * Currently handles: successful subscription activation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEmailService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy").withZone(ZoneOffset.UTC);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String springMailUsername;

    @Value("${app.frontend-url:https://subratapc.net}")
    private String frontendUrl;

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sends a subscription confirmation email to the subscriber.
     */
    public void sendSubscriptionConfirmation(UserSubscription sub) {
        if (!StringUtils.hasText(sub.getUserEmail())) {
            log.debug("No email on subscription {} — skipping confirmation", sub.getId());
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("JavaMailSender not available — subscription confirmation not sent for {}", sub.getUserEmail());
            return;
        }

        String from = resolveFrom();
        if (!StringUtils.hasText(from)) {
            log.warn("No mail.from configured — subscription confirmation not sent");
            return;
        }

        String planName   = sub.getPlanName() != null ? sub.getPlanName() : sub.getPlanCode();
        String billing    = sub.getPricingType() != null ? sub.getPricingType().name() : "MONTHLY";
        String billingLabel = "YEARLY".equalsIgnoreCase(billing) ? "Annual" : "Monthly";
        String endDate    = sub.getEndDate() != null ? DATE_FMT.format(sub.getEndDate()) : "—";
        String amount     = sub.getAmountPaid() != null
                ? String.format("$%.2f %s", sub.getAmountPaid(), sub.getCurrency() != null ? sub.getCurrency().toUpperCase() : "USD")
                : "Free";
        String userName   = StringUtils.hasText(sub.getUserEmail())
                ? sub.getUserEmail().split("@")[0]
                : "there";

        String subject = "Your " + planName + " plan is now active — JadeAI";

        String textBody = buildTextBody(userName, planName, billingLabel, amount, endDate);
        String htmlBody = buildHtmlBody(userName, planName, billingLabel, amount, endDate);

        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(sub.getUserEmail());
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            sender.send(msg);
            log.info("Sent subscription confirmation to {} for plan {}", sub.getUserEmail(), planName);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send subscription confirmation to {}: {}", sub.getUserEmail(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending subscription confirmation to {}", sub.getUserEmail(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EMAIL BODY BUILDERS
    // ═══════════════════════════════════════════════════════════════════════

    private String buildTextBody(String name, String planName, String billing,
                                  String amount, String endDate) {
        return String.format(
            "Hi %s,%n%n" +
            "Your subscription to the %s plan is now active!%n%n" +
            "Plan:     %s%n" +
            "Billing:  %s%n" +
            "Amount:   %s%n" +
            "Renews:   %s%n%n" +
            "You can manage your subscription and view usage limits at:%n" +
            "%s/subscription%n%n" +
            "Thank you for choosing JadeAI. We're excited to have you on board!%n%n" +
            "Best regards,%nThe JadeAI Team",
            name, planName, planName, billing, amount, endDate, frontendUrl
        );
    }

    private String buildHtmlBody(String name, String planName, String billing,
                                  String amount, String endDate) {
        String dashboardUrl = frontendUrl + "/";
        String manageUrl    = frontendUrl + "/subscription";

        return "<html><body style=\"margin:0;padding:0;background:#f8fafc;font-family:system-ui,'Segoe UI',sans-serif;\">" +
            "<div style=\"max-width:600px;margin:40px auto;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);\">" +

            // Header
            "<div style=\"background:linear-gradient(135deg,#2563eb 0%,#7c3aed 100%);padding:40px 40px 32px;text-align:center;\">" +
            "<div style=\"font-size:32px;margin-bottom:8px;\">🎉</div>" +
            "<h1 style=\"margin:0;color:#ffffff;font-size:24px;font-weight:800;letter-spacing:-0.5px;\">You're all set!</h1>" +
            "<p style=\"margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:15px;\">Your <strong>" + escHtml(planName) + "</strong> plan is now active</p>" +
            "</div>" +

            // Body
            "<div style=\"padding:36px 40px;\">" +
            "<p style=\"margin:0 0 24px;font-size:16px;color:#0f172a;\">Hi <strong>" + escHtml(name) + "</strong>,</p>" +
            "<p style=\"margin:0 0 24px;font-size:15px;color:#475569;line-height:1.6;\">Thank you for subscribing to JadeAI! Your payment was successful and your account has been upgraded. Here's a summary of your subscription:</p>" +

            // Summary table
            "<div style=\"background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;margin-bottom:28px;\">" +
            summaryRow("Plan",        planName,   true) +
            summaryRow("Billing",     billing,    false) +
            summaryRow("Amount paid", amount,     true) +
            summaryRow("Next renewal", endDate,   false) +
            "</div>" +

            // CTA buttons
            "<div style=\"display:flex;gap:12px;justify-content:center;flex-wrap:wrap;margin-bottom:28px;\">" +
            "<a href=\"" + escHtml(dashboardUrl) + "\" style=\"display:inline-block;padding:13px 28px;background:linear-gradient(135deg,#2563eb,#7c3aed);color:#ffffff;text-decoration:none;border-radius:10px;font-size:15px;font-weight:700;\">Go to Dashboard</a>" +
            "<a href=\"" + escHtml(manageUrl) + "\" style=\"display:inline-block;padding:13px 24px;background:#fff;color:#475569;text-decoration:none;border-radius:10px;font-size:15px;font-weight:600;border:1px solid #e2e8f0;\">Manage Subscription</a>" +
            "</div>" +

            "<p style=\"margin:0;font-size:13px;color:#94a3b8;line-height:1.6;\">If you have any questions or need help, just reply to this email or visit our <a href=\"" + escHtml(frontendUrl + "/support") + "\" style=\"color:#6366f1;text-decoration:none;\">support centre</a>.</p>" +
            "</div>" +

            // Footer
            "<div style=\"background:#f8fafc;border-top:1px solid #e2e8f0;padding:20px 40px;text-align:center;\">" +
            "<p style=\"margin:0;font-size:12px;color:#94a3b8;\">JadeAI &bull; You received this because you subscribed to a JadeAI plan.</p>" +
            "</div>" +

            "</div></body></html>";
    }

    private static String summaryRow(String label, String value, boolean shaded) {
        String bg = shaded ? "background:#f1f5f9;" : "background:#f8fafc;";
        return "<div style=\"display:flex;justify-content:space-between;padding:12px 18px;" + bg + "\">" +
               "<span style=\"font-size:14px;color:#64748b;font-weight:500;\">" + escHtml(label) + "</span>" +
               "<span style=\"font-size:14px;color:#0f172a;font-weight:700;\">" + escHtml(value) + "</span>" +
               "</div>";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private String resolveFrom() {
        if (StringUtils.hasText(mailFrom)) return mailFrom;
        if (StringUtils.hasText(springMailUsername)) return springMailUsername;
        return null;
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
