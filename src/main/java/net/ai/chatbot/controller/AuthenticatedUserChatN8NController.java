package net.ai.chatbot.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.ChatbotReplyRequest;
import net.ai.chatbot.dto.ChatbotReplyResponse;
import net.ai.chatbot.dto.Message;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.n8n.N8NChatResponse;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.service.aiprocess.NativeAiChatService;
import net.ai.chatbot.service.googlecalendar.ChatbotOwnershipService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authenticated user chat endpoint for N8N integration
 * Supports both text messages and file attachments
 * Requires authentication context
 */
@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/n8n/authenticated")
public class AuthenticatedUserChatN8NController {

    @Autowired
    private NativeAiChatService nativeAiChatService;

    @Autowired
    private ChatBotService chatBotService;

    @Autowired
    private ChatbotOwnershipService chatbotOwnershipService;

    @Autowired
    private GuardrailService guardrailService;

    @PostMapping("/chatHistory/{chatbotId}")
    public ResponseEntity<List<UserChatHistory>> getConversationList(@PathVariable String chatbotId) {
        if (chatBotService.getChatBot(chatbotId) == null) {
            return ResponseEntity.notFound().build();
        }

        if (canReadAllConversations(chatbotId)) {
            return ResponseEntity.ok(chatBotService.getChatConversationList(chatbotId));
        }

        String email = AuthUtils.getEmail();
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(chatBotService.getChatConversationList(chatbotId, email));
    }

    @PostMapping("/chatHistory/{chatbotId}/{conversationId}")
    public ResponseEntity<List<UserChatHistory>> getChatHistory(@PathVariable String chatbotId, @PathVariable String conversationId) {
        if (chatBotService.getChatBot(chatbotId) == null) {
            return ResponseEntity.notFound().build();
        }

        boolean readAll = canReadAllConversations(chatbotId);
        if (!readAll) {
            String email = AuthUtils.getEmail();
            if (email == null || email.isBlank()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        return ResponseEntity.ok(chatBotService.getChatHistory(chatbotId, conversationId, !readAll));
    }

    /**
     * Owners, teammates, and admins can open every thread on a chatbot.
     * Everyone else may still read the conversations saved under their own email.
     */
    private boolean canReadAllConversations(String chatbotId) {
        if (AuthUtils.isAdmin()) {
            return true;
        }
        try {
            chatbotOwnershipService.verifyCanView(chatbotId, AuthUtils.getEmail());
            return true;
        } catch (IllegalArgumentException | SecurityException e) {
            return false;
        }
    }

    /**
     * Send a single message to N8N workflow
     */
    @PostMapping("/chat")
    public ResponseEntity<N8NChatResponse<Object>> sendMessage(
            @RequestHeader(value = "userToken", required = false) String userToken,
            @RequestBody Message message) {

        // Guardrail validation
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            userId = message.getSessionId() != null ? message.getSessionId() : "anonymous";
        }
        
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

        N8NChatResponse<Object> response = nativeAiChatService.reply(chatBot, message, userToken, false);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /v1/api/n8n/authenticated/chatbot-reply
     * 
     * Send a reply on behalf of a chatbot in an existing conversation
     * Used by admins/managers in the conversation history admin panel
     * 
     * @param request ChatbotReplyRequest containing conversationId, chatbotId, message, and role
     * @return ChatbotReplyResponse with success status and saved message details
     */
    @PostMapping("/chatbot-reply")
    public ResponseEntity<ChatbotReplyResponse> chatbotReply(@Valid @RequestBody ChatbotReplyRequest request) {
        long timestamp = System.currentTimeMillis();
        
        try {
            // Get authenticated user email
            String userEmail = AuthUtils.getEmail();
            if (userEmail == null || userEmail.isBlank()) {
                log.warn("Unauthenticated request to chatbot-reply endpoint");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ChatbotReplyResponse.error("UNAUTHORIZED", 
                                "Invalid or expired token", timestamp));
            }
            
            log.info("Chatbot reply request from user: {}, chatbotId: {}, conversationId: {}", 
                    userEmail, request.getChatbotId(), request.getConversationId());
            
            // Validate required fields (additional validation beyond annotations)
            if (request.getConversationId() == null || request.getConversationId().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ChatbotReplyResponse.error("MISSING_FIELD", 
                                "conversationId is required", timestamp));
            }
            
            if (request.getChatbotId() == null || request.getChatbotId().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ChatbotReplyResponse.error("MISSING_FIELD", 
                                "chatbotId is required", timestamp));
            }
            
            // Validate message
            String message = request.getMessage();
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ChatbotReplyResponse.error("EMPTY_MESSAGE", 
                                "Message cannot be empty", timestamp));
            }
            
            if (message.length() > 10000) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ChatbotReplyResponse.error("MESSAGE_TOO_LONG", 
                                "Message exceeds maximum length of 10000 characters", timestamp));
            }
            
            // Validate role
            if (!"assistant".equals(request.getRole())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ChatbotReplyResponse.error("INVALID_ROLE", 
                                "Role must be 'assistant'", timestamp));
            }
            
            // Verify chatbot exists
            ChatBot chatBot;
            try {
                chatBot = chatBotService.getChatBot(request.getChatbotId());
                if (chatBot == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ChatbotReplyResponse.error("CHATBOT_NOT_FOUND", 
                                    "Chatbot not found: " + request.getChatbotId(), timestamp));
                }
            } catch (Exception e) {
                log.error("Error fetching chatbot: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ChatbotReplyResponse.error("CHATBOT_NOT_FOUND", 
                                "Chatbot not found: " + request.getChatbotId(), timestamp));
            }
            
            // Owner or team Admin/Editor can send admin replies
            try {
                chatbotOwnershipService.verifyCanConfigure(request.getChatbotId(), userEmail);
            } catch (SecurityException e) {
                log.warn("Permission denied for user {} on chatbot {}", userEmail, request.getChatbotId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ChatbotReplyResponse.error("INSUFFICIENT_PERMISSIONS", 
                                "You do not have permission to reply in this conversation", timestamp));
            } catch (Exception e) {
                log.error("Error verifying chatbot ownership: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ChatbotReplyResponse.error("INSUFFICIENT_PERMISSIONS", 
                                "You do not have permission to reply in this conversation", timestamp));
            }
            
            // Verify conversation exists and belongs to this chatbot
            boolean conversationExists = chatBotService.verifyConversationOwnership(
                    request.getConversationId(), request.getChatbotId());
            
            if (!conversationExists) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ChatbotReplyResponse.error("CONVERSATION_NOT_FOUND", 
                                "Conversation not found: " + request.getConversationId(), timestamp));
            }
            
            // Save admin reply to database
            UserChatHistory savedMessage;
            try {
                savedMessage = chatBotService.saveAdminReply(
                        request.getConversationId(),
                        request.getChatbotId(),
                        message.trim(),
                        userEmail
                );
            } catch (Exception e) {
                log.error("Failed to save admin reply to database: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ChatbotReplyResponse.error("DATABASE_ERROR", 
                                "Failed to save message to database", timestamp));
            }
            
            // Build success response
            ChatbotReplyResponse response = ChatbotReplyResponse.success(
                    savedMessage.getId(),
                    request.getConversationId(),
                    request.getChatbotId(),
                    message.trim(),
                    "assistant",
                    timestamp
            );
            
            log.info("Admin reply saved successfully: messageId={}, conversationId={}", 
                    savedMessage.getId(), request.getConversationId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Unexpected error in chatbot-reply endpoint: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatbotReplyResponse.error("UNEXPECTED_ERROR", 
                            "An unexpected error occurred", timestamp));
        }
    }

}
