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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${meta.oauth-callback-secret:}")
    private String metaOAuthCallbackSecret;

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
     * Public server-to-server callback used by the Next.js Meta OAuth route for
     * Facebook Messenger chatbot setup. It must be signed with
     * META_OAUTH_CALLBACK_SECRET and does not use Clerk.
     */
    @PostMapping("/messenger/{chatbotId}/oauth-callback")
    public ResponseEntity<ChatBotCreationResponse> messengerOAuthCallbackConnect(
            @PathVariable String chatbotId,
            @RequestHeader(value = "X-Meta-OAuth-Callback-Secret", required = false) String callbackSecret,
            @RequestBody MessengerIntegration messengerIntegration) {
        try {
            if (metaOAuthCallbackSecret == null || metaOAuthCallbackSecret.isBlank()
                    || callbackSecret == null || !metaOAuthCallbackSecret.equals(callbackSecret)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ChatBotCreationResponse.builder()
                                .status("FAILED")
                                .message("Invalid OAuth callback secret")
                                .build());
            }

            MessengerIntegration integration = MessengerIntegration.builder()
                    .chatbotId(chatbotId)
                    .pageName(messengerIntegration.pageName())
                    .pageId(messengerIntegration.pageId())
                    .accessToken(messengerIntegration.accessToken())
                    .verifyToken(messengerIntegration.verifyToken())
                    .enabled(true)
                    .build();

            MessengerIntegration savedIntegration = messengerIntegrationService
                    .createOrUpdateMessengerIntegrationFromOAuth(integration);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ChatBotCreationResponse.builder()
                            .id(savedIntegration.id())
                            .status("SUCCESS")
                            .message("Facebook Messenger connected successfully via OAuth.")
                            .createdAt(new Date())
                            .createdBy("meta-oauth-callback")
                            .build());
        } catch (Exception e) {
            log.error("Error in Messenger OAuth callback connect", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to connect Facebook Messenger: " + e.getMessage())
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
     * Public server-to-server callback used by the Next.js Meta OAuth route for
     * WhatsApp chatbot setup. It must be signed with META_OAUTH_CALLBACK_SECRET
     * and does not use Clerk.
     */
    @PostMapping("/whatsapp/{chatbotId}/oauth-callback")
    public ResponseEntity<ChatBotCreationResponse> whatsAppOAuthCallbackConnect(
            @PathVariable String chatbotId,
            @RequestHeader(value = "X-Meta-OAuth-Callback-Secret", required = false) String callbackSecret,
            @RequestBody WhatsAppIntegration whatsAppIntegration) {
        try {
            if (metaOAuthCallbackSecret == null || metaOAuthCallbackSecret.isBlank()
                    || callbackSecret == null || !metaOAuthCallbackSecret.equals(callbackSecret)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ChatBotCreationResponse.builder()
                                .status("FAILED")
                                .message("Invalid OAuth callback secret")
                                .build());
            }

            WhatsAppIntegration integration = WhatsAppIntegration.builder()
                    .chatbotId(chatbotId)
                    .name(whatsAppIntegration.name())
                    .businessAccountId(whatsAppIntegration.businessAccountId())
                    .phoneNumberId(whatsAppIntegration.phoneNumberId())
                    .phoneNumber(whatsAppIntegration.phoneNumber())
                    .appId(whatsAppIntegration.appId())
                    .appSecret(whatsAppIntegration.appSecret())
                    .accessToken(whatsAppIntegration.accessToken())
                    .webhookVerifyToken(whatsAppIntegration.webhookVerifyToken())
                    .enabled(true)
                    .build();

            WhatsAppIntegration savedIntegration = whatsAppIntegrationService
                    .createOrUpdateWhatsAppIntegrationFromOAuth(integration);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ChatBotCreationResponse.builder()
                            .id(savedIntegration.id())
                            .status("SUCCESS")
                            .message("WhatsApp connected successfully via OAuth.")
                            .createdAt(new Date())
                            .createdBy("meta-oauth-callback")
                            .build());
        } catch (Exception e) {
            log.error("Error in WhatsApp OAuth callback connect", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to connect WhatsApp: " + e.getMessage())
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

    /**
     * One-Click Connect for Instagram - Creates/updates integration with OAuth credentials
     * POST /v1/api/chatbot/instagram/{chatbotId}/one-click-connect
     * 
     * This endpoint is called from the OAuth callback to automatically set up
     * the Instagram integration with minimal user input.
     */
    @PostMapping("/instagram/{chatbotId}/one-click-connect")
    public ResponseEntity<ChatBotCreationResponse> oneClickInstagramConnect(
            @PathVariable String chatbotId,
            @RequestBody InstagramIntegration instagramIntegration) {
        try {
            String userEmail = AuthUtils.getEmail();

            log.info("One-click Instagram connect for chatbot: {} by user: {}", chatbotId, userEmail);

            // Set the chatbotId and ensure it's enabled
            InstagramIntegration integration = InstagramIntegration.builder()
                    .chatbotId(chatbotId)
                    .accountName(instagramIntegration.accountName())
                    .instagramAccountId(instagramIntegration.instagramAccountId())
                    .instagramUsername(instagramIntegration.instagramUsername())
                    .connectedPageId(instagramIntegration.connectedPageId())
                    .accessToken(instagramIntegration.accessToken())
                    .webhookVerifyToken(instagramIntegration.webhookVerifyToken())
                    .enabled(true)
                    .build();

            InstagramIntegration savedIntegration = instagramIntegrationService
                    .createOrUpdateInstagramIntegration(integration, userEmail);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ChatBotCreationResponse.builder()
                            .id(savedIntegration.id())
                            .status("SUCCESS")
                            .message("Instagram connected successfully via OAuth! Your chatbot is now ready to respond to Instagram DMs.")
                            .createdAt(new Date())
                            .createdBy(userEmail)
                            .build());

        } catch (Exception e) {
            log.error("Error in one-click Instagram connect", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to connect Instagram: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Public server-to-server callback used by the Next.js Meta OAuth route.
     * It must be signed with META_OAUTH_CALLBACK_SECRET and does not use Clerk.
     */
    @PostMapping("/instagram/{chatbotId}/oauth-callback")
    public ResponseEntity<ChatBotCreationResponse> instagramOAuthCallbackConnect(
            @PathVariable String chatbotId,
            @RequestHeader(value = "X-Meta-OAuth-Callback-Secret", required = false) String callbackSecret,
            @RequestBody InstagramIntegration instagramIntegration) {
        try {
            if (metaOAuthCallbackSecret == null || metaOAuthCallbackSecret.isBlank()
                    || callbackSecret == null || !metaOAuthCallbackSecret.equals(callbackSecret)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ChatBotCreationResponse.builder()
                                .status("FAILED")
                                .message("Invalid OAuth callback secret")
                                .build());
            }

            InstagramIntegration integration = InstagramIntegration.builder()
                    .chatbotId(chatbotId)
                    .accountName(instagramIntegration.accountName())
                    .instagramAccountId(instagramIntegration.instagramAccountId())
                    .instagramUsername(instagramIntegration.instagramUsername())
                    .connectedPageId(instagramIntegration.connectedPageId())
                    .accessToken(instagramIntegration.accessToken())
                    .webhookVerifyToken(instagramIntegration.webhookVerifyToken())
                    .enabled(true)
                    .build();

            InstagramIntegration savedIntegration = instagramIntegrationService
                    .createOrUpdateInstagramIntegrationFromOAuth(integration);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ChatBotCreationResponse.builder()
                            .id(savedIntegration.id())
                            .status("SUCCESS")
                            .message("Instagram connected successfully via OAuth.")
                            .createdAt(new Date())
                            .createdBy("meta-oauth-callback")
                            .build());
        } catch (Exception e) {
            log.error("Error in Instagram OAuth callback connect", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatBotCreationResponse.builder()
                            .status("FAILED")
                            .message("Failed to connect Instagram: " + e.getMessage())
                            .build());
        }
    }
}
