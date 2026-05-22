package net.ai.chatbot.controller.aichatbot;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationResponse;
import net.ai.chatbot.entity.InstagramIntegration;
import net.ai.chatbot.entity.MessengerIntegration;
import net.ai.chatbot.entity.WhatsAppIntegration;
import net.ai.chatbot.service.integration.InstagramIntegrationService;
import net.ai.chatbot.service.integration.MessengerIntegrationService;
import net.ai.chatbot.service.integration.WhatsAppIntegrationService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/chatbot")
public class ExternalIntegrationController {

    private final MessengerIntegrationService messengerIntegrationService;
    private final WhatsAppIntegrationService whatsAppIntegrationService;
    private final InstagramIntegrationService instagramIntegrationService;

    public ExternalIntegrationController(MessengerIntegrationService messengerIntegrationService,
                                         WhatsAppIntegrationService whatsAppIntegrationService,
                                         InstagramIntegrationService instagramIntegrationService) {
        this.messengerIntegrationService = messengerIntegrationService;
        this.whatsAppIntegrationService = whatsAppIntegrationService;
        this.instagramIntegrationService = instagramIntegrationService;
    }

    /**
     * Create a new Messenger Setup configuration
     * POST /v1/api/chatbot/messenger/setup
     */
    @PostMapping("/messenger/setup")
    public ResponseEntity<ChatBotCreationResponse> createMessengerSetup(@Valid @RequestBody MessengerIntegration messengerIntegration) {
        try {
            String userEmail = AuthUtils.getEmail();

            MessengerIntegration savedIntegration = messengerIntegrationService
                    .createOrUpdateMessengerIntegration(messengerIntegration, userEmail);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ChatBotCreationResponse.builder()
                            .id(savedIntegration.id())
                            .status("SUCCESS")
                            .message("Messenger setup completed successfully")
                            .createdAt(new Date())
                            .createdBy(userEmail)
                            .build());

        } catch (Exception e) {
            log.error("Error creating messenger setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to create messenger setup: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Get Messenger Setup configuration by chatbot ID
     * GET /v1/api/chatbot/messenger/{chatbotId}
     */
    @GetMapping("/messenger/{chatbotId}")
    public ResponseEntity<MessengerIntegration> getMessengerSetup(@PathVariable String chatbotId) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("Getting Messenger setup for chatbot: {} by user: {}", chatbotId, userEmail);

            return messengerIntegrationService
                    .getMessengerIntegrationByChatbotId(chatbotId, userEmail)
                    .map(integration -> {
                        log.info("Messenger integration found for chatbot: {}", chatbotId);
                        return ResponseEntity.ok(integration);
                    })
                    .orElseGet(() -> {
                        log.info("Messenger integration not found for chatbot: {}", chatbotId);
                        return ResponseEntity.notFound().build();
                    });

        } catch (Exception e) {
            log.error("Error getting messenger setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Toggle Messenger Integration enabled/disabled status
     * PUT /v1/api/chatbot/messenger/{chatbotId}/toggle
     */
    @PutMapping("/messenger/{chatbotId}/toggle")
    public ResponseEntity<ChatBotCreationResponse> toggleMessengerIntegration(
            @PathVariable String chatbotId,
            @RequestParam(defaultValue = "false") Boolean enabled) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("Toggling messenger integration for chatbot: {} to {} by user: {}", chatbotId, enabled, userEmail);

            MessengerIntegration updatedIntegration = messengerIntegrationService
                    .toggleMessengerIntegrationStatus(chatbotId, enabled, userEmail);

            return ResponseEntity.ok(ChatBotCreationResponse.builder()
                    .id(updatedIntegration.id())
                    .status("SUCCESS")
                    .message("Messenger integration " + (enabled ? "enabled" : "disabled") + " successfully")
                    .createdAt(new Date())
                    .createdBy(userEmail)
                    .build());

        } catch (Exception e) {
            log.error("Error toggling messenger integration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to toggle messenger integration: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Create a new WhatsApp Setup configuration
     * POST /v1/api/chatbot/whatsapp/setup
     */
    @PostMapping("/whatsapp/setup")
    public ResponseEntity<ChatBotCreationResponse> createWhatsAppSetup(@Valid @RequestBody WhatsAppIntegration whatsAppIntegration) {
        try {
            String userEmail = AuthUtils.getEmail();

            WhatsAppIntegration savedIntegration = whatsAppIntegrationService
                    .createOrUpdateWhatsAppIntegration(whatsAppIntegration, userEmail);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ChatBotCreationResponse.builder()
                            .id(savedIntegration.id())
                            .status("SUCCESS")
                            .message("WhatsApp setup completed successfully")
                            .createdAt(new Date())
                            .createdBy(userEmail)
                            .build());

        } catch (Exception e) {
            log.error("Error creating WhatsApp setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to create WhatsApp setup: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Get WhatsApp Setup configuration by chatbot ID
     * GET /v1/api/chatbot/whatsapp/{chatbotId}
     */
    @GetMapping("/whatsapp/{chatbotId}")
    public ResponseEntity<WhatsAppIntegration> getWhatsAppSetup(@PathVariable String chatbotId) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("Getting WhatsApp setup for chatbot: {} by user: {}", chatbotId, userEmail);

            return whatsAppIntegrationService
                    .getWhatsAppIntegrationByChatbotId(chatbotId, userEmail)
                    .map(integration -> {
                        log.info("WhatsApp integration found for chatbot: {}", chatbotId);
                        return ResponseEntity.ok(integration);
                    })
                    .orElseGet(() -> {
                        log.info("WhatsApp integration not found for chatbot: {}", chatbotId);
                        return ResponseEntity.notFound().build();
                    });

        } catch (Exception e) {
            log.error("Error getting WhatsApp setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Toggle WhatsApp Integration enabled/disabled status
     * PUT /v1/api/chatbot/whatsapp/{chatbotId}/toggle
     */
    @PutMapping("/whatsapp/{chatbotId}/toggle")
    public ResponseEntity<ChatBotCreationResponse> toggleWhatsAppIntegration(
            @PathVariable String chatbotId,
            @RequestParam(defaultValue = "false") Boolean enabled) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("Toggling WhatsApp integration for chatbot: {} to {} by user: {}", chatbotId, enabled, userEmail);

            WhatsAppIntegration updatedIntegration = whatsAppIntegrationService
                    .toggleWhatsAppIntegrationStatus(chatbotId, enabled, userEmail);

            return ResponseEntity.ok(ChatBotCreationResponse.builder()
                    .id(updatedIntegration.id())
                    .status("SUCCESS")
                    .message("WhatsApp integration " + (enabled ? "enabled" : "disabled") + " successfully")
                    .createdAt(new Date())
                    .createdBy(userEmail)
                    .build());

        } catch (Exception e) {
            log.error("Error toggling WhatsApp integration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to toggle WhatsApp integration: " + e.getMessage())
                            .build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INSTAGRAM INTEGRATION ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new Instagram Setup configuration
     * POST /v1/api/chatbot/instagram/setup
     */
    @PostMapping("/instagram/setup")
    public ResponseEntity<ChatBotCreationResponse> createInstagramSetup(@Valid @RequestBody InstagramIntegration instagramIntegration) {
        try {
            String userEmail = AuthUtils.getEmail();

            InstagramIntegration savedIntegration = instagramIntegrationService
                    .createOrUpdateInstagramIntegration(instagramIntegration, userEmail);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ChatBotCreationResponse.builder()
                            .id(savedIntegration.id())
                            .status("SUCCESS")
                            .message("Instagram setup completed successfully")
                            .createdAt(new Date())
                            .createdBy(userEmail)
                            .build());

        } catch (Exception e) {
            log.error("Error creating Instagram setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to create Instagram setup: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Get Instagram Setup configuration by chatbot ID
     * GET /v1/api/chatbot/instagram/{chatbotId}
     */
    @GetMapping("/instagram/{chatbotId}")
    public ResponseEntity<InstagramIntegration> getInstagramSetup(@PathVariable String chatbotId) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("Getting Instagram setup for chatbot: {} by user: {}", chatbotId, userEmail);

            return instagramIntegrationService
                    .getInstagramIntegrationByChatbotId(chatbotId, userEmail)
                    .map(integration -> {
                        log.info("Instagram integration found for chatbot: {}", chatbotId);
                        return ResponseEntity.ok(integration);
                    })
                    .orElseGet(() -> {
                        log.info("Instagram integration not found for chatbot: {}", chatbotId);
                        return ResponseEntity.notFound().build();
                    });

        } catch (Exception e) {
            log.error("Error getting Instagram setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Toggle Instagram Integration enabled/disabled status
     * PUT /v1/api/chatbot/instagram/{chatbotId}/toggle
     */
    @PutMapping("/instagram/{chatbotId}/toggle")
    public ResponseEntity<ChatBotCreationResponse> toggleInstagramIntegration(
            @PathVariable String chatbotId,
            @RequestParam(defaultValue = "false") Boolean enabled) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("Toggling Instagram integration for chatbot: {} to {} by user: {}", chatbotId, enabled, userEmail);

            InstagramIntegration updatedIntegration = instagramIntegrationService
                    .toggleInstagramIntegrationStatus(chatbotId, enabled, userEmail);

            return ResponseEntity.ok(ChatBotCreationResponse.builder()
                    .id(updatedIntegration.id())
                    .status("SUCCESS")
                    .message("Instagram integration " + (enabled ? "enabled" : "disabled") + " successfully")
                    .createdAt(new Date())
                    .createdBy(userEmail)
                    .build());

        } catch (Exception e) {
            log.error("Error toggling Instagram integration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to toggle Instagram integration: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Delete Instagram Integration
     * DELETE /v1/api/chatbot/instagram/{chatbotId}
     */
    @DeleteMapping("/instagram/{chatbotId}")
    public ResponseEntity<ChatBotCreationResponse> deleteInstagramSetup(@PathVariable String chatbotId) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("Deleting Instagram setup for chatbot: {} by user: {}", chatbotId, userEmail);

            instagramIntegrationService.deleteInstagramIntegration(chatbotId, userEmail);

            return ResponseEntity.ok(ChatBotCreationResponse.builder()
                    .status("SUCCESS")
                    .message("Instagram integration deleted successfully")
                    .createdAt(new Date())
                    .createdBy(userEmail)
                    .build());

        } catch (Exception e) {
            log.error("Error deleting Instagram setup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to delete Instagram setup: " + e.getMessage())
                            .build());
        }
    }
}
