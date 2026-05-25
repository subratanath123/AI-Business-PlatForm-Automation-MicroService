package net.ai.chatbot.service.admin;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.SupportTicket;
import net.ai.chatbot.dto.admin.TicketMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.ai.chatbot.dao.admin.SupportTicketDao;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketEmailNotificationService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SupportTicketDao supportTicketDao;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String springMailUsername;

    @Value("${support.email.admin-recipients:}")
    private String adminRecipients;

    @Value("${app.frontend-url:}")
    private String frontendBaseUrl;

    public void notifyTicketResolved(SupportTicket ticket) {
        Set<String> recipients = new LinkedHashSet<>();
        addIfPresent(recipients, ticket.getUserEmail());
        sendResolvedEmail(ticket, recipients);
    }

    public void notifyTicketCreated(SupportTicket ticket) {
        Set<String> recipients = new LinkedHashSet<>(resolveAdminRecipients());
        addIfPresent(recipients, ticket.getUserEmail());
        sendTranscriptEmail(ticket, recipients, "New support ticket created");
    }

    public void notifyTicketReply(SupportTicket ticket, TicketMessage latestMessage) {
        Set<String> recipients = new LinkedHashSet<>();
        addIfPresent(recipients, ticket.getUserEmail());
        addIfPresent(recipients, latestMessage.getSenderEmail());

        if (!latestMessage.isAdmin()) {
            recipients.addAll(resolveAdminRecipients());
        }

        sendTranscriptEmail(ticket, recipients, "New support ticket reply");
    }

    private void sendResolvedEmail(SupportTicket ticket, Set<String> recipients) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.debug("Resolved ticket email skipped: JavaMailSender is not configured.");
            return;
        }

        String from = resolveFromAddress();
        if (!StringUtils.hasText(from)) {
            log.warn("Resolved ticket email skipped: no from address configured.");
            return;
        }

        List<String> validRecipients = recipients.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (validRecipients.isEmpty()) {
            return;
        }

        String replyTo = buildReplyToAddress(ticket, from);
        String ticketLink = buildTicketLink(ticket);
        String customerName = StringUtils.hasText(ticket.getUserName()) ? ticket.getUserName() : "there";

        String textBody = String.format(
            "Hi %s,%n%n" +
            "Great news! Your support ticket #%s \"%s\" has been resolved.%n%n" +
            "We hope everything is working well for you. If you have any follow-up questions or " +
            "if the issue persists, simply reply to this email and we will re-open your ticket right away.%n%n" +
            "%s" +
            "Thank you for reaching out to us — we truly appreciate your patience.%n%n" +
            "Best regards,%nThe Support Team",
            customerName,
            ticket.getTicketNumber(),
            nullToEmpty(ticket.getSubject()),
            StringUtils.hasText(ticketLink) ? "You can also view your ticket at: " + ticketLink + "%n%n" : ""
        );

        String htmlBody = String.format(
            "<html><body style=\"font-family:system-ui,Segoe UI,sans-serif;line-height:1.6;color:#0f172a;max-width:600px;\">" +
            "<div style=\"background:#f0fdf4;border:1px solid #bbf7d0;border-radius:12px;padding:20px 24px;margin-bottom:20px;\">" +
            "<h2 style=\"margin:0 0 8px;color:#15803d;\">&#10003; Your ticket has been resolved</h2>" +
            "<p style=\"margin:0;font-size:14px;color:#166534;\">Ticket #%s &mdash; %s</p>" +
            "</div>" +
            "<p>Hi <strong>%s</strong>,</p>" +
            "<p>We are happy to let you know that your support request has been resolved.</p>" +
            "<p>If you have any follow-up questions or if the issue comes back, " +
            "just <strong>reply to this email</strong> and we will re-open your ticket right away.</p>" +
            "%s" +
            "<p style=\"color:#64748b;font-size:13px;\">Thank you for your patience. We appreciate you choosing our platform.</p>" +
            "<p>Best regards,<br/><strong>The Support Team</strong></p>" +
            "</body></html>",
            escapeHtml(ticket.getTicketNumber()),
            escapeHtml(nullToEmpty(ticket.getSubject())),
            escapeHtml(customerName),
            StringUtils.hasText(ticketLink)
                ? "<p><a href=\"" + escapeHtml(ticketLink) + "\" style=\"color:#2563eb;\">View your ticket</a></p>"
                : ""
        );

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            if (StringUtils.hasText(replyTo)) {
                helper.setReplyTo(replyTo);
            }
            helper.setTo(validRecipients.toArray(String[]::new));
            helper.setSubject(buildThreadSubject(ticket));
            helper.setText(textBody, htmlBody);
            applyThreadingHeaders(message, ticket, from);
            sender.send(message);
            log.info("Sent resolved ticket email for {} to {}", ticket.getTicketNumber(), validRecipients);
        } catch (MessagingException e) {
            log.error("Failed to send resolved ticket email for {}: {}", ticket.getTicketNumber(), e.getMessage());
        } catch (MailException e) {
            log.error("Failed to send resolved ticket email for {} via SMTP: {}", ticket.getTicketNumber(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected failure sending resolved ticket email for {}: {}", ticket.getTicketNumber(), e.getMessage(), e);
        }
    }

    private void sendTranscriptEmail(SupportTicket ticket, Set<String> recipients, String eventTitle) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.debug("Support ticket email skipped: JavaMailSender is not configured.");
            return;
        }

        String from = resolveFromAddress();
        if (!StringUtils.hasText(from)) {
            log.warn("Support ticket email skipped: set app.mail.from or spring.mail.username.");
            return;
        }

        List<String> validRecipients = recipients.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (validRecipients.isEmpty()) {
            log.warn("Support ticket email skipped for ticket {}: no recipients.", ticket.getTicketNumber());
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            String replyTo = buildReplyToAddress(ticket, from);
            if (StringUtils.hasText(replyTo)) {
                helper.setReplyTo(replyTo);
            }
            helper.setTo(validRecipients.toArray(String[]::new));
            helper.setSubject(buildThreadSubject(ticket));
            helper.setText(buildTextBody(ticket, eventTitle), buildHtmlBody(ticket, eventTitle));
            applyThreadingHeaders(message, ticket, from);
            sender.send(message);
            log.info("Sent support ticket email for {} to {}", ticket.getTicketNumber(), validRecipients);
        } catch (MessagingException e) {
            log.error("Failed to send support ticket email for {}: {}", ticket.getTicketNumber(), e.getMessage());
        } catch (MailException e) {
            log.error("Failed to send support ticket email for {} via SMTP: {}", ticket.getTicketNumber(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected support ticket email failure for {}: {}", ticket.getTicketNumber(), e.getMessage(), e);
        }
    }

    private List<String> resolveAdminRecipients() {
        if (!StringUtils.hasText(adminRecipients)) {
            return List.of();
        }
        return Arrays.stream(adminRecipients.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
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

    private String buildThreadSubject(SupportTicket ticket) {
        return String.format("[%s] %s", ticket.getTicketNumber(), nullToEmpty(ticket.getSubject()));
    }

    private void applyThreadingHeaders(MimeMessage message, SupportTicket ticket, String fromAddress) throws MessagingException {
        String domain = resolveMessageIdDomain(fromAddress);
        String outboundMessageId = "<support-" + normalizeForMessageId(ticket.getTicketNumber())
                + "-" + UUID.randomUUID() + "@" + domain + ">";

        // Persist the very first email's Message-ID as the stable thread root
        String threadRootId = ticket.getEmailThreadMessageId();
        if (!StringUtils.hasText(threadRootId)) {
            threadRootId = outboundMessageId;
            ticket.setEmailThreadMessageId(threadRootId);
            supportTicketDao.save(ticket);
        }

        message.setHeader("Message-ID", outboundMessageId);
        message.setHeader("In-Reply-To", threadRootId);
        message.setHeader("References", threadRootId);
        message.setHeader("X-Support-Ticket-Number", ticket.getTicketNumber());
    }

    private String resolveMessageIdDomain(String fromAddress) {
        if (!StringUtils.hasText(fromAddress)) {
            return "subratapc.net";
        }

        int atIndex = fromAddress.indexOf('@');
        if (atIndex < 0 || atIndex >= fromAddress.length() - 1) {
            return "subratapc.net";
        }

        return fromAddress.substring(atIndex + 1).replaceAll("[^A-Za-z0-9.-]", "");
    }

    private String normalizeForMessageId(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private String buildReplyToAddress(SupportTicket ticket, String fallbackAddress) {
        if (!StringUtils.hasText(fallbackAddress) || !StringUtils.hasText(ticket.getTicketNumber())) {
            return fallbackAddress;
        }

        int atIndex = fallbackAddress.indexOf('@');
        if (atIndex <= 0) {
            return fallbackAddress;
        }

        String localPart = fallbackAddress.substring(0, atIndex);
        String domain = fallbackAddress.substring(atIndex);
        String normalizedTicketNumber = ticket.getTicketNumber().replaceAll("[^A-Za-z0-9]", "");
        return localPart + "+" + normalizedTicketNumber + domain;
    }

    private String buildTextBody(SupportTicket ticket, String eventTitle) {
        StringBuilder body = new StringBuilder();
        body.append(eventTitle).append("\n\n");
        body.append("Ticket: #").append(ticket.getTicketNumber()).append("\n");
        body.append("Subject: ").append(nullToEmpty(ticket.getSubject())).append("\n");
        body.append("Status: ").append(ticket.getStatus()).append("\n");
        body.append("Priority: ").append(ticket.getPriority()).append("\n");
        body.append("Customer: ").append(nullToEmpty(ticket.getUserEmail())).append("\n");
        String ticketLink = buildTicketLink(ticket);
        if (StringUtils.hasText(ticketLink)) {
            body.append("Open ticket: ").append(ticketLink).append("\n");
        }
        body.append("\nTranscript\n");
        body.append("----------\n");

        for (TicketMessage message : ticket.getMessages()) {
            body.append(formatDate(message)).append(" - ")
                    .append(message.isAdmin() ? "Support" : "Customer")
                    .append(" (").append(nullToEmpty(message.getSenderEmail())).append(")\n");
            body.append(nullToEmpty(message.getContent())).append("\n");
            appendAttachmentNames(body, message.getAttachmentUrls());
            body.append("\n");
        }

        return body.toString();
    }

    private String buildHtmlBody(SupportTicket ticket, String eventTitle) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:system-ui,Segoe UI,sans-serif;line-height:1.5;color:#0f172a;\">");
        html.append("<h2 style=\"margin:0 0 12px;\">").append(escapeHtml(eventTitle)).append("</h2>");
        html.append("<div style=\"padding:14px;border:1px solid #e2e8f0;border-radius:12px;background:#f8fafc;margin-bottom:18px;\">");
        html.append("<div><strong>Ticket:</strong> #").append(escapeHtml(ticket.getTicketNumber())).append("</div>");
        html.append("<div><strong>Subject:</strong> ").append(escapeHtml(ticket.getSubject())).append("</div>");
        html.append("<div><strong>Status:</strong> ").append(ticket.getStatus()).append("</div>");
        html.append("<div><strong>Priority:</strong> ").append(ticket.getPriority()).append("</div>");
        html.append("<div><strong>Customer:</strong> ").append(escapeHtml(ticket.getUserEmail())).append("</div>");
        String ticketLink = buildTicketLink(ticket);
        if (StringUtils.hasText(ticketLink)) {
            html.append("<div style=\"margin-top:8px;\"><a href=\"").append(escapeHtml(ticketLink)).append("\" style=\"color:#2563eb;\">Open ticket</a></div>");
        }
        html.append("</div>");
        html.append("<h3 style=\"margin:0 0 12px;\">Conversation transcript</h3>");

        for (TicketMessage message : ticket.getMessages()) {
            html.append("<div style=\"border:1px solid #e2e8f0;border-radius:12px;padding:12px;margin-bottom:10px;\">");
            html.append("<div style=\"font-size:12px;color:#64748b;margin-bottom:6px;\">")
                    .append(escapeHtml(formatDate(message)))
                    .append(" - ")
                    .append(message.isAdmin() ? "Support" : "Customer")
                    .append(" (").append(escapeHtml(message.getSenderEmail())).append(")")
                    .append("</div>");
            html.append("<div style=\"white-space:pre-wrap;\">").append(escapeHtml(message.getContent())).append("</div>");
            appendAttachmentNamesHtml(html, message.getAttachmentUrls());
            html.append("</div>");
        }

        html.append("<p style=\"font-size:12px;color:#64748b;\">Attachment links are available inside the authenticated support ticket page.</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private String buildTicketLink(SupportTicket ticket) {
        if (!StringUtils.hasText(frontendBaseUrl)) {
            return null;
        }
        String base = frontendBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/support";
    }

    private String formatDate(TicketMessage message) {
        if (message.getSentAt() == null) {
            return "";
        }
        return DATE_FORMATTER.format(message.getSentAt());
    }

    private void appendAttachmentNames(StringBuilder body, List<String> attachmentUrls) {
        if (attachmentUrls == null || attachmentUrls.isEmpty()) {
            return;
        }
        body.append("Attachments:\n");
        for (String url : attachmentUrls) {
            body.append("- ").append(extractAttachmentName(url)).append("\n");
        }
    }

    private void appendAttachmentNamesHtml(StringBuilder html, List<String> attachmentUrls) {
        if (attachmentUrls == null || attachmentUrls.isEmpty()) {
            return;
        }
        html.append("<div style=\"margin-top:8px;font-size:13px;color:#475569;\"><strong>Attachments:</strong><ul style=\"margin:4px 0 0;\">");
        for (String url : attachmentUrls) {
            html.append("<li>").append(escapeHtml(extractAttachmentName(url))).append("</li>");
        }
        html.append("</ul></div>");
    }

    private String extractAttachmentName(String url) {
        if (!StringUtils.hasText(url)) {
            return "Attachment";
        }
        int hashIndex = url.indexOf('#');
        if (hashIndex >= 0 && hashIndex < url.length() - 1) {
            return java.net.URLDecoder.decode(url.substring(hashIndex + 1), java.nio.charset.StandardCharsets.UTF_8);
        }
        return "Attachment";
    }

    private static void addIfPresent(Set<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
