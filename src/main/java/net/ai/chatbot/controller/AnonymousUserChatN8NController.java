package net.ai.chatbot.controller;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.service.n8n.GenericN8NService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/n8n/anonymous")
public class AnonymousUserChatN8NController {

    @Autowired
    private GenericN8NService<Message, Object> n8nService;

    @Autowired
    private ChatBotService chatBotService;

    @Autowired
    private GuardrailService guardrailService;

    @Value("${n8n.webhook.knowledgebase.chat.url}")
    private String webhookUrl;

    @Value("${n8n.webhook.knowledgebase.multimodal.chat.url}")
    private String multimodalWebhookUrl;

    /**
     * Send a single message to N8N workflow
     */
    @PostMapping("/chat")
    public ResponseEntity<N8NChatResponse<Object>> sendMessage(
            @RequestHeader(value = "userToken", required = false) String userToken,
            @RequestBody Message message) {

        // Guardrail validation for chat messages
        String userId = message.getSessionId() != null ? message.getSessionId() : "anonymous";
        GuardrailService.ValidationResult validationResult = guardrailService.validateContent(
                message.getMessage(), "CONTENT", userId);
        
        if (!validationResult.isAllowed()) {
            log.warn("Chat message blocked by guardrails: {}", validationResult.getMessage());
            N8NChatResponse<Object> blockedResponse = new N8NChatResponse<>();
            blockedResponse.setSuccess(false);
            blockedResponse.setErrorMessage("Your message was blocked due to content policy violations. Please rephrase and try again.");
            return ResponseEntity.ok(blockedResponse);
        }

        ChatBot chatBot = chatBotService.getChatBot(message.getChatbotId());

        N8NChatResponse<Object> response = n8nService.sendMessage(chatBot, message,
                message.getFileAttachments() != null && !message.getFileAttachments().isEmpty()
                        ? multimodalWebhookUrl
                        : webhookUrl,
                forwardUserTokenHeader(userToken)
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat/generic")
    public ResponseEntity<N8NChatResponse<Object>> sendGenericMessage(
            @RequestHeader(value = "userToken", required = false) String userToken,
            @RequestBody Message message) {
        log.info("Anonymous generic chat request received - sessionId: {}", message.getSessionId());

        // Guardrail validation for content generation
        String userId = message.getSessionId() != null ? message.getSessionId() : "anonymous";
        GuardrailService.ValidationResult validationResult = guardrailService.validateTextContent(
                message.getMessage(), userId);
        
        if (!validationResult.isAllowed()) {
            log.warn("Content generation blocked by guardrails: {}", validationResult.getMessage());
            N8NChatResponse<Object> blockedResponse = new N8NChatResponse<>();
            blockedResponse.setSuccess(false);
            blockedResponse.setErrorMessage(validationResult.getMessage());
            return ResponseEntity.ok(blockedResponse);
        }

        ChatBot chatBot;
        
        // If no chatbotId provided, use default platform demo chatbot
        if (message.getChatbotId() == null || message.getChatbotId().trim().isEmpty()) {
            log.info("No chatbotId provided, creating default platform demo chatbot");
            chatBot = createDefaultPlatformChatBot();
        } else {
            chatBot = chatBotService.getChatBot(message.getChatbotId());
        }

        N8NChatResponse<Object> response = n8nService.sendMessage(chatBot, message,
                message.getFileAttachments() != null && !message.getFileAttachments().isEmpty()
                        ? multimodalWebhookUrl
                        : webhookUrl,
                forwardUserTokenHeader(userToken)
        );

        return ResponseEntity.ok(response);
    }

    /** Forwards the widget visitor token to N8N as the {@code userToken} header. */
    private static Map<String, String> forwardUserTokenHeader(String userToken) {
        if (userToken == null || userToken.isBlank()) {
            return Collections.emptyMap();
        }
        return Map.of("userToken", userToken);
    }

    /**
     * Creates a default platform demo chatbot for anonymous users.
     * This chatbot is configured to showcase the platform's capabilities
     * and guide users about our automation features.
     */
    private ChatBot createDefaultPlatformChatBot() {
        ChatBot chatBot = new ChatBot();
        chatBot.setId("PLATFORM_DEMO_BOT");
        chatBot.setName("Platform Demo Assistant");
        chatBot.setTitle("AI Chatbot Platform - Demo");
        chatBot.setEmail("demo@platform.com");
        chatBot.setHideName(false);
        
        // Platform-focused instructions
        chatBot.setInstructions(
            "You are the AI Chatbot Platform Demo Assistant. Your role is to:" +
            "1. Showcase the capabilities of our AI chatbot platform" +
            "2. Explain how our platform helps businesses automate customer interactions" +
            "3. Describe key features: knowledge base integration, multimodal support (text, images, PDFs), " +
            "   website scraping, custom training, N8N workflow automation, Google Calendar integration, " +
            "   and social media scheduling (Facebook and Twitter)" +
            "4. Guide users on how to create their own custom chatbots" +
            "5. Answer questions about pricing, setup, and use cases" +
            "6. Provide examples of automation workflows (e.g., customer support, appointment booking, lead generation)" +
            "" +
            "Be helpful, professional, and focus on demonstrating the value of our platform. " +
            "If users ask about topics outside the platform's scope, politely redirect them to platform-related questions."
        );
        
        chatBot.setRestrictToDataSource(true);
        
        chatBot.setFallbackMessage(
            "I'm here to help you learn about our AI Chatbot Platform and its automation capabilities. " +
            "Could you please rephrase your question or ask about our platform features, pricing, or use cases?"
        );
        
        chatBot.setGreetingMessage(
            "Welcome to our AI Chatbot Platform Demo!" +
            "I'm here to show you how our platform can help automate your business interactions. " +
            "You can ask me about:" +
            "- Platform features and capabilities" +
            "- How to create custom chatbots" +
            "- Automation workflows (N8N integration)" +
            "- Knowledge base training" +
            "- Social media scheduling" +
            "- Pricing and setup" +
            "What would you like to know?"
        );
        
        chatBot.setSelectedDataSource("platform_knowledge");
        chatBot.setWidth("400px");
        chatBot.setHeight("600px");
        
        // Optional: Add default Q&A pairs about the platform
        java.util.List<ChatBot.QAPair> platformQAs = new java.util.ArrayList<>();
        platformQAs.add(new ChatBot.QAPair(
            "What is this platform?",
            "This is an AI-powered chatbot platform that helps businesses automate customer interactions. " +
            "You can create custom chatbots, train them with your data (websites, documents, text), " +
            "and integrate them with your workflows using N8N automation, Google Calendar, and social media."
        ));
        platformQAs.add(new ChatBot.QAPair(
            "What features do you offer?",
            "Our platform offers: (1) Custom chatbot creation with AI, (2) Knowledge base training (upload PDFs, scrape websites, add text), " +
            "(3) Multimodal support (text, images, documents), (4) N8N workflow automation, " +
            "(5) Google Calendar integration for appointments, (6) Social media scheduling (Facebook and Twitter), " +
            "(7) MCP (Model Context Protocol) integrations, (8) Widget embedding for your website."
        ));
        platformQAs.add(new ChatBot.QAPair(
            "How do I get started?",
            "Getting started is easy: (1) Sign up for an account, (2) Create your first chatbot, " +
            "(3) Train it with your business data (upload documents, add website URLs, or enter text), " +
            "(4) Customize the appearance and behavior, (5) Embed the chat widget on your website. " +
            "You can start with our free tier and upgrade as you grow!"
        ));
        platformQAs.add(new ChatBot.QAPair(
            "What automation can I do?",
            "You can automate various workflows: (1) Customer support with knowledge base answers, " +
            "(2) Appointment booking via Google Calendar integration, (3) Lead capture and CRM updates via N8N, " +
            "(4) Social media post scheduling for Facebook and Twitter, (5) File conversion and processing, " +
            "(6) Custom workflows using our MCP integrations. All automation is no-code or low-code!"
        ));
        
        chatBot.setQaPairs(platformQAs);
        
        log.info("Created default platform demo chatbot for anonymous user");
        return chatBot;
    }
}
