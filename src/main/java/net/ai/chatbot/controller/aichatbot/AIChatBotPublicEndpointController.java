package net.ai.chatbot.controller.aichatbot;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.aichatbot.ChatbotWidgetThemeDto;
import net.ai.chatbot.dto.aichatbot.PublicChatbotResponseDto;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.service.aichatbot.ChatBotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/public")
public class AIChatBotPublicEndpointController {

    private final ChatBotService chatBotService;

    public AIChatBotPublicEndpointController(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    /**
     * Get chatbot configuration for widget embedding
     * GET /v1/api/public/chatbot/{id}
     */
    @GetMapping("/chatbot/{id}")
    public ResponseEntity<PublicChatbotResponseDto> getChatBot(@PathVariable String id) {
        log.info("Getting public chatbot config: {}", id);

        ChatBot chatbot = chatBotService.getChatBot(id);

        if (chatbot == null) {
            return ResponseEntity.notFound().build();
        }

        // Parse width/height to Integer
        Integer width = parseToInteger(chatbot.getWidth(), 380);
        Integer height = parseToInteger(chatbot.getHeight(), 600);

        // Avatar resolution logic
        String resolvedAiAvatar = null;
        String resolvedAvatarFileId = null;

        // Check if aiAvatarUrl is a valid http/https URL (preset avatar)
        if (isValidHttpUrl(chatbot.getAiAvatarUrl())) {
            resolvedAiAvatar = chatbot.getAiAvatarUrl();
        } 
        // If no valid URL but has avatarFileId (custom upload)
        else if (chatbot.getAvatarFileId() != null && !chatbot.getAvatarFileId().isBlank()) {
            resolvedAvatarFileId = chatbot.getAvatarFileId();
            // Widget will build URL: {apiUrl}/api/attachments/download/{avatarFileId}?chatbotId={id}
        }
        // Ignore blob URLs or invalid URLs - widget will show first letter of name

        // Build nested theme object
        ChatbotWidgetThemeDto theme = ChatbotWidgetThemeDto.builder()
                .headerBackground(chatbot.getHeaderBackground())
                .headerText(chatbot.getHeaderText())
                .aiBackground(chatbot.getAiBackground())
                .aiText(chatbot.getAiText())
                .userBackground(chatbot.getUserBackground())
                .userText(chatbot.getUserText())
                .widgetPosition(chatbot.getWidgetPosition())
                .aiAvatar(resolvedAiAvatar)
                .hideMainBannerLogo(chatbot.getHideMainBannerLogo())
                .build();

        // Build response with both flat and nested theme for widget compatibility
        PublicChatbotResponseDto response = PublicChatbotResponseDto.builder()
                .id(chatbot.getId())
                .name(chatbot.getName())
                .title(chatbot.getTitle())
                .greetingMessage(chatbot.getGreetingMessage())
                .width(width)
                .height(height)
                .status(chatbot.getStatus())  // Widget checks this for disabled state
                // Flat theme fields (backward compatibility)
                .headerBackground(chatbot.getHeaderBackground())
                .headerText(chatbot.getHeaderText())
                .aiBackground(chatbot.getAiBackground())
                .aiText(chatbot.getAiText())
                .userBackground(chatbot.getUserBackground())
                .userText(chatbot.getUserText())
                .widgetPosition(chatbot.getWidgetPosition())
                .aiAvatar(resolvedAiAvatar)
                .avatarFileId(resolvedAvatarFileId)
                .hideMainBannerLogo(chatbot.getHideMainBannerLogo())
                // Nested theme object
                .theme(theme)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Check if URL is a valid http or https URL (not blob)
     */
    private boolean isValidHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        
        String trimmedUrl = url.trim().toLowerCase();
        
        // Ignore blob URLs
        if (trimmedUrl.startsWith("blob:")) {
            log.debug("Ignoring blob URL: {}", url);
            return false;
        }
        
        // Accept only http or https URLs
        return trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://");
    }

    private Integer parseToInteger(String value, Integer defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            // Remove 'px' suffix if present
            String cleaned = value.replace("px", "").trim();
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse '{}' to integer, using default {}", value, defaultValue);
            return defaultValue;
        }
    }

    @GetMapping("/chatHistory/{chatbotId}/{conversationId}")
    public ResponseEntity<List<UserChatHistory>> getChatHistory(@PathVariable String chatbotId, @PathVariable String conversationId) {

        List<UserChatHistory> chatHistories = chatBotService.getChatHistory(chatbotId, conversationId);

        return ResponseEntity.ok(chatHistories);
    }

    @GetMapping("/chatbot/messenger/{messengerId}")
    public ResponseEntity<ChatBot> getMessengerSetup(@PathVariable String messengerId) {
        try {

            log.info("Getting chatbot for messengerId: {}", messengerId);

            return ResponseEntity.ok(chatBotService.getChabotFromMessengerId(messengerId));

        } catch (Exception e) {
            log.error("Error getting messenger setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/chatbot/whatsapp/{phonenumberId}")
    public ResponseEntity<ChatBot> getWhatsappSetup(@PathVariable String phonenumberId) {
        try {

            log.info("Getting chatbot for phonenumberId: {}", phonenumberId);

            return ResponseEntity.ok(chatBotService.getChabotFromPhoneNumberID(phonenumberId));

        } catch (Exception e) {
            log.error("Error getting messenger setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get chatbot by Instagram Account ID (for webhook/n8n lookup)
     * GET /v1/api/public/chatbot/instagram/{instagramAccountId}
     */
    @GetMapping("/chatbot/instagram/{instagramAccountId}")
    public ResponseEntity<ChatBot> getInstagramSetup(@PathVariable String instagramAccountId) {
        try {
            log.info("Getting chatbot for instagramAccountId: {}", instagramAccountId);

            return ResponseEntity.ok(chatBotService.getChatbotFromInstagramAccountId(instagramAccountId));

        } catch (Exception e) {
            log.error("Error getting Instagram setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}

