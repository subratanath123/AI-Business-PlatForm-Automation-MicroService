package net.ai.chatbot.service.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ai.chatbot.controller.social.MetaInboundWebhookController;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.InstagramIntegration;
import net.ai.chatbot.entity.MessengerIntegration;
import net.ai.chatbot.entity.WhatsAppIntegration;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import net.ai.chatbot.service.aiprocess.NativeAiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetaInboundWebhookTest {

    private static final String APP_SECRET = "test-app-secret";
    private static final String VERIFY_TOKEN = "test-verify-token";
    private static final String BOT_ID = "bot-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<CapturedRequest> graphRequests = new ArrayList<>();
    private final Set<String> redisKeys = new HashSet<>();
    private HttpStatus graphStatus = HttpStatus.OK;
    private String graphResponse = "{\"message_id\":\"m-out\"}";

    private ChatBotService chatBotService;
    private NativeAiChatService nativeAiChatService;
    private MockMvc mockMvc;

    record CapturedRequest(String method, String url, String authorization, JsonNode body) {
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatBotService = mock(ChatBotService.class);
        nativeAiChatService = mock(NativeAiChatService.class);
        GuardrailService guardrailService = mock(GuardrailService.class);
        when(guardrailService.validateContent(anyString(), anyString(), anyString()))
                .thenReturn(GuardrailService.ValidationResult.success());
        when(nativeAiChatService.reply(any(), any(), isNull(), anyBoolean(), anyString(), anyString()))
                .thenReturn(ok("Our store opens at 9am."));

        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> redisKeys.add(inv.getArgument(0)));

        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            MockClientHttpRequest written = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(written, ExchangeStrategies.withDefaults())
                    .then(Mono.defer(() -> written.getBodyAsString()))
                    .map(body -> {
                        try {
                            graphRequests.add(new CapturedRequest(request.method().name(), request.url().toString(),
                                    request.headers().getFirst(HttpHeaders.AUTHORIZATION), objectMapper.readTree(body)));
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        return ClientResponse.create(graphStatus)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body(graphResponse)
                                .build();
                    });
        }).build();

        MetaInboundMessageService service = new MetaInboundMessageService(chatBotService, nativeAiChatService,
                guardrailService, objectMapper, webClient, redisTemplate, Runnable::run);
        ReflectionTestUtils.setField(service, "appSecret", APP_SECRET);
        ReflectionTestUtils.setField(service, "verifyToken", VERIFY_TOKEN);
        mockMvc = MockMvcBuilders.standaloneSetup(new MetaInboundWebhookController(service)).build();
    }

    // ---------- verification ----------

    @Test
    void getVerificationEchoesChallengeForConfiguredToken() throws Exception {
        mockMvc.perform(get("/v1/api/meta/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                .andExpect(content().string("1158201444"));
    }

    @Test
    void getVerificationRejectsWrongToken() throws Exception {
        mockMvc.perform(get("/v1/api/meta/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "nope")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden());
    }

    // ---------- WhatsApp ----------

    @Test
    void whatsAppTextIsAnsweredAndSentToPhoneNumberId() throws Exception {
        stubWhatsApp(true);

        postSigned(whatsAppText("wamid.A1", "8801711111111", "When do you open?")).andExpect(status().isOk());

        assertAiCalled("When do you open?", "8801711111111");
        assertEquals(1, graphRequests.size());
        CapturedRequest sent = graphRequests.get(0);
        assertEquals("POST", sent.method());
        assertEquals("https://graph.facebook.com/v18.0/PHONE_ID_1/messages", sent.url());
        assertEquals("Bearer wa-token", sent.authorization());
        assertEquals("whatsapp", sent.body().path("messaging_product").asText());
        assertEquals("8801711111111", sent.body().path("to").asText());
        assertEquals("text", sent.body().path("type").asText());
        assertEquals("Our store opens at 9am.", sent.body().path("text").path("body").asText());
    }

    @Test
    void whatsAppStatusUpdateProducesNoReply() throws Exception {
        stubWhatsApp(true);
        String statuses = """
                {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
                  "messaging_product":"whatsapp","metadata":{"display_phone_number":"15550001111","phone_number_id":"PHONE_ID_1"},
                  "statuses":[{"id":"wamid.OUT","status":"delivered","timestamp":"1730870000","recipient_id":"8801711111111"}]}}]}]}
                """;

        postSigned(statuses).andExpect(status().isOk());

        assertNoReply();
    }

    @Test
    void whatsAppImageWithoutTextIsIgnored() throws Exception {
        stubWhatsApp(true);
        String image = """
                {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
                  "messaging_product":"whatsapp","metadata":{"phone_number_id":"PHONE_ID_1"},
                  "messages":[{"from":"8801711111111","id":"wamid.IMG","timestamp":"1730870000","type":"image",
                               "image":{"id":"media-1","mime_type":"image/jpeg"}}]}}]}]}
                """;

        postSigned(image).andExpect(status().isOk());

        assertNoReply();
    }

    @Test
    void whatsAppButtonReplyUsesButtonTitle() throws Exception {
        stubWhatsApp(true);
        String interactive = """
                {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
                  "messaging_product":"whatsapp","metadata":{"phone_number_id":"PHONE_ID_1"},
                  "messages":[{"from":"8801711111111","id":"wamid.BTN","type":"interactive",
                               "interactive":{"type":"button_reply","button_reply":{"id":"b1","title":"Opening hours"}}}]}}]}]}
                """;

        postSigned(interactive).andExpect(status().isOk());

        assertAiCalled("Opening hours", "8801711111111");
    }

    @Test
    void duplicateWhatsAppMessageIdIsAnsweredOnce() throws Exception {
        stubWhatsApp(true);
        String payload = whatsAppText("wamid.DUP", "8801711111111", "Hi");

        postSigned(payload).andExpect(status().isOk());
        postSigned(payload).andExpect(status().isOk());

        verify(nativeAiChatService, times(1)).reply(any(), any(), isNull(), anyBoolean(), anyString(), anyString());
        assertEquals(1, graphRequests.size());
    }

    @Test
    void disabledWhatsAppIntegrationDoesNotReply() throws Exception {
        stubWhatsApp(false);

        postSigned(whatsAppText("wamid.OFF", "8801711111111", "Hi")).andExpect(status().isOk());

        assertNoReply();
    }

    @Test
    void unknownPhoneNumberIdDoesNotReplyOrFail() throws Exception {
        postSigned(whatsAppText("wamid.UNK", "8801711111111", "Hi")).andExpect(status().isOk());

        verify(chatBotService).getChabotFromPhoneNumberID("PHONE_ID_1");
        assertNoReply();
    }

    @Test
    void longWhatsAppReplyIsSplitAtLimit() throws Exception {
        stubWhatsApp(true);
        when(nativeAiChatService.reply(any(), any(), isNull(), anyBoolean(), anyString(), anyString()))
                .thenReturn(ok("word ".repeat(1500)));

        postSigned(whatsAppText("wamid.LONG", "8801711111111", "Tell me everything")).andExpect(status().isOk());

        assertEquals(2, graphRequests.size());
        for (CapturedRequest sent : graphRequests) {
            assertTrue(sent.body().path("text").path("body").asText().length() <= MetaInboundMessageService.WHATSAPP_MAX_CHARS);
        }
    }

    // ---------- Messenger ----------

    @Test
    void messengerTextIsAnsweredThroughPage() throws Exception {
        stubMessenger();

        postSigned(messengerEvent("""
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"timestamp":1730870000000,
                 "message":{"mid":"m_A1","text":"Do you deliver?"}}
                """)).andExpect(status().isOk());

        assertAiCalled("Do you deliver?", "PSID_1");
        assertEquals(1, graphRequests.size());
        CapturedRequest sent = graphRequests.get(0);
        assertEquals("https://graph.facebook.com/v18.0/PAGE_1/messages", sent.url());
        assertEquals("Bearer page-token", sent.authorization());
        assertEquals("PSID_1", sent.body().path("recipient").path("id").asText());
        assertEquals("RESPONSE", sent.body().path("messaging_type").asText());
        assertEquals("Our store opens at 9am.", sent.body().path("message").path("text").asText());
    }

    @Test
    void messengerPostbackUsesTitle() throws Exception {
        stubMessenger();

        postSigned(messengerEvent("""
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"timestamp":1730870000000,
                 "postback":{"mid":"m_PB","title":"Get Started","payload":"GET_STARTED"}}
                """)).andExpect(status().isOk());

        assertAiCalled("Get Started", "PSID_1");
    }

    @Test
    void messengerEchoReadAndDeliveryEventsAreIgnored() throws Exception {
        stubMessenger();

        postSigned(messengerEvent("""
                {"sender":{"id":"PAGE_1"},"recipient":{"id":"PSID_1"},"timestamp":1,
                 "message":{"mid":"m_ECHO","is_echo":true,"app_id":123,"text":"Our store opens at 9am."}}
                """, """
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"timestamp":2,"read":{"watermark":1730870000000}}
                """, """
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"timestamp":3,"delivery":{"mids":["m_X"],"watermark":1}}
                """, """
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"timestamp":4,
                 "message":{"mid":"m_STICKER","sticker_id":369239263222822,
                            "attachments":[{"type":"image","payload":{"url":"https://example.com/s.png"}}]}}
                """)).andExpect(status().isOk());

        assertNoReply();
    }

    @Test
    void longMessengerReplyIsSplitAtLimit() throws Exception {
        stubMessenger();
        when(nativeAiChatService.reply(any(), any(), isNull(), anyBoolean(), anyString(), anyString()))
                .thenReturn(ok("x".repeat(4500)));

        postSigned(messengerEvent("""
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"message":{"mid":"m_LONG","text":"long please"}}
                """)).andExpect(status().isOk());

        assertEquals(3, graphRequests.size());
        for (CapturedRequest sent : graphRequests) {
            assertTrue(sent.body().path("message").path("text").asText().length() <= MetaInboundMessageService.MESSENGER_MAX_CHARS);
        }
    }

    @Test
    void graphErrorStopsRemainingPartsWithoutFailingWebhook() throws Exception {
        stubMessenger();
        when(nativeAiChatService.reply(any(), any(), isNull(), anyBoolean(), anyString(), anyString()))
                .thenReturn(ok("x".repeat(4500)));
        graphStatus = HttpStatus.BAD_REQUEST;
        graphResponse = "{\"error\":{\"message\":\"No matching user found\",\"code\":100,\"error_subcode\":2018001}}";

        postSigned(messengerEvent("""
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"message":{"mid":"m_ERR","text":"hi"}}
                """)).andExpect(status().isOk());

        assertEquals(1, graphRequests.size());
    }

    @Test
    void unknownPageDoesNotReply() throws Exception {
        postSigned(messengerEvent("""
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"message":{"mid":"m_UNK","text":"hi"}}
                """)).andExpect(status().isOk());

        assertNoReply();
    }

    // ---------- Instagram ----------

    @Test
    void instagramDmWithPageTokenIsSentThroughConnectedPage() throws Exception {
        stubInstagram("EAAB-page-token");

        postSigned(instagramEvent("""
                {"sender":{"id":"IGSID_1"},"recipient":{"id":"IG_1"},"timestamp":1730870000000,
                 "message":{"mid":"ig_A1","text":"What sizes do you have?"}}
                """)).andExpect(status().isOk());

        assertAiCalled("What sizes do you have?", "IGSID_1");
        assertEquals(1, graphRequests.size());
        CapturedRequest sent = graphRequests.get(0);
        assertEquals("https://graph.facebook.com/v18.0/PAGE_IG/messages", sent.url());
        assertEquals("Bearer EAAB-page-token", sent.authorization());
        assertEquals("IGSID_1", sent.body().path("recipient").path("id").asText());
        assertEquals("Our store opens at 9am.", sent.body().path("message").path("text").asText());
    }

    @Test
    void instagramLoginTokenUsesInstagramGraphHost() throws Exception {
        stubInstagram("IGAA-ig-token");

        postSigned(instagramEvent("""
                {"sender":{"id":"IGSID_1"},"recipient":{"id":"IG_1"},"message":{"mid":"ig_B1","text":"hello"}}
                """)).andExpect(status().isOk());

        assertEquals("https://graph.instagram.com/v21.0/IG_1/messages", graphRequests.get(0).url());
    }

    @Test
    void instagramReplyIsSplitAt1000Chars() throws Exception {
        stubInstagram("EAAB-page-token");
        when(nativeAiChatService.reply(any(), any(), isNull(), anyBoolean(), anyString(), anyString()))
                .thenReturn(ok("Sentence number one is here.\n".repeat(60)));

        postSigned(instagramEvent("""
                {"sender":{"id":"IGSID_1"},"recipient":{"id":"IG_1"},"message":{"mid":"ig_LONG","text":"details"}}
                """)).andExpect(status().isOk());

        assertEquals(2, graphRequests.size());
        for (CapturedRequest sent : graphRequests) {
            assertTrue(sent.body().path("message").path("text").asText().length() <= MetaInboundMessageService.INSTAGRAM_MAX_CHARS);
        }
    }

    @Test
    void instagramStoryMentionReactionAndEchoAreIgnored() throws Exception {
        stubInstagram("EAAB-page-token");

        postSigned(instagramEvent("""
                {"sender":{"id":"IGSID_1"},"recipient":{"id":"IG_1"},
                 "message":{"mid":"ig_STORY","attachments":[{"type":"story_mention","payload":{"url":"https://example.com/story"}}]}}
                """, """
                {"sender":{"id":"IGSID_1"},"recipient":{"id":"IG_1"},
                 "reaction":{"mid":"ig_A1","action":"react","reaction":"love","emoji":"\\u2764"}}
                """, """
                {"sender":{"id":"IG_1"},"recipient":{"id":"IGSID_1"},
                 "message":{"mid":"ig_ECHO","is_echo":true,"text":"Our store opens at 9am."}}
                """, """
                {"sender":{"id":"IGSID_1"},"recipient":{"id":"IG_1"},"message":{"mid":"ig_DEL","is_deleted":true}}
                """)).andExpect(status().isOk());

        assertNoReply();
    }

    @Test
    void duplicateInstagramMidIsAnsweredOnce() throws Exception {
        stubInstagram("EAAB-page-token");
        String payload = instagramEvent("""
                {"sender":{"id":"IGSID_1"},"recipient":{"id":"IG_1"},"message":{"mid":"ig_DUP","text":"hi"}}
                """);

        postSigned(payload).andExpect(status().isOk());
        postSigned(payload).andExpect(status().isOk());

        assertEquals(1, graphRequests.size());
    }

    // ---------- signature ----------

    @Test
    void badSignatureIsRejectedWithoutProcessing() throws Exception {
        stubMessenger();
        String body = messengerEvent("""
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"message":{"mid":"m_SIG","text":"hi"}}
                """);

        mockMvc.perform(post("/v1/api/meta/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/api/meta/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        assertNoReply();
    }

    @Test
    void signatureIsCheckedAgainstRawUtf8Bytes() throws Exception {
        stubMessenger();
        byte[] raw = messengerEvent("""
                {"sender":{"id":"PSID_1"},"recipient":{"id":"PAGE_1"},"message":{"mid":"m_UTF","text":"দাম কত? 😊"}}
                """).getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/v1/api/meta/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(raw))
                        .content(raw))
                .andExpect(status().isOk());

        assertAiCalled("দাম কত? 😊", "PSID_1");
    }

    @Test
    void missingAppSecretRejectsEverything() {
        MetaInboundMessageService unconfigured = new MetaInboundMessageService(chatBotService, nativeAiChatService,
                mock(GuardrailService.class), objectMapper, WebClient.create(), mock(RedisTemplate.class), Runnable::run);
        assertFalse(unconfigured.hasValidSignature("{}".getBytes(StandardCharsets.UTF_8), "sha256=abc"));
    }

    @Test
    void splitPrefersWordBoundaries() {
        List<String> parts = MetaInboundMessageService.split("alpha beta gamma delta", 11);
        assertEquals(List.of("alpha beta", "gamma delta"), parts);
    }

    @Test
    void markdownIsFlattenedForMetaClients() {
        String reply = "### Tables\nSee **our range** at [this link](https://auzie.com.au/category/dining-tables-12-22).";
        assertEquals("Tables\nSee our range at this link: https://auzie.com.au/category/dining-tables-12-22.",
                MetaInboundMessageService.plainText(reply, false));
        assertEquals("Tables\nSee *our range* at this link: https://auzie.com.au/category/dining-tables-12-22.",
                MetaInboundMessageService.plainText(reply, true));
        assertEquals("https://auzie.com.au", MetaInboundMessageService.plainText("[https://auzie.com.au](https://auzie.com.au)", false));
    }

    // ---------- helpers ----------

    private void stubWhatsApp(boolean enabled) {
        ChatBot bot = bot();
        bot.setWhatsAppIntegration(WhatsAppIntegration.builder()
                .chatbotId(BOT_ID).phoneNumberId("PHONE_ID_1").accessToken("wa-token").enabled(enabled).build());
        when(chatBotService.getChabotFromPhoneNumberID("PHONE_ID_1")).thenReturn(bot);
    }

    private void stubMessenger() {
        ChatBot bot = bot();
        bot.setMessengerIntegration(MessengerIntegration.builder()
                .chatbotId(BOT_ID).pageId("PAGE_1").accessToken("page-token").enabled(true).build());
        when(chatBotService.getChabotFromMessengerId("PAGE_1")).thenReturn(bot);
    }

    private void stubInstagram(String token) {
        ChatBot bot = bot();
        bot.setInstagramIntegration(InstagramIntegration.builder()
                .chatbotId(BOT_ID).instagramAccountId("IG_1").connectedPageId("PAGE_IG").accessToken(token).enabled(true).build());
        when(chatBotService.getChatbotFromInstagramAccountId("IG_1")).thenReturn(bot);
    }

    private static ChatBot bot() {
        return ChatBot.builder().id(BOT_ID).name("Store bot").fallbackMessage("fallback").build();
    }

    private void assertAiCalled(String text, String senderId) {
        ArgumentCaptor<ChatBot> botCaptor = ArgumentCaptor.forClass(ChatBot.class);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(nativeAiChatService).reply(botCaptor.capture(), messageCaptor.capture(), isNull(), eq(false),
                eq(senderId), eq("webhook"));
        assertEquals(BOT_ID, botCaptor.getValue().getId());
        assertEquals(BOT_ID, messageCaptor.getValue().getChatbotId());
        assertEquals(text, messageCaptor.getValue().getMessage());
        assertEquals(senderId, messageCaptor.getValue().getSessionId());
    }

    private void assertNoReply() {
        verify(nativeAiChatService, never()).reply(any(), any(), any(), anyBoolean(), any(), any());
        assertTrue(graphRequests.isEmpty());
    }

    private org.springframework.test.web.servlet.ResultActions postSigned(String body) throws Exception {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(post("/v1/api/meta/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Hub-Signature-256", sign(raw))
                .content(raw));
    }

    private static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder hex = new StringBuilder("sha256=");
        for (byte b : mac.doFinal(body)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String whatsAppText(String id, String from, String text) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
                  "messaging_product":"whatsapp","metadata":{"display_phone_number":"15550001111","phone_number_id":"PHONE_ID_1"},
                  "contacts":[{"profile":{"name":"Test"},"wa_id":"%s"}],
                  "messages":[{"from":"%s","id":"%s","timestamp":"1730870000","type":"text","text":{"body":"%s"}}]}}]}]}
                """.formatted(from, from, id, text);
    }

    private static String messengerEvent(String... messaging) {
        return "{\"object\":\"page\",\"entry\":[{\"id\":\"PAGE_1\",\"time\":1730870000000,\"messaging\":["
                + String.join(",", messaging) + "]}]}";
    }

    private static String instagramEvent(String... messaging) {
        return "{\"object\":\"instagram\",\"entry\":[{\"id\":\"IG_1\",\"time\":1730870000000,\"messaging\":["
                + String.join(",", messaging) + "]}]}";
    }

    private static N8NChatResponse<Object> ok(String reply) {
        return N8NChatResponse.<Object>builder().success(true).result(reply).build();
    }
}
