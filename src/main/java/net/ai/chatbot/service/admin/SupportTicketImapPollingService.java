package net.ai.chatbot.service.admin;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.SupportTicketDao;
import net.ai.chatbot.dto.admin.SupportTicket;
import net.ai.chatbot.dto.admin.TicketStatus;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketImapPollingService {

    private static final Pattern TICKET_PATTERN = Pattern.compile("TKT[-_ ]?(\\d{6})", Pattern.CASE_INSENSITIVE);

    private final SupportTicketService supportTicketService;
    private final SupportTicketDao supportTicketDao;

    @Value("${support.email.imap.enabled:false}")
    private boolean enabled;

    @Value("${support.email.imap.host:imap.gmail.com}")
    private String host;

    @Value("${support.email.imap.port:993}")
    private int port;

    @Value("${support.email.imap.username:}")
    private String username;

    @Value("${support.email.imap.password:}")
    private String password;

    @Value("${support.email.imap.folder:INBOX}")
    private String folderName;

    @Value("${support.email.admin-recipients:}")
    private String adminRecipients;

    @Scheduled(fixedDelayString = "${support.email.imap.poll-ms:60000}", initialDelay = 30000)
    public void pollReplies() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("Support IMAP polling skipped: username/password is not configured.");
            return;
        }

        Store store = null;
        Folder folder = null;
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.host", host);
            props.put("mail.imaps.port", String.valueOf(port));
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.connectiontimeout", "10000");
            props.put("mail.imaps.timeout", "10000");

            Session session = Session.getInstance(props);
            store = session.getStore("imaps");
            store.connect(host, port, username, password);

            folder = store.getFolder(folderName);
            folder.open(Folder.READ_WRITE);

            Message[] unreadMessages = folder.search(new jakarta.mail.search.FlagTerm(new Flags(Flags.Flag.SEEN), false));
            for (Message message : unreadMessages) {
                processMessage(message);
            }
        } catch (Exception e) {
            log.error("Support IMAP polling failed: {}", e.getMessage(), e);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private void processMessage(Message message) {
        try {
            String subject = message.getSubject();
            String ticketNumber = extractTicketNumber(subject);
            if (!StringUtils.hasText(ticketNumber)) {
                log.debug("Skipping unread email without ticket number in subject: {}", subject);
                return;
            }

            Optional<SupportTicket> ticketOptional = supportTicketDao.findByTicketNumber(ticketNumber);
            if (ticketOptional.isEmpty()) {
                log.debug("Skipping email reply; ticket not found: {}", ticketNumber);
                return;
            }

            TicketStatus status = ticketOptional.get().getStatus();
            if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
                message.setFlag(Flags.Flag.SEEN, true);
                log.debug("Skipping IMAP import for {} — ticket is {}", ticketNumber, status);
                return;
            }

            InternetAddress senderAddress = resolveSender(message);
            String senderEmail = senderAddress != null ? senderAddress.getAddress() : "";
            String senderName = senderAddress != null && StringUtils.hasText(senderAddress.getPersonal())
                    ? senderAddress.getPersonal()
                    : senderEmail;

            String body = cleanReplyBody(extractText(message));
            if (!StringUtils.hasText(body)) {
                log.debug("Skipping email reply for {} because body is empty.", ticketNumber);
                message.setFlag(Flags.Flag.SEEN, true);
                return;
            }

            boolean isAdmin = isAdminSender(senderEmail);
            Optional<?> saved = supportTicketService.addEmailReply(ticketNumber, senderEmail, senderName, body, isAdmin);
            if (saved.isPresent()) {
                message.setFlag(Flags.Flag.SEEN, true);
                log.info("Imported support email reply into ticket {} from {}", ticketNumber, senderEmail);
            }
        } catch (Exception e) {
            log.error("Failed to process support email reply: {}", e.getMessage(), e);
        }
    }

    private String extractTicketNumber(String subject) {
        if (!StringUtils.hasText(subject)) {
            return null;
        }
        Matcher matcher = TICKET_PATTERN.matcher(subject);
        if (!matcher.find()) {
            return null;
        }
        return "TKT-" + matcher.group(1);
    }

    private InternetAddress resolveSender(Message message) throws Exception {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0 || !(from[0] instanceof InternetAddress internetAddress)) {
            return null;
        }
        return internetAddress;
    }

    private boolean isAdminSender(String senderEmail) {
        if (!StringUtils.hasText(senderEmail) || !StringUtils.hasText(adminRecipients)) {
            return false;
        }
        String normalizedSender = senderEmail.trim().toLowerCase();
        return Arrays.stream(adminRecipients.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(normalizedSender::equals);
    }

    private String extractText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content instanceof String text ? text : "";
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return content instanceof String html ? Jsoup.parse(html).text() : "";
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            String htmlFallback = "";
            for (int i = 0; i < multipart.getCount(); i++) {
                Part bodyPart = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    continue;
                }
                String text = extractText(bodyPart);
                if (StringUtils.hasText(text) && bodyPart.isMimeType("text/plain")) {
                    return text;
                }
                if (StringUtils.hasText(text)) {
                    htmlFallback = text;
                }
            }
            return htmlFallback;
        }
        Object content = part.getContent();
        if (content instanceof InputStream inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        return "";
    }

    private String cleanReplyBody(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return "";
        }

        String body = rawBody.replace("\r\n", "\n").trim();
        List<String> cutMarkers = List.of(
                "\nOn ",
                "\nConversation transcript",
                "\nTicket:",
                "\n----------",
                "\nFrom:",
                "\nSent:"
        );

        int cutIndex = body.length();
        for (String marker : cutMarkers) {
            int index = body.indexOf(marker);
            if (index > 0 && index < cutIndex) {
                cutIndex = index;
            }
        }

        return body.substring(0, cutIndex).trim();
    }

    private void closeQuietly(Folder folder) {
        if (folder == null || !folder.isOpen()) {
            return;
        }
        try {
            folder.close(false);
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(Store store) {
        if (store == null || !store.isConnected()) {
            return;
        }
        try {
            store.close();
        } catch (Exception ignored) {
        }
    }
}
