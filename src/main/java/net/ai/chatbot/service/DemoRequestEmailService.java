package net.ai.chatbot.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.DemoRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoRequestEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String springMailUsername;

    @Value("${demo.request.recipient:${support.email.admin-recipients:}}")
    private String demoRecipient;

    private static final Map<String, String> GOAL_LABELS = Map.of(
        "content-creation", "AI Content Creation",
        "customer-support", "Customer Support Automation",
        "social-media", "Social Media Management",
        "ecommerce", "E-commerce Product Management",
        "all-in-one", "All-in-One Solution"
    );

    public boolean sendDemoRequestEmail(DemoRequest request) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Demo request email skipped: JavaMailSender is not configured.");
            logDemoRequest(request);
            return true;
        }

        String from = resolveFromAddress();
        if (!StringUtils.hasText(from)) {
            log.warn("Demo request email skipped: no from address configured.");
            logDemoRequest(request);
            return true;
        }

        String recipient = resolveRecipient();
        if (!StringUtils.hasText(recipient)) {
            log.warn("Demo request email skipped: no recipient configured. Set demo.request.recipient or support.email.admin-recipients.");
            logDemoRequest(request);
            return true;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient.split(","));
            helper.setReplyTo(request.getEmail());
            helper.setSubject("🎯 New Demo Request from " + request.getFullName() + " (" + request.getCompany() + ")");
            helper.setText(buildTextBody(request), buildHtmlBody(request));
            sender.send(message);
            log.info("Demo request email sent successfully for {} ({})", request.getFullName(), request.getEmail());
            return true;
        } catch (MessagingException | MailException e) {
            log.error("Failed to send demo request email: {}", e.getMessage(), e);
            logDemoRequest(request);
            return false;
        }
    }

    private void logDemoRequest(DemoRequest request) {
        log.info("=== NEW DEMO REQUEST ===");
        log.info("Name: {}", request.getFullName());
        log.info("Email: {}", request.getEmail());
        log.info("Company: {}", request.getCompany());
        log.info("Phone: {}", request.getPhone());
        log.info("Company Size: {}", request.getCompanySize());
        log.info("Primary Goal: {}", GOAL_LABELS.getOrDefault(request.getPrimaryGoal(), request.getPrimaryGoal()));
        log.info("========================");
    }

    private String resolveFromAddress() {
        if (StringUtils.hasText(mailFrom)) {
            return mailFrom.trim();
        }
        if (StringUtils.hasText(springMailUsername)) {
            return springMailUsername.trim();
        }
        return null;
    }

    private String resolveRecipient() {
        if (StringUtils.hasText(demoRecipient)) {
            return demoRecipient.trim();
        }
        return null;
    }

    private String buildTextBody(DemoRequest request) {
        String goalLabel = GOAL_LABELS.getOrDefault(request.getPrimaryGoal(), request.getPrimaryGoal());
        return String.format("""
            New Demo Request from Jade AI Website
            
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            
            Contact Information:
            • Full Name: %s
            • Email: %s
            • Phone: %s
            • Company: %s
            • Company Size: %s employees
            
            Primary Goal: %s
            
            Submitted: %s
            
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            
            Please follow up with this lead within 24 hours.
            
            This is an automated message from Jade AIBOT website.
            """,
            request.getFullName(),
            request.getEmail(),
            StringUtils.hasText(request.getPhone()) ? request.getPhone() : "Not provided",
            request.getCompany(),
            request.getCompanySize(),
            goalLabel,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        );
    }

    private String buildHtmlBody(DemoRequest request) {
        String goalLabel = GOAL_LABELS.getOrDefault(request.getPrimaryGoal(), request.getPrimaryGoal());
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
              <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background: linear-gradient(90deg, #7c5cff, #a78bfa); color: white; padding: 30px; border-radius: 16px 16px 0 0; text-align: center; }
                .header h1 { margin: 0; font-size: 24px; }
                .header p { margin: 10px 0 0; opacity: 0.9; }
                .content { background: #f9fafb; padding: 30px; border-radius: 0 0 16px 16px; border: 1px solid #e5e7eb; border-top: none; }
                .field { margin-bottom: 16px; }
                .label { font-weight: 600; color: #666; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; }
                .value { font-size: 16px; color: #222; margin-top: 4px; }
                .goal { background: linear-gradient(90deg, #7c5cff, #a78bfa); color: white; padding: 12px 20px; border-radius: 8px; display: inline-block; margin-top: 16px; font-weight: 600; }
                .footer { text-align: center; margin-top: 24px; color: #888; font-size: 12px; }
                .cta { background: #10b981; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; display: inline-block; margin-top: 20px; font-weight: 600; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>🎯 New Demo Request</h1>
                  <p>Someone wants to see Jade AI in action!</p>
                </div>
                <div class="content">
                  <div class="field">
                    <div class="label">Full Name</div>
                    <div class="value">%s</div>
                  </div>
                  <div class="field">
                    <div class="label">Email</div>
                    <div class="value"><a href="mailto:%s" style="color:#7c5cff;">%s</a></div>
                  </div>
                  <div class="field">
                    <div class="label">Phone</div>
                    <div class="value">%s</div>
                  </div>
                  <div class="field">
                    <div class="label">Company</div>
                    <div class="value">%s</div>
                  </div>
                  <div class="field">
                    <div class="label">Company Size</div>
                    <div class="value">%s employees</div>
                  </div>
                  <div class="field">
                    <div class="label">Primary Goal</div>
                    <div class="goal">%s</div>
                  </div>
                  <div style="margin-top: 24px; padding-top: 20px; border-top: 1px solid #e5e7eb;">
                    <a href="mailto:%s" class="cta">Reply to Lead →</a>
                  </div>
                </div>
                <div class="footer">
                  Please follow up within 24 hours<br>
                  Jade AIBOT Website • %s
                </div>
              </div>
            </body>
            </html>
            """,
            escapeHtml(request.getFullName()),
            escapeHtml(request.getEmail()),
            escapeHtml(request.getEmail()),
            StringUtils.hasText(request.getPhone()) ? escapeHtml(request.getPhone()) : "Not provided",
            escapeHtml(request.getCompany()),
            escapeHtml(request.getCompanySize()),
            escapeHtml(goalLabel),
            escapeHtml(request.getEmail()),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        );
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
