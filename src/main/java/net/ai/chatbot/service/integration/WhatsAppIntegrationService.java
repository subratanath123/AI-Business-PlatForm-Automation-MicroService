package net.ai.chatbot.service.integration;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.WhatsAppIntegration;
import net.ai.chatbot.service.googlecalendar.ChatbotOwnershipService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing WhatsApp integrations
 */
@Service
@Slf4j
public class WhatsAppIntegrationService {

    private final MongoTemplate mongoTemplate;
    private final ChatBotDao chatBotDao;
    private final ChatbotOwnershipService chatbotOwnershipService;

    public WhatsAppIntegrationService(MongoTemplate mongoTemplate, ChatBotDao chatBotDao,
                                      ChatbotOwnershipService chatbotOwnershipService) {
        this.mongoTemplate = mongoTemplate;
        this.chatBotDao = chatBotDao;
        this.chatbotOwnershipService = chatbotOwnershipService;
    }

    /**
     * Create or update a WhatsApp integration for a chatbot
     *
     * @param whatsAppIntegration The integration data
     * @param userEmail            The authenticated user's email
     * @return The saved WhatsAppIntegration entity
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException    if chatbot not found or permission denied
     */
    @Transactional
    public WhatsAppIntegration createOrUpdateWhatsAppIntegration(WhatsAppIntegration whatsAppIntegration, String userEmail) {
        log.info("Creating/updating WhatsApp setup for chatbot: {} by user: {}", whatsAppIntegration.chatbotId(), userEmail);

        // Validate chatbotId is provided
        validateChatbotId(whatsAppIntegration.chatbotId());

        // Validate chatbot exists and user has permission
        validateAndGetChatBot(whatsAppIntegration.chatbotId(), userEmail, true);

        // Validate required fields
        validateRequiredFields(whatsAppIntegration);

        // Check if WhatsApp integration already exists for this chatbot
        Query existingQuery = new Query(Criteria.where("chatbotId").is(whatsAppIntegration.chatbotId()));
        WhatsAppIntegration existingIntegration = mongoTemplate.findOne(existingQuery, WhatsAppIntegration.class);

        WhatsAppIntegration savedIntegration;
        if (existingIntegration != null) {
            // Update existing integration
            log.info("Updating existing WhatsApp integration for chatbot: {}", whatsAppIntegration.chatbotId());
            // Preserve existing enabled status if not provided, otherwise use the provided value
            Boolean enabledStatus = whatsAppIntegration.enabled() != null 
                    ? whatsAppIntegration.enabled() 
                    : (existingIntegration.enabled() != null ? existingIntegration.enabled() : true);
            
            WhatsAppIntegration updatedIntegration = WhatsAppIntegration.builder()
                    .id(existingIntegration.id())
                    .name(whatsAppIntegration.name())
                    .businessAccountId(whatsAppIntegration.businessAccountId())
                    .chatbotId(whatsAppIntegration.chatbotId())
                    .phoneNumberId(whatsAppIntegration.phoneNumberId())
                    .phoneNumber(whatsAppIntegration.phoneNumber())
                    .appId(whatsAppIntegration.appId())
                    .appSecret(whatsAppIntegration.appSecret())
                    .accessToken(whatsAppIntegration.accessToken())
                    .webhookVerifyToken(whatsAppIntegration.webhookVerifyToken())
                    .enabled(enabledStatus)
                    .build();

            savedIntegration = mongoTemplate.save(updatedIntegration);

        } else {
            // Create new integration - default to enabled (true)
            log.info("Creating new WhatsApp integration for chatbot: {}", whatsAppIntegration.chatbotId());
            Boolean enabledStatus = whatsAppIntegration.enabled() != null ? whatsAppIntegration.enabled() : true;
            WhatsAppIntegration newIntegration = WhatsAppIntegration.builder()
                    .name(whatsAppIntegration.name())
                    .businessAccountId(whatsAppIntegration.businessAccountId())
                    .chatbotId(whatsAppIntegration.chatbotId())
                    .phoneNumberId(whatsAppIntegration.phoneNumberId())
                    .phoneNumber(whatsAppIntegration.phoneNumber())
                    .appId(whatsAppIntegration.appId())
                    .appSecret(whatsAppIntegration.appSecret())
                    .accessToken(whatsAppIntegration.accessToken())
                    .webhookVerifyToken(whatsAppIntegration.webhookVerifyToken())
                    .enabled(enabledStatus)
                    .build();
            savedIntegration = mongoTemplate.save(newIntegration);
        }

        log.info("WhatsApp setup completed successfully for chatbot: {} with integration ID: {}",
                whatsAppIntegration.chatbotId(), savedIntegration.id());

        return savedIntegration;
    }

    /**
     * Create or update WhatsApp integration from the trusted Meta OAuth callback.
     * The controller authenticates this path with META_OAUTH_CALLBACK_SECRET, so
     * this method does not require a Clerk user email.
     */
    @Transactional
    public WhatsAppIntegration createOrUpdateWhatsAppIntegrationFromOAuth(WhatsAppIntegration whatsAppIntegration) {
        log.info("Creating/updating WhatsApp setup from OAuth callback for chatbot: {}",
                whatsAppIntegration.chatbotId());

        validateChatbotId(whatsAppIntegration.chatbotId());
        validateRequiredFields(whatsAppIntegration);

        Query existingQuery = new Query(Criteria.where("chatbotId").is(whatsAppIntegration.chatbotId()));
        WhatsAppIntegration existingIntegration = mongoTemplate.findOne(existingQuery, WhatsAppIntegration.class);

        Boolean enabledStatus = whatsAppIntegration.enabled() != null
                ? whatsAppIntegration.enabled()
                : (existingIntegration != null && existingIntegration.enabled() != null
                    ? existingIntegration.enabled()
                    : true);

        WhatsAppIntegration integrationToSave = WhatsAppIntegration.builder()
                .id(existingIntegration == null ? null : existingIntegration.id())
                .name(whatsAppIntegration.name())
                .businessAccountId(whatsAppIntegration.businessAccountId())
                .chatbotId(whatsAppIntegration.chatbotId())
                .phoneNumberId(whatsAppIntegration.phoneNumberId())
                .phoneNumber(whatsAppIntegration.phoneNumber())
                .appId(whatsAppIntegration.appId())
                .appSecret(whatsAppIntegration.appSecret())
                .accessToken(whatsAppIntegration.accessToken())
                .webhookVerifyToken(whatsAppIntegration.webhookVerifyToken())
                .enabled(enabledStatus)
                .build();

        WhatsAppIntegration savedIntegration = mongoTemplate.save(integrationToSave);
        log.info("WhatsApp OAuth setup completed for chatbot: {} with integration ID: {}",
                whatsAppIntegration.chatbotId(), savedIntegration.id());
        return savedIntegration;
    }

    /**
     * Get WhatsApp integration by chatbot ID
     *
     * @param chatbotId The chatbot ID
     * @param userEmail The authenticated user's email
     * @return Optional WhatsAppIntegration
     */
    public Optional<WhatsAppIntegration> getWhatsAppIntegrationByChatbotId(String chatbotId, String userEmail) {
        // Validate user has access to the chatbot
        validateAndGetChatBot(chatbotId, userEmail, false);

        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        WhatsAppIntegration integration = mongoTemplate.findOne(query, WhatsAppIntegration.class);
        return Optional.ofNullable(integration);
    }

    /**
     * Toggle the enabled status of a WhatsApp integration
     *
     * @param chatbotId The chatbot ID
     * @param enabled    The new enabled status
     * @param userEmail  The authenticated user's email
     * @return The updated WhatsAppIntegration entity
     * @throws IllegalStateException if integration not found
     */
    @Transactional
    public WhatsAppIntegration toggleWhatsAppIntegrationStatus(String chatbotId, Boolean enabled, String userEmail) {
        log.info("Toggling WhatsApp integration status for chatbot: {} to {} by user: {}", chatbotId, enabled, userEmail);

        // Validate chatbotId is provided
        validateChatbotId(chatbotId);

        // Validate chatbot exists and user has permission
        validateAndGetChatBot(chatbotId, userEmail, true);

        // Get existing integration
        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        WhatsAppIntegration existingIntegration = mongoTemplate.findOne(query, WhatsAppIntegration.class);

        if (existingIntegration == null) {
            throw new IllegalStateException("WhatsApp integration not found for chatbot: " + chatbotId);
        }

        // Update enabled status
        WhatsAppIntegration updatedIntegration = WhatsAppIntegration.builder()
                .id(existingIntegration.id())
                .name(existingIntegration.name())
                .businessAccountId(existingIntegration.businessAccountId())
                .chatbotId(existingIntegration.chatbotId())
                .phoneNumberId(existingIntegration.phoneNumberId())
                .phoneNumber(existingIntegration.phoneNumber())
                .appId(existingIntegration.appId())
                .appSecret(existingIntegration.appSecret())
                .accessToken(existingIntegration.accessToken())
                .webhookVerifyToken(existingIntegration.webhookVerifyToken())
                .enabled(enabled)
                .build();

        WhatsAppIntegration savedIntegration = mongoTemplate.save(updatedIntegration);

        log.info("WhatsApp integration status updated for chatbot: {} to {} by user: {}", 
                chatbotId, enabled, userEmail);

        return savedIntegration;
    }

    private void validateChatbotId(String chatbotId) {
        if (chatbotId == null || chatbotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Chatbot ID is required");
        }
    }

    private ChatBot validateAndGetChatBot(String chatbotId, String userEmail, boolean requireConfigure) {
        Optional<ChatBot> chatbotOpt = chatBotDao.findById(chatbotId);
        if (chatbotOpt.isEmpty()) {
            throw new IllegalStateException("Chatbot not found with ID: " + chatbotId);
        }

        ChatBot chatBot = chatbotOpt.get();
        if (AuthUtils.isAdmin()) {
            return chatBot;
        }
        try {
            if (requireConfigure) {
                chatbotOwnershipService.verifyCanConfigure(chatbotId, userEmail);
            } else {
                chatbotOwnershipService.verifyCanView(chatbotId, userEmail);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Chatbot not found with ID: " + chatbotId);
        } catch (SecurityException e) {
            throw new IllegalStateException("You don't have permission to setup WhatsApp for this chatbot");
        }

        return chatBot;
    }

    private void validateRequiredFields(WhatsAppIntegration whatsAppIntegration) {
        if (whatsAppIntegration.phoneNumberId() == null || whatsAppIntegration.phoneNumberId().trim().isEmpty()) {
            throw new IllegalArgumentException("Phone Number ID is required");
        }

        if (whatsAppIntegration.accessToken() == null || whatsAppIntegration.accessToken().trim().isEmpty()) {
            throw new IllegalArgumentException("Access Token is required");
        }
    }
}

