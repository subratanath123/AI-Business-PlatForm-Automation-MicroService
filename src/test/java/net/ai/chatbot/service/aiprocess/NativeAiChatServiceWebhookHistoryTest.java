package net.ai.chatbot.service.aiprocess;

import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.mcp.McpToolsResponse;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.service.workflow.McpExecutorService;
import net.ai.chatbot.service.workflow.WorkflowConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativeAiChatServiceWebhookHistoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void webhookReplyUsesSenderHistoryAndSavesN8nStyleRecord() {
        OpenAiApiClient openAi = mock(OpenAiApiClient.class);
        KnowledgebaseSearchService knowledge = mock(KnowledgebaseSearchService.class);
        WorkflowConfigService workflows = mock(WorkflowConfigService.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        NativeAiChatService service = new NativeAiChatService(openAi, knowledge, workflows,
                mock(McpExecutorService.class), mongo);

        when(openAi.isConfigured()).thenReturn(true);
        when(openAi.embed(anyList())).thenReturn(List.of(List.of(0.1, 0.2)));
        when(knowledge.search(eq("jade-ai-knowledgebase-bot-1"), eq("jade-ai-vector-index-bot-1"), anyList()))
                .thenReturn(List.of("We deliver across Dhaka within 2 days."));
        when(workflows.getTools("bot-1")).thenReturn(new McpToolsResponse());
        when(mongo.find(any(Query.class), eq(UserChatHistory.class))).thenReturn(List.of(
                UserChatHistory.builder().conversationid("PSID_1").chatbotId("bot-1")
                        .userMessage("Hi").aiMessage("Hello! How can I help?").createdAt(Instant.now()).build()));
        when(openAi.complete(anyString(), anyList(), anyList()))
                .thenReturn(new OpenAiApiClient.ChatCompletion("Yes, we deliver in 2 days.", List.of()));

        ChatBot bot = ChatBot.builder().id("bot-1").name("Store bot").model("gpt-4o").build();
        Message message = Message.builder().role("user").message("Do you deliver?").sessionId("PSID_1")
                .chatbotId("bot-1").build();

        N8NChatResponse<Object> response = service.reply(bot, message, null, false, "PSID_1", "webhook");

        assertTrue(response.isSuccess());
        assertEquals("Yes, we deliver in 2 days.", response.getResult());

        ArgumentCaptor<Query> historyQuery = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(historyQuery.capture(), eq(UserChatHistory.class));
        assertEquals("PSID_1", historyQuery.getValue().getQueryObject().get("conversationid"));
        assertEquals("bot-1", historyQuery.getValue().getQueryObject().get("chatbotId"));

        ArgumentCaptor<List<Map<String, Object>>> sentMessages = ArgumentCaptor.forClass(List.class);
        verify(openAi).complete(eq("gpt-4o"), sentMessages.capture(), anyList());
        List<Map<String, Object>> prompt = sentMessages.getValue();
        assertTrue(prompt.get(0).get("content").toString().contains("We deliver across Dhaka"));
        assertEquals("Hi", prompt.get(1).get("content"));
        assertEquals("Hello! How can I help?", prompt.get(2).get("content"));
        assertEquals("Do you deliver?", prompt.get(prompt.size() - 1).get("content"));

        ArgumentCaptor<UserChatHistory> saved = ArgumentCaptor.forClass(UserChatHistory.class);
        verify(mongo).save(saved.capture());
        UserChatHistory history = saved.getValue();
        assertEquals("PSID_1", history.getConversationid());
        assertEquals("PSID_1", history.getEmail());
        assertEquals("bot-1", history.getChatbotId());
        assertEquals("webhook", history.getMode());
        assertFalse(history.isAnonymous());
        assertEquals("Do you deliver?", history.getUserMessage());
        assertEquals("Yes, we deliver in 2 days.", history.getAiMessage());
    }
}
