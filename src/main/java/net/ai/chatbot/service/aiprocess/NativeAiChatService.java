package net.ai.chatbot.service.aiprocess;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.mcp.McpExecuteRequest;
import net.ai.chatbot.dto.mcp.McpExecuteResponse;
import net.ai.chatbot.dto.mcp.McpToolsResponse;
import net.ai.chatbot.dto.n8n.FileAttachment;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.WorkflowConfig.ActionEndpoint;
import net.ai.chatbot.service.workflow.McpExecutorService;
import net.ai.chatbot.service.workflow.WorkflowConfigService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.ai.chatbot.service.workflow.WorkflowConfigService.WORKFLOW_PLACEHOLDER_TOOL_NAME;

/**
 * In-process replacement for the n8n knowledge-base chat and multimodal chat webhooks.
 * Retrieves chatbot knowledge, calls the model, runs configured workflow tools, and stores history.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NativeAiChatService {

    private static final int HISTORY_TURNS = 12;
    private static final int MAX_TOOL_ROUNDS = 4;
    private static final int MAX_CONTEXT_CHARS = 12_000;
    private static final Pattern MODEL_NAME = Pattern.compile("^(gpt-|o[0-9])[A-Za-z0-9._-]{0,48}$");

    private final OpenAiApiClient openAiApiClient;
    private final KnowledgebaseSearchService knowledgebaseSearchService;
    private final WorkflowConfigService workflowConfigService;
    private final McpExecutorService mcpExecutorService;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public N8NChatResponse<Object> reply(ChatBot chatBot, Message message, String userToken, boolean anonymous) {
        return reply(chatBot, message, userToken, anonymous, null, "chat");
    }

    /**
     * Same as {@link #reply(ChatBot, Message, String, boolean)} but lets the caller set the history owner
     * and mode, e.g. Meta channels store the sender id as email with mode "webhook".
     */
    public N8NChatResponse<Object> reply(ChatBot chatBot,
                                         Message message,
                                         String userToken,
                                         boolean anonymous,
                                         String historyEmail,
                                         String historyMode) {
        if (message == null || message.getMessage() == null || message.getMessage().isBlank()) {
            return N8NChatResponse.error("INVALID_MESSAGE", "Message content is required");
        }
        if (chatBot == null || chatBot.getId() == null) {
            return N8NChatResponse.error("CHATBOT_NOT_FOUND", "Chatbot not found");
        }
        if (!openAiApiClient.isConfigured()) {
            return N8NChatResponse.error("OPENAI_NOT_CONFIGURED", "OpenAI API key is not configured");
        }

        String sessionId = message.getSessionId() == null || message.getSessionId().isBlank()
                ? UUID.randomUUID().toString()
                : message.getSessionId();
        String userText = message.getMessage().trim();
        String model = resolveModel(message.getModel(), chatBot.getModel());

        try {
            List<String> knowledge = retrieveKnowledge(chatBot, userText);
            boolean selfContainedTask = userText.length() > 500;

            List<Map<String, Object>> tools = loadTools(chatBot.getId());
            List<Map<String, Object>> messages = buildMessages(
                    chatBot, sessionId, userText, knowledge, message.getFileAttachments(), selfContainedTask);

            log.info("Answering chatbot {} session {} with model {}", chatBot.getId(), sessionId, model);
            String reply = runWithTools(chatBot, sessionId, userText, userToken, model, messages, tools);
            if (reply == null || reply.isBlank() || isGreeting(chatBot, reply)) {
                reply = fallback(chatBot);
            }
            saveHistory(chatBot, sessionId, userText, reply, anonymous, historyEmail, historyMode);
            return success(reply);
        } catch (Exception e) {
            log.error("Native AI reply failed for chatbot {}: {}", chatBot.getId(), e.getMessage(), e);
            return N8NChatResponse.error("AI_ERROR", "The assistant could not complete this request. Please try again.");
        }
    }

    private String runWithTools(ChatBot chatBot,
                                String sessionId,
                                String userText,
                                String userToken,
                                String model,
                                List<Map<String, Object>> messages,
                                List<Map<String, Object>> tools) {
        String latest = "";
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            OpenAiApiClient.ChatCompletion completion;
            try {
                completion = openAiApiClient.complete(model, messages, tools);
            } catch (RuntimeException first) {
                if (round == 0 && containsImage(messages)) {
                    log.info("Vision request failed, retrying as text: {}", first.getMessage());
                    stripImages(messages, userText);
                    completion = openAiApiClient.complete(model, messages, tools);
                } else {
                    throw first;
                }
            }
            latest = completion.content() == null ? "" : completion.content().trim();
            if (completion.toolCalls() == null || completion.toolCalls().isEmpty()) {
                return latest;
            }

            messages.add(assistantToolMessage(completion));
            for (OpenAiApiClient.ToolCall toolCall : completion.toolCalls()) {
                String toolResult = executeTool(chatBot.getId(), toolCall, sessionId, userToken, userText);
                Map<String, Object> toolMessage = new LinkedHashMap<>();
                toolMessage.put("role", "tool");
                toolMessage.put("tool_call_id", toolCall.id());
                toolMessage.put("content", toolResult);
                messages.add(toolMessage);
            }
        }
        return latest.isBlank()
                ? "I started that action but could not finish it. Please try again with a bit more detail."
                : latest;
    }

    private String executeTool(String chatbotId,
                               OpenAiApiClient.ToolCall toolCall,
                               String sessionId,
                               String userToken,
                               String userText) {
        if (WORKFLOW_PLACEHOLDER_TOOL_NAME.equals(toolCall.name())) {
            return "No workflow actions are configured for this chatbot.";
        }
        try {
            ActionEndpoint action = workflowConfigService.findActionByToolName(chatbotId, toolCall.name());
            Map<String, Object> arguments = parseArguments(toolCall.argumentsJson());
            String userId = AuthUtils.getUserId() != null ? AuthUtils.getUserId() : sessionId;
            McpExecuteResponse response = mcpExecutorService.execute(chatbotId, McpExecuteRequest.builder()
                    .actionId(action.getId())
                    .collectedParams(arguments)
                    .sessionId(sessionId)
                    .userId(userId)
                    .userToken(userToken)
                    .message(userText)
                    .build());
            String message = response.getMessage() == null ? "" : response.getMessage();
            if (response.getResponseBody() != null) {
                message = message + "\n" + objectMapper.writeValueAsString(response.getResponseBody());
            }
            if (!response.isSuccess() && response.getError() != null) {
                message = message + "\n" + response.getError();
            }
            return truncate(message.isBlank() ? "The action completed." : message, 8000);
        } catch (Exception e) {
            log.warn("Workflow tool '{}' failed: {}", toolCall.name(), e.getMessage());
            return "The action could not be completed: " + e.getMessage();
        }
    }

    private List<String> retrieveKnowledge(ChatBot chatBot, String userText) {
        try {
            List<Double> query = openAiApiClient.embed(List.of(userText)).get(0);
            return knowledgebaseSearchService.search(
                    chatBot.getChatbotknowledgebasecollection(),
                    chatBot.getVectorIndexName(),
                    query
            );
        } catch (Exception e) {
            log.info("Knowledge retrieval skipped for chatbot {}: {}", chatBot.getId(), e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> buildMessages(ChatBot chatBot,
                                                    String sessionId,
                                                    String userText,
                                                    List<String> knowledge,
                                                    List<FileAttachment> attachments,
                                                    boolean selfContainedTask) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(chatBot, knowledge, selfContainedTask)));

        for (UserChatHistory turn : loadHistory(chatBot.getId(), sessionId)) {
            if (turn.getUserMessage() != null && !turn.getUserMessage().isBlank()) {
                messages.add(Map.of("role", "user", "content", turn.getUserMessage()));
            }
            if (turn.getAiMessage() != null && !turn.getAiMessage().isBlank()
                    && (turn.getSenderType() == null || !"admin_reply".equals(turn.getSenderType()) || turn.getUserMessage() == null)) {
                messages.add(Map.of("role", "assistant", "content", turn.getAiMessage()));
            }
        }

        messages.add(userMessage(userText, attachments));
        return messages;
    }

    private String systemPrompt(ChatBot chatBot, List<String> knowledge, boolean selfContainedTask) {
        StringBuilder prompt = new StringBuilder();
        String name = chatBot.getName() == null ? "the assistant" : chatBot.getName();
        prompt.append("You are ").append(name).append(", a helpful assistant.\n");
        if (chatBot.getInstructions() != null && !chatBot.getInstructions().isBlank()) {
            prompt.append("\nInstructions:\n").append(chatBot.getInstructions()).append('\n');
        }
        boolean restricted = Boolean.TRUE.equals(chatBot.getRestrictToDataSource()) && !selfContainedTask;
        boolean hasContext = !knowledge.isEmpty() || hasQa(chatBot);
        if (restricted && hasContext) {
            prompt.append("\nUse the knowledge and Q&A below when they answer the question. ");
            prompt.append("If they do not contain the answer, still answer from your instructions. ");
            prompt.append("Never reply with only a greeting.\n");
        } else if (restricted) {
            prompt.append("\nNo knowledge base is loaded for this question. ");
            prompt.append("Answer from your instructions. Never reply with only a greeting.\n");
        }
        if (selfContainedTask) {
            prompt.append("\nThe latest user message is a complete task. Follow its requested format and content.\n");
        }
        if (hasQa(chatBot)) {
            prompt.append("\nKnown questions and answers:\n");
            for (ChatBot.QAPair pair : chatBot.getQaPairs()) {
                if (pair == null) {
                    continue;
                }
                prompt.append("Q: ").append(nullToEmpty(pair.getQuestion()))
                        .append("\nA: ").append(nullToEmpty(pair.getAnswer())).append("\n");
            }
        }
        if (chatBot.getAddedTexts() != null && !chatBot.getAddedTexts().isEmpty()) {
            prompt.append("\nAdditional notes:\n");
            for (String note : chatBot.getAddedTexts()) {
                if (note != null && !note.isBlank()) {
                    prompt.append("- ").append(note).append('\n');
                }
            }
        }
        if (!knowledge.isEmpty()) {
            prompt.append("\nKnowledge context:\n");
            int used = 0;
            for (String chunk : knowledge) {
                if (used >= MAX_CONTEXT_CHARS) {
                    break;
                }
                String piece = chunk.length() > 2000 ? chunk.substring(0, 2000) : chunk;
                prompt.append(piece).append("\n---\n");
                used += piece.length();
            }
        }
        prompt.append("\nYou can call the provided tools when the user asks for an action those tools perform. ");
        prompt.append("After a tool returns, answer the user in plain language.");
        return prompt.toString();
    }

    private Map<String, Object> userMessage(String userText, List<FileAttachment> attachments) {
        List<Map<String, Object>> parts = new ArrayList<>();
        StringBuilder text = new StringBuilder(userText);
        if (attachments != null) {
            for (FileAttachment attachment : attachments) {
                if (attachment == null) {
                    continue;
                }
                boolean image = attachment.getMimeType() != null && attachment.getMimeType().startsWith("image/");
                String url = attachment.getDownloadUrl();
                if (image && url != null && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:"))) {
                    parts.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
                } else if (attachment.getFileName() != null) {
                    text.append("\nAttached file: ").append(attachment.getFileName());
                    if (attachment.getMimeType() != null) {
                        text.append(" (").append(attachment.getMimeType()).append(')');
                    }
                }
            }
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        if (parts.isEmpty()) {
            message.put("content", text.toString());
        } else {
            parts.add(0, Map.of("type", "text", "text", text.toString()));
            message.put("content", parts);
        }
        return message;
    }

    private List<Map<String, Object>> loadTools(String chatbotId) {
        try {
            McpToolsResponse response = workflowConfigService.getTools(chatbotId);
            if (response.getTools() == null) {
                return List.of();
            }
            List<Map<String, Object>> tools = new ArrayList<>();
            for (McpToolsResponse.Tool tool : response.getTools()) {
                if (tool.getFunction() == null || WORKFLOW_PLACEHOLDER_TOOL_NAME.equals(tool.getFunction().getName())) {
                    continue;
                }
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tool.getFunction().getName());
                function.put("description", tool.getFunction().getDescription() == null ? "" : tool.getFunction().getDescription());
                McpToolsResponse.ParameterSchema schema = tool.getFunction().getParameters();
                Map<String, Object> parameters = new LinkedHashMap<>();
                parameters.put("type", "object");
                parameters.put("properties", schema != null && schema.getProperties() != null ? schema.getProperties() : Map.of());
                parameters.put("required", schema != null && schema.getRequired() != null ? schema.getRequired() : List.of());
                function.put("parameters", parameters);
                tools.add(Map.of("type", "function", "function", function));
            }
            return tools;
        } catch (Exception e) {
            log.info("No workflow tools for chatbot {}: {}", chatbotId, e.getMessage());
            return List.of();
        }
    }

    private List<UserChatHistory> loadHistory(String chatbotId, String sessionId) {
        Query query = new Query(Criteria.where("chatbotId").is(chatbotId).and("conversationid").is(sessionId));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.limit(HISTORY_TURNS);
        List<UserChatHistory> turns = mongoTemplate.find(query, UserChatHistory.class);
        List<UserChatHistory> chronological = new ArrayList<>();
        for (int i = turns.size() - 1; i >= 0; i--) {
            chronological.add(turns.get(i));
        }
        return chronological;
    }

    private void saveHistory(ChatBot chatBot,
                             String sessionId,
                             String userText,
                             String reply,
                             boolean anonymous,
                             String historyEmail,
                             String historyMode) {
        String email = historyEmail != null && !historyEmail.isBlank() ? historyEmail : AuthUtils.getEmail();
        if (email == null || email.isBlank()) {
            email = anonymous ? "anonymous" : nullToEmpty(chatBot.getEmail());
        }
        UserChatHistory history = UserChatHistory.builder()
                .conversationid(sessionId)
                .chatbotId(chatBot.getId())
                .email(email)
                .userMessage(userText)
                .aiMessage(reply)
                .createdAt(Instant.now())
                .mode(historyMode == null || historyMode.isBlank() ? "chat" : historyMode)
                .isAnonymous(anonymous)
                .role("assistant")
                .senderType("bot")
                .status("sent")
                .build();
        try {
            mongoTemplate.save(history);
            log.info("Saved chat history chatbotId={} conversationid={}", chatBot.getId(), sessionId);
        } catch (RuntimeException e) {
            log.error("Failed to save chat history chatbotId={} conversationid={}: {}",
                    chatBot.getId(), sessionId, e.getMessage(), e);
            throw e;
        }
    }

    private Map<String, Object> assistantToolMessage(OpenAiApiClient.ChatCompletion completion) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (OpenAiApiClient.ToolCall toolCall : completion.toolCalls()) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("id", toolCall.id());
            call.put("type", "function");
            call.put("function", Map.of(
                    "name", toolCall.name(),
                    "arguments", toolCall.argumentsJson() == null ? "{}" : toolCall.argumentsJson()
            ));
            calls.add(call);
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", completion.content() == null ? "" : completion.content());
        message.put("tool_calls", calls);
        return message;
    }

    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static boolean containsImage(List<Map<String, Object>> messages) {
        for (Map<String, Object> message : messages) {
            if (message.get("content") instanceof List<?> parts) {
                for (Object part : parts) {
                    if (part instanceof Map<?, ?> map && "image_url".equals(map.get("type"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void stripImages(List<Map<String, Object>> messages, String userText) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).get("role"))) {
                messages.get(i).put("content", userText + "\n(The attached image could not be read.)");
                return;
            }
        }
    }

    private static String resolveModel(String requested, String chatbotModel) {
        if (isAllowedModel(requested)) {
            return requested;
        }
        if (isAllowedModel(chatbotModel)) {
            return chatbotModel;
        }
        return "gpt-4o-mini";
    }

    private static boolean isAllowedModel(String model) {
        return model != null && MODEL_NAME.matcher(model).matches();
    }

    private static boolean hasQa(ChatBot chatBot) {
        return chatBot.getQaPairs() != null && !chatBot.getQaPairs().isEmpty();
    }

    private static String fallback(ChatBot chatBot) {
        String fallback = chatBot.getFallbackMessage();
        if (fallback != null && !fallback.isBlank() && !isGreeting(chatBot, fallback)) {
            return fallback;
        }
        return "I don't have that information yet. Please try another question.";
    }

    private static boolean isGreeting(ChatBot chatBot, String reply) {
        String greeting = chatBot.getGreetingMessage();
        if (reply == null || greeting == null || greeting.isBlank()) {
            return false;
        }
        return reply.trim().equalsIgnoreCase(greeting.trim());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static N8NChatResponse<Object> success(String reply) {
        return N8NChatResponse.<Object>builder()
                .success(true)
                .result(reply)
                .status("200")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
