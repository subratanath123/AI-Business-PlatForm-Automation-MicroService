package net.ai.chatbot.service.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.InstagramIntegration;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import net.ai.chatbot.service.aiprocess.NativeAiChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receives Messenger, Instagram, and WhatsApp messages that used to land on the n8n meta webhook,
 * answers them with the in-process assistant, and sends the reply through the Graph API.
 */
@Service
@Slf4j
public class MetaInboundMessageService {

    static final String FACEBOOK_GRAPH = "https://graph.facebook.com/v18.0";
    static final String INSTAGRAM_GRAPH = "https://graph.instagram.com/v21.0";
    static final int WHATSAPP_MAX_CHARS = 4096;
    static final int MESSENGER_MAX_CHARS = 2000;
    static final int INSTAGRAM_MAX_CHARS = 1000;
    private static final Duration DEDUPE_TTL = Duration.ofHours(24);
    private static final int LOCAL_DEDUPE_SIZE = 5000;
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern MARKDOWN_BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+");

    private final ChatBotService chatBotService;
    private final NativeAiChatService nativeAiChatService;
    private final GuardrailService guardrailService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final Executor executor;
    private final Map<String, Boolean> recentMessageIds = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > LOCAL_DEDUPE_SIZE;
                }
            });

    @Value("${meta.app-secret:}")
    private String appSecret;

    @Value("${meta.webhook-verify-token:}")
    private String verifyToken;

    @Autowired
    public MetaInboundMessageService(ChatBotService chatBotService,
                                     NativeAiChatService nativeAiChatService,
                                     GuardrailService guardrailService,
                                     ObjectMapper objectMapper,
                                     WebClient.Builder webClientBuilder,
                                     RedisTemplate<String, String> redisTemplate) {
        this(chatBotService, nativeAiChatService, guardrailService, objectMapper, webClientBuilder.build(),
                redisTemplate, Executors.newFixedThreadPool(4, runnable -> {
                    Thread thread = new Thread(runnable, "meta-inbound");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    MetaInboundMessageService(ChatBotService chatBotService,
                              NativeAiChatService nativeAiChatService,
                              GuardrailService guardrailService,
                              ObjectMapper objectMapper,
                              WebClient webClient,
                              RedisTemplate<String, String> redisTemplate,
                              Executor executor) {
        this.chatBotService = chatBotService;
        this.nativeAiChatService = nativeAiChatService;
        this.guardrailService = guardrailService;
        this.objectMapper = objectMapper;
        this.webClient = webClient;
        this.redisTemplate = redisTemplate;
        this.executor = executor;
    }

    public boolean isValidVerifyToken(String token) {
        return verifyToken != null && !verifyToken.isBlank() && verifyToken.equals(token);
    }

    public boolean hasValidSignature(byte[] body, String signatureHeader) {
        if (appSecret == null || appSecret.isBlank()) {
            log.error("Rejecting Meta webhook: meta.app-secret is not configured");
            return false;
        }
        if (body == null || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] provided = HexFormat.of().parseHex(signatureHeader.substring("sha256=".length()).trim());
            return MessageDigest.isEqual(expected, provided);
        } catch (IllegalArgumentException e) {
            return false;
        } catch (Exception e) {
            log.warn("Could not verify Meta webhook signature: {}", e.getMessage());
            return false;
        }
    }

    public void accept(byte[] body) {
        executor.execute(() -> {
            try {
                handle(objectMapper.readTree(body));
            } catch (Exception e) {
                log.error("Failed to handle Meta webhook: {}", e.getMessage(), e);
            }
        });
    }

    private void handle(JsonNode root) {
        String object = root.path("object").asText("");
        for (JsonNode entry : root.path("entry")) {
            try {
                switch (object) {
                    case "whatsapp_business_account" -> handleWhatsApp(entry);
                    case "instagram" -> handleInstagram(entry);
                    case "page" -> handleMessenger(entry);
                    default -> log.debug("Ignoring Meta webhook object '{}'", object);
                }
            } catch (Exception e) {
                log.error("Failed to handle Meta {} entry: {}", object, e.getMessage(), e);
            }
        }
    }

    private void handleWhatsApp(JsonNode entry) {
        for (JsonNode change : entry.path("changes")) {
            JsonNode value = change.path("value");
            String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
            if (phoneNumberId == null || !value.has("messages")) {
                continue;
            }
            ChatBot chatBot = chatBotService.getChabotFromPhoneNumberID(phoneNumberId);
            if (chatBot == null || chatBot.getWhatsAppIntegration() == null) {
                log.info("No chatbot linked to WhatsApp phone number id {}", phoneNumberId);
                continue;
            }
            if (Boolean.FALSE.equals(chatBot.getWhatsAppIntegration().enabled())) {
                log.info("WhatsApp integration for phone number id {} is disabled", phoneNumberId);
                continue;
            }
            for (JsonNode incoming : value.path("messages")) {
                String text = whatsAppText(incoming);
                String from = incoming.path("from").asText(null);
                if (from == null || text.isBlank()) {
                    log.info("Ignoring WhatsApp {} message without text", incoming.path("type").asText("unknown"));
                    continue;
                }
                if (!firstDelivery(incoming.path("id").asText(null))) {
                    continue;
                }
                String reply = answer(chatBot, from, text);
                for (String part : split(plainText(reply, true), WHATSAPP_MAX_CHARS)) {
                    boolean sent = post(FACEBOOK_GRAPH + "/" + phoneNumberId + "/messages",
                            chatBot.getWhatsAppIntegration().accessToken(), Map.of(
                                    "messaging_product", "whatsapp",
                                    "recipient_type", "individual",
                                    "to", from,
                                    "type", "text",
                                    "text", Map.of("body", part)
                            ));
                    if (!sent) {
                        break;
                    }
                }
            }
        }
    }

    private static String whatsAppText(JsonNode incoming) {
        return switch (incoming.path("type").asText("")) {
            case "text" -> incoming.path("text").path("body").asText("");
            case "button" -> incoming.path("button").path("text").asText("");
            case "interactive" -> {
                JsonNode interactive = incoming.path("interactive");
                JsonNode choice = interactive.has("button_reply")
                        ? interactive.path("button_reply")
                        : interactive.path("list_reply");
                yield choice.path("title").asText("");
            }
            default -> "";
        };
    }

    private void handleMessenger(JsonNode entry) {
        String pageId = entry.path("id").asText(null);
        if (pageId == null || !entry.has("messaging")) {
            return;
        }
        ChatBot chatBot = chatBotService.getChabotFromMessengerId(pageId);
        if (chatBot == null || chatBot.getMessengerIntegration() == null) {
            log.info("No chatbot linked to Facebook page {}", pageId);
            return;
        }
        if (Boolean.FALSE.equals(chatBot.getMessengerIntegration().enabled())) {
            log.info("Messenger integration for page {} is disabled", pageId);
            return;
        }
        for (JsonNode messaging : entry.path("messaging")) {
            Inbound inbound = messagingInbound(messaging, pageId);
            if (inbound == null || !firstDelivery(inbound.messageId())) {
                continue;
            }
            String reply = answer(chatBot, inbound.senderId(), inbound.text());
            for (String part : split(plainText(reply, false), MESSENGER_MAX_CHARS)) {
                boolean sent = post(FACEBOOK_GRAPH + "/" + pageId + "/messages",
                        chatBot.getMessengerIntegration().accessToken(), Map.of(
                                "recipient", Map.of("id", inbound.senderId()),
                                "messaging_type", "RESPONSE",
                                "message", Map.of("text", part)
                        ));
                if (!sent) {
                    break;
                }
            }
        }
    }

    private void handleInstagram(JsonNode entry) {
        String accountId = entry.path("id").asText(null);
        if (accountId == null || !entry.has("messaging")) {
            return;
        }
        ChatBot chatBot = chatBotService.getChatbotFromInstagramAccountId(accountId);
        if (chatBot == null || chatBot.getInstagramIntegration() == null) {
            log.info("No chatbot linked to Instagram account {}", accountId);
            return;
        }
        InstagramIntegration integration = chatBot.getInstagramIntegration();
        if (Boolean.FALSE.equals(integration.enabled())) {
            log.info("Instagram integration for account {} is disabled", accountId);
            return;
        }
        for (JsonNode messaging : entry.path("messaging")) {
            Inbound inbound = messagingInbound(messaging, accountId);
            if (inbound == null || !firstDelivery(inbound.messageId())) {
                continue;
            }
            String reply = answer(chatBot, inbound.senderId(), inbound.text());
            for (String part : split(plainText(reply, false), INSTAGRAM_MAX_CHARS)) {
                boolean sent = post(instagramSendUrl(integration, accountId), integration.accessToken(), Map.of(
                        "recipient", Map.of("id", inbound.senderId()),
                        "message", Map.of("text", part)
                ));
                if (!sent) {
                    break;
                }
            }
        }
    }

    /**
     * Facebook-login page tokens send through the connected page; Instagram-login tokens (IGAA...)
     * send through graph.instagram.com as the Instagram account.
     */
    static String instagramSendUrl(InstagramIntegration integration, String accountId) {
        String token = integration.accessToken();
        if (token != null && token.startsWith("IG")) {
            return INSTAGRAM_GRAPH + "/" + accountId + "/messages";
        }
        String pageId = integration.connectedPageId();
        return FACEBOOK_GRAPH + "/" + (pageId == null || pageId.isBlank() ? "me" : pageId) + "/messages";
    }

    /**
     * Text the user sent in a Messenger/Instagram messaging event, or null for echoes, receipts,
     * reactions, deleted messages and attachment-only messages.
     */
    private static Inbound messagingInbound(JsonNode messaging, String ownId) {
        String sender = messaging.path("sender").path("id").asText(null);
        if (sender == null || sender.equals(ownId)) {
            return null;
        }
        JsonNode message = messaging.path("message");
        if (!message.isMissingNode()) {
            if (message.path("is_echo").asBoolean(false)
                    || message.path("is_deleted").asBoolean(false)
                    || message.path("is_unsupported").asBoolean(false)) {
                return null;
            }
            String text = message.path("text").asText("");
            if (text.isBlank()) {
                log.info("Ignoring messaging event without text from {}", sender);
                return null;
            }
            return new Inbound(sender, text, message.path("mid").asText(null));
        }
        JsonNode postback = messaging.path("postback");
        if (!postback.isMissingNode()) {
            String text = postback.path("title").asText("");
            if (text.isBlank()) {
                text = postback.path("payload").asText("");
            }
            if (text.isBlank()) {
                return null;
            }
            String id = postback.path("mid").asText(null);
            if (id == null) {
                id = "postback-" + sender + "-" + messaging.path("timestamp").asText("");
            }
            return new Inbound(sender, text, id);
        }
        return null;
    }

    /**
     * Meta retries webhooks it thinks were not delivered; only the first copy of a message id is answered.
     */
    private boolean firstDelivery(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return true;
        }
        boolean fresh;
        try {
            Boolean claimed = redisTemplate.opsForValue().setIfAbsent("meta:inbound:" + messageId, "1", DEDUPE_TTL);
            fresh = claimed == null
                    ? recentMessageIds.putIfAbsent(messageId, Boolean.TRUE) == null
                    : claimed;
        } catch (RuntimeException e) {
            log.warn("Redis dedupe unavailable, using local cache: {}", e.getMessage());
            fresh = recentMessageIds.putIfAbsent(messageId, Boolean.TRUE) == null;
        }
        if (!fresh) {
            log.info("Skipping duplicate Meta message {}", messageId);
        }
        return fresh;
    }

    private String answer(ChatBot chatBot, String senderId, String text) {
        GuardrailService.ValidationResult validation = guardrailService.validateContent(text, "CONTENT", senderId);
        if (!validation.isAllowed()) {
            return "Your message was blocked due to content policy. Please rephrase and try again.";
        }
        Message message = Message.builder()
                .role("user")
                .message(text)
                .sessionId(senderId)
                .chatbotId(chatBot.getId())
                .build();
        N8NChatResponse<Object> response = nativeAiChatService.reply(chatBot, message, null, false, senderId, "webhook");
        if (!response.isSuccess() || response.getResult() == null) {
            return chatBot.getFallbackMessage() == null || chatBot.getFallbackMessage().isBlank()
                    ? "Sorry, I could not answer that just now."
                    : chatBot.getFallbackMessage();
        }
        return response.getResult().toString();
    }

    /**
     * Meta clients show Markdown literally; turn links into "label: url" and drop heading/bold markers.
     * WhatsApp has its own *bold* syntax, so bold is kept there.
     */
    static String plainText(String reply, boolean whatsApp) {
        if (reply == null) {
            return "";
        }
        String text = MARKDOWN_LINK.matcher(reply).replaceAll(match ->
                Matcher.quoteReplacement(match.group(1).equals(match.group(2))
                        ? match.group(2)
                        : match.group(1) + ": " + match.group(2)));
        text = MARKDOWN_HEADING.matcher(text).replaceAll("");
        return MARKDOWN_BOLD.matcher(text).replaceAll(whatsApp ? "*$1*" : "$1");
    }

    /**
     * Splits a reply into chunks no longer than {@code max}, preferring paragraph, line, then word breaks.
     */
    static List<String> split(String text, int max) {
        List<String> parts = new ArrayList<>();
        String rest = text == null ? "" : text.trim();
        while (rest.length() > max) {
            int cut = rest.lastIndexOf("\n\n", max);
            if (cut < max / 2) {
                cut = rest.lastIndexOf('\n', max);
            }
            if (cut < max / 2) {
                cut = rest.lastIndexOf(' ', max);
            }
            if (cut < max / 2) {
                cut = max;
                if (Character.isHighSurrogate(rest.charAt(cut - 1))) {
                    cut--;
                }
            }
            parts.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        if (!rest.isEmpty()) {
            parts.add(rest);
        }
        return parts;
    }

    private boolean post(String url, String accessToken, Map<String, Object> body) {
        String target = url.substring(url.indexOf("://") + 3);
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("Skipping Meta reply to {} because the access token is missing", target);
            return false;
        }
        try {
            String response = webClient.post()
                    .uri(url)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));
            log.info("Meta reply sent to {}: {}", target, response);
            return true;
        } catch (WebClientResponseException e) {
            log.error("Meta reply to {} failed with HTTP {}: {}", target, e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Meta reply to {} failed: {}", target, e.getMessage());
            return false;
        }
    }

    private record Inbound(String senderId, String text, String messageId) {
    }
}
