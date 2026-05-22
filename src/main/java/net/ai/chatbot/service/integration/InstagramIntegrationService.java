package net.ai.chatbot.service.integration;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.InstagramIntegration;
import net.ai.chatbot.service.googlecalendar.ChatbotOwnershipService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing Instagram Business Account integrations for chatbot DM handling.
 * 
 * Instagram Messaging API requirements:
 * - Instagram Business or Creator Account
 * - Connected to a Facebook Page
 * - Meta App with instagram_basic and instagram_manage_messages permissions
 */
@Service
@Slf4j
public class InstagramIntegrationService {

    private final MongoTemplate mongoTemplate;
    private final ChatBotDao chatBotDao;
    private final ChatbotOwnershipService chatbotOwnershipService;

    public InstagramIntegrationService(MongoTemplate mongoTemplate, ChatBotDao chatBotDao,
                                        ChatbotOwnershipService chatbotOwnershipService) {
        this.mongoTemplate = mongoTemplate;
        this.chatBotDao = chatBotDao;
        this.chatbotOwnershipService = chatbotOwnershipService;
    }

    /**
     * Create or update an Instagram integration for a chatbot
     */
    @Transactional
    public InstagramIntegration createOrUpdateInstagramIntegration(InstagramIntegration instagramIntegration, String userEmail) {
        log.info("Creating/updating Instagram setup for chatbot: {} by user: {}", 
                instagramIntegration.chatbotId(), userEmail);

        validateChatbotId(instagramIntegration.chatbotId());
        validateAndGetChatBot(instagramIntegration.chatbotId(), userEmail, true);
        validateRequiredFields(instagramIntegration);

        Query existingQuery = new Query(Criteria.where("chatbotId").is(instagramIntegration.chatbotId()));
        InstagramIntegration existingIntegration = mongoTemplate.findOne(existingQuery, InstagramIntegration.class);

        InstagramIntegration savedIntegration;
        if (existingIntegration != null) {
            log.info("Updating existing Instagram integration for chatbot: {}", instagramIntegration.chatbotId());
            Boolean enabledStatus = instagramIntegration.enabled() != null 
                    ? instagramIntegration.enabled() 
                    : (existingIntegration.enabled() != null ? existingIntegration.enabled() : true);
            
            InstagramIntegration updatedIntegration = InstagramIntegration.builder()
                    .id(existingIntegration.id())
                    .chatbotId(instagramIntegration.chatbotId())
                    .accountName(instagramIntegration.accountName())
                    .instagramAccountId(instagramIntegration.instagramAccountId())
                    .instagramUsername(instagramIntegration.instagramUsername())
                    .connectedPageId(instagramIntegration.connectedPageId())
                    .accessToken(instagramIntegration.accessToken())
                    .webhookVerifyToken(instagramIntegration.webhookVerifyToken())
                    .enabled(enabledStatus)
                    .build();

            savedIntegration = mongoTemplate.save(updatedIntegration);
        } else {
            log.info("Creating new Instagram integration for chatbot: {}", instagramIntegration.chatbotId());
            Boolean enabledStatus = instagramIntegration.enabled() != null ? instagramIntegration.enabled() : true;
            InstagramIntegration newIntegration = InstagramIntegration.builder()
                    .chatbotId(instagramIntegration.chatbotId())
                    .accountName(instagramIntegration.accountName())
                    .instagramAccountId(instagramIntegration.instagramAccountId())
                    .instagramUsername(instagramIntegration.instagramUsername())
                    .connectedPageId(instagramIntegration.connectedPageId())
                    .accessToken(instagramIntegration.accessToken())
                    .webhookVerifyToken(instagramIntegration.webhookVerifyToken())
                    .enabled(enabledStatus)
                    .build();
            savedIntegration = mongoTemplate.save(newIntegration);
        }

        log.info("Instagram setup completed successfully for chatbot: {} with integration ID: {}",
                instagramIntegration.chatbotId(), savedIntegration.id());

        return savedIntegration;
    }

    /**
     * Get Instagram integration by chatbot ID
     */
    public Optional<InstagramIntegration> getInstagramIntegrationByChatbotId(String chatbotId, String userEmail) {
        validateAndGetChatBot(chatbotId, userEmail, false);
        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        InstagramIntegration integration = mongoTemplate.findOne(query, InstagramIntegration.class);
        return Optional.ofNullable(integration);
    }

    /**
     * Get Instagram integration by Instagram Account ID (for webhook/public lookup)
     */
    public Optional<InstagramIntegration> getByInstagramAccountId(String instagramAccountId) {
        Query query = new Query(Criteria.where("instagramAccountId").is(instagramAccountId));
        InstagramIntegration integration = mongoTemplate.findOne(query, InstagramIntegration.class);
        return Optional.ofNullable(integration);
    }

    /**
     * Toggle the enabled status of an Instagram integration.
     * If no integration exists and enabled=true, creates a placeholder integration.
     */
    @Transactional
    public InstagramIntegration toggleInstagramIntegrationStatus(String chatbotId, Boolean enabled, String userEmail) {
        log.info("Toggling Instagram integration status for chatbot: {} to {} by user: {}", 
                chatbotId, enabled, userEmail);

        validateChatbotId(chatbotId);
        validateAndGetChatBot(chatbotId, userEmail, true);

        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        InstagramIntegration existingIntegration = mongoTemplate.findOne(query, InstagramIntegration.class);

        InstagramIntegration savedIntegration;
        
        if (existingIntegration == null) {
            if (!enabled) {
                // Trying to disable a non-existent integration - just return null or a placeholder
                log.info("No Instagram integration exists for chatbot: {}, nothing to disable", chatbotId);
                return InstagramIntegration.builder()
                        .chatbotId(chatbotId)
                        .enabled(false)
                        .build();
            }
            // Create a new placeholder integration when enabling for the first time
            log.info("Creating new placeholder Instagram integration for chatbot: {}", chatbotId);
            InstagramIntegration newIntegration = InstagramIntegration.builder()
                    .chatbotId(chatbotId)
                    .enabled(true)
                    .build();
            savedIntegration = mongoTemplate.save(newIntegration);
        } else {
            InstagramIntegration updatedIntegration = InstagramIntegration.builder()
                    .id(existingIntegration.id())
                    .chatbotId(existingIntegration.chatbotId())
                    .accountName(existingIntegration.accountName())
                    .instagramAccountId(existingIntegration.instagramAccountId())
                    .instagramUsername(existingIntegration.instagramUsername())
                    .connectedPageId(existingIntegration.connectedPageId())
                    .accessToken(existingIntegration.accessToken())
                    .webhookVerifyToken(existingIntegration.webhookVerifyToken())
                    .enabled(enabled)
                    .build();

            savedIntegration = mongoTemplate.save(updatedIntegration);
        }

        log.info("Instagram integration status updated for chatbot: {} to {} by user: {}", 
                chatbotId, enabled, userEmail);

        return savedIntegration;
    }

    /**
     * Delete Instagram integration for a chatbot
     */
    @Transactional
    public void deleteInstagramIntegration(String chatbotId, String userEmail) {
        log.info("Deleting Instagram integration for chatbot: {} by user: {}", chatbotId, userEmail);
        
        validateChatbotId(chatbotId);
        validateAndGetChatBot(chatbotId, userEmail, true);

        Query query = new Query(Criteria.where("chatbotId").is(chatbotId));
        mongoTemplate.remove(query, InstagramIntegration.class);
        
        log.info("Instagram integration deleted for chatbot: {}", chatbotId);
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
            throw new IllegalStateException("You don't have permission to setup Instagram for this chatbot");
        }

        return chatBot;
    }

    private void validateRequiredFields(InstagramIntegration instagramIntegration) {
        if (instagramIntegration.instagramAccountId() == null || 
            instagramIntegration.instagramAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Instagram Account ID is required");
        }

        if (instagramIntegration.accessToken() == null || 
            instagramIntegration.accessToken().trim().isEmpty()) {
            throw new IllegalArgumentException("Access Token is required");
        }

        if (instagramIntegration.connectedPageId() == null || 
            instagramIntegration.connectedPageId().trim().isEmpty()) {
            throw new IllegalArgumentException("Connected Facebook Page ID is required");
        }
    }
}
