package net.ai.chatbot.service.aichatbot;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ChatBotDao;
import net.ai.chatbot.dao.TeamMembershipDao;
import net.ai.chatbot.entity.TeamMembership;
import net.ai.chatbot.service.googlecalendar.ChatbotOwnershipService;
import net.ai.chatbot.service.team.TeamService;
import net.ai.chatbot.dto.UserChatHistory;
import net.ai.chatbot.dto.aichatbot.ChatBotCreationRequest;
import net.ai.chatbot.entity.*;
import net.ai.chatbot.entity.ChatBot.QAPair;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static net.ai.chatbot.constants.Constants.CHAT_BOT_CREATE_EVENT_STREAM;

@Service
@Slf4j
public class ChatBotService {

    private final ChatBotDao chatBotDao;
    private final TeamMembershipDao teamMembershipDao;
    private final ChatbotOwnershipService chatbotOwnershipService;
    private final MongoTemplate mongoTemplate;

    private final RedisTemplate<String, String> redisTemplate;

    public ChatBotService(ChatBotDao chatBotDao, TeamMembershipDao teamMembershipDao,
                          ChatbotOwnershipService chatbotOwnershipService, MongoTemplate mongoTemplate,
                          RedisTemplate<String, String> redisTemplate) {
        this.chatBotDao = chatBotDao;
        this.teamMembershipDao = teamMembershipDao;
        this.chatbotOwnershipService = chatbotOwnershipService;
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Create a new chatbot with the given configuration
     */
    @Transactional
    public String createChatBot(ChatBotCreationRequest request, String createdBy) {
        log.info("Creating chatbot: Name={}, Title={}", request.getName(), request.getTitle());

        // Check if chatbot with same name already exists for this user
        if (chatBotDao.existsByNameAndCreatedBy(request.getName(), createdBy)) {
            throw new IllegalArgumentException("Chatbot with name '" + request.getName() + "' already exists");
        }

        // Convert Q&A pairs from DTO to Entity
        List<QAPair> qaPairs = null;
        if (request.getQaPairs() != null) {
            qaPairs = request.getQaPairs().stream()
                    .map(qa -> QAPair.builder()
                            .question(qa.getQuestion())
                            .answer(qa.getAnswer())
                            .build())
                    .toList();
        }

        // Save chatbot to MongoDB
        ChatBot chatbot = ChatBot.builder()
                .email(AuthUtils.getEmail())
                .name(request.getName())
                .title(request.getTitle())
                .hideName(request.getHideName())
                .instructions(request.getInstructions())
                .restrictToDataSource(request.getRestrictToDataSource())
                .fallbackMessage(request.getFallbackMessage())
                .greetingMessage(request.getGreetingMessage())
                .selectedDataSource(request.getSelectedDataSource())
                .width(request.getWidth())
                .height(request.getHeight())
                .model(request.getModel())
                // Widget theme fields
                .headerBackground(request.getHeaderBackground())
                .headerText(request.getHeaderText())
                .aiBackground(request.getAiBackground())
                .aiText(request.getAiText())
                .userBackground(request.getUserBackground())
                .userText(request.getUserText())
                .widgetPosition(request.getWidgetPosition())
                .aiAvatarUrl(request.getAiAvatar())
                .avatarFileId(request.getAvatarFileId())
                .hideMainBannerLogo(request.getHideMainBannerLogo())
                .qaPairs(qaPairs)
                .fileIds(request.getFileIds())
                .addedWebsites(request.getAddedWebsites())
                .addedTexts(request.getAddedTexts())
                .createdBy(createdBy)
                .createdAt(new Date())
                .updatedAt(new Date())
                .status("CREATED")
                .build();

        ChatBot saved = chatBotDao.save(chatbot);

        ChatBotTask chatBotTask = ChatBotTask.builder()
                .chatbotId(chatbot.getId())
                .fileIds(chatbot.getFileIds())
                .qaPairs(chatbot.getQaPairs())
                .addedTexts(chatbot.getAddedTexts())
                .addedWebsites(chatbot.getAddedWebsites())
                .build();

        chatBotTask = mongoTemplate.save(chatBotTask);

        postSaveEventTrigger(chatBotTask);

        log.info("Chatbot created successfully with ID: {}", saved.getId());

        return saved.getId();
    }

    /**
     * Update chatbot configuration
     */
    @Transactional
    public ChatBot updateChatBot(String id, ChatBotCreationRequest request) {
        log.info("Updating chatbot: {}", id);

        ChatBot existing = chatBotDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chatbot not found with ID: " + id));

        // Merge lists without duplicates
        List<String> mergedFileIds = mergeList(existing.getFileIds(), request.getFileIds());
        List<QAPair> mergedQaPairs = mergeList(existing.getQaPairs(), request.getQaPairs());
        List<String> mergedTexts = mergeList(existing.getAddedTexts(), request.getAddedTexts());
        List<String> mergedWebsites = mergeList(existing.getAddedWebsites(), request.getAddedWebsites());

        // Build updated ChatBot
        ChatBot updated = ChatBot.builder()
                .id(existing.getId())
                .email(existing.getEmail())
                .updatedAt(new Date())
                .status(existing.getStatus())
                .createdBy(existing.getCreatedBy())
                .createdAt(existing.getCreatedAt())
                .name(request.getName())
                .title(request.getTitle())
                .hideName(request.getHideName())
                .instructions(request.getInstructions())
                .restrictToDataSource(request.getRestrictToDataSource())
                .fallbackMessage(request.getFallbackMessage())
                .greetingMessage(request.getGreetingMessage())
                .selectedDataSource(request.getSelectedDataSource())
                .width(request.getWidth())
                .height(request.getHeight())
                .model(request.getModel())
                // Widget theme fields
                .headerBackground(request.getHeaderBackground())
                .headerText(request.getHeaderText())
                .aiBackground(request.getAiBackground())
                .aiText(request.getAiText())
                .userBackground(request.getUserBackground())
                .userText(request.getUserText())
                .widgetPosition(request.getWidgetPosition())
                .aiAvatarUrl(request.getAiAvatar())
                .avatarFileId(request.getAvatarFileId())
                .hideMainBannerLogo(request.getHideMainBannerLogo())

                // merged new + existing
                .fileIds(mergedFileIds)
                .qaPairs(mergedQaPairs)
                .addedTexts(mergedTexts)
                .addedWebsites(mergedWebsites)

                .build();

        updated = chatBotDao.save(updated);

        // Build ChatBotTask with merged data
        ChatBotTask chatBotTask = ChatBotTask.builder()
                .chatbotId(updated.getId())
                .fileIds(mergedFileIds)
                .qaPairs(mergedQaPairs)
                .addedTexts(mergedTexts)
                .addedWebsites(mergedWebsites)
                .build();

        chatBotTask = mongoTemplate.save(chatBotTask);

        postSaveEventTrigger(chatBotTask);

        log.info("Chatbot updated successfully: {}", id);

        return updated;
    }

    /**
     * Retrieve chatbot by ID
     */
    public ChatBot getChatBot(String id) {
        log.info("Retrieving chatbot: {}", id);
        return chatBotDao.findById(id).orElse(null);
    }

    /**
     * Retrieve getKnowledgeBaseList
     */
    public List<KnowledgeBase> getKnowledgeBaseList(String id) {
        log.info("Retrieving getKnowledgeBaseList: {}", id);

        return mongoTemplate.find(
                new Query().addCriteria(Criteria.where("chatbotId").is(id)),
                KnowledgeBase.class
        );
    }

    /**
     * Retrieve getChatBotFrom MessengerId
     */
    public ChatBot getChabotFromMessengerId(String messengerId) {
        log.info("Retrieving getChabot for messengerId: {}", messengerId);

        MessengerIntegration messengerIntegration = getMessengerIntegration(messengerId);

        if (messengerIntegration == null) {
            return null;
        }

        ChatBot chatBot = getChatBot(messengerIntegration.chatbotId());
        if (chatBot == null) {
            log.warn("Messenger integration {} points to missing chatbot {}", messengerId, messengerIntegration.chatbotId());
            return null;
        }
        chatBot.setMessengerIntegration(messengerIntegration);

        return chatBot;
    }

    /**
     * Retrieve getChatBotFrom MessengerId
     */
    public ChatBot getChabotFromPhoneNumberID(String phonenumberId) {
        log.info("Retrieving getChabot for whatsappId: {}", phonenumberId);

        WhatsAppIntegration whatsAppIntegration = getWhatsappIntegration(phonenumberId);

        if (whatsAppIntegration == null) {
            return null;
        }

        ChatBot chatBot = getChatBot(whatsAppIntegration.chatbotId());
        if (chatBot == null) {
            log.warn("WhatsApp integration {} points to missing chatbot {}", phonenumberId, whatsAppIntegration.chatbotId());
            return null;
        }
        chatBot.setWhatsAppIntegration(whatsAppIntegration);

        return chatBot;
    }

    public MessengerIntegration getMessengerIntegration(String messengerId) {
        MessengerIntegration messengerIntegration = mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("pageId").is(messengerId)),
                MessengerIntegration.class
        );
        return messengerIntegration;
    }

    public WhatsAppIntegration getWhatsappIntegration(String phoneNumberId) {
        WhatsAppIntegration whatsAppIntegration = mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("phoneNumberId").is(phoneNumberId)),
                WhatsAppIntegration.class
        );
        return whatsAppIntegration;
    }

    /**
     * Retrieve chatbot from Instagram Account ID
     */
    public ChatBot getChatbotFromInstagramAccountId(String instagramAccountId) {
        log.info("Retrieving chatbot for instagramAccountId: {}", instagramAccountId);

        InstagramIntegration instagramIntegration = getInstagramIntegration(instagramAccountId);

        if (instagramIntegration == null) {
            return null;
        }

        ChatBot chatBot = getChatBot(instagramIntegration.chatbotId());
        if (chatBot == null) {
            log.warn("Instagram integration {} points to missing chatbot {}", instagramAccountId, instagramIntegration.chatbotId());
            return null;
        }
        chatBot.setInstagramIntegration(instagramIntegration);

        return chatBot;
    }

    public InstagramIntegration getInstagramIntegration(String instagramAccountId) {
        InstagramIntegration instagramIntegration = mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("instagramAccountId").is(instagramAccountId)),
                InstagramIntegration.class
        );
        return instagramIntegration;
    }

    /**
     * Utility: merges without duplicates while supporting nulls
     */
    private <T> List<T> mergeList(List<T> existing, List<T> incoming) {
        Set<T> set = new LinkedHashSet<>();
        if (existing != null) set.addAll(existing);
        if (incoming != null) set.addAll(incoming);
        return new ArrayList<>(set);
    }

    /**
     * Delete chatbot
     */
    @Transactional
    public void deleteChatBot(String id) {
        log.info("Deleting chatbot: {}", id);

        if (!chatBotDao.existsById(id)) {
            throw new IllegalArgumentException("Chatbot not found with ID: " + id);
        }

        chatBotDao.deleteById(id);

        log.info("Chatbot deleted successfully: {}", id);
    }

    /**
     * Toggle chatbot status (ACTIVE / DISABLED)
     */
    @Transactional
    public ChatBot toggleChatBotStatus(String id, String status) {
        log.info("Toggling chatbot status: {} to {}", id, status);

        ChatBot chatBot = chatBotDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chatbot not found with ID: " + id));

        // Validate status
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("Invalid status. Must be 'ACTIVE' or 'DISABLED'");
        }

        chatBot.setStatus(status);
        chatBot.setUpdatedAt(new Date());

        ChatBot updated = chatBotDao.save(chatBot);

        log.info("Chatbot status updated successfully: {} -> {}", id, status);

        return updated;
    }

    /**
     * Get aggregated statistics for user's chatbots
     */
    public net.ai.chatbot.dto.aichatbot.ChatBotStatsResponse getChatBotStats(String userEmail) {
        log.info("Getting chatbot statistics for user: {}", userEmail);

        // Get all chatbots for user
        List<ChatBot> chatbots = getChatBotsByUser(userEmail);

        long totalChatbots = chatbots.size();
        long activeDomains = chatbots.stream()
                .filter(bot -> "ACTIVE".equals(bot.getStatus()))
                .count();

        // Get conversation and message counts for all chatbots
        long totalConversations = 0;
        long totalMessages = 0;

        for (ChatBot chatbot : chatbots) {
            // Count unique conversations using aggregation
            GroupOperation groupByConversation = Aggregation.group("conversationid");
            MatchOperation matchChatbot = Aggregation.match(
                    Criteria.where("chatbotId").is(chatbot.getId())
            );
            CountOperation count = Aggregation.count().as("total");

            Aggregation aggregation = Aggregation.newAggregation(
                    matchChatbot,
                    groupByConversation,
                    count
            );

            try {
                var result = mongoTemplate.aggregate(
                        aggregation,
                        "n8n_chat_session_histories",
                        org.bson.Document.class
                );
                
                long conversations = result.getMappedResults().isEmpty() ? 0 : 
                        ((Number) result.getMappedResults().get(0).get("total", 0)).longValue();

                // Count total messages for this chatbot
                long messages = mongoTemplate.count(
                        new Query().addCriteria(Criteria.where("chatbotId").is(chatbot.getId())),
                        "n8n_chat_session_histories"
                );

                totalConversations += conversations;
                totalMessages += messages;
                
                log.debug("Chatbot {}: {} conversations, {} messages", 
                        chatbot.getId(), conversations, messages);
                
            } catch (Exception e) {
                log.warn("Error counting stats for chatbot {}: {}", chatbot.getId(), e.getMessage());
            }
        }

        log.info("Total stats - Chatbots: {}, Conversations: {}, Messages: {}, Active: {}", 
                totalChatbots, totalConversations, totalMessages, activeDomains);

        return net.ai.chatbot.dto.aichatbot.ChatBotStatsResponse.builder()
                .totalChatbots(totalChatbots)
                .totalConversations(totalConversations)
                .totalMessages(totalMessages)
                .activeDomains(activeDomains)
                .build();
    }

    /**
     * Get chatbots list with per-chatbot statistics
     */
    public List<net.ai.chatbot.dto.aichatbot.ChatBotListItemResponse> getChatBotsWithStats(String userEmail) {
        log.info("Getting chatbots with statistics for user: {}", userEmail);

        List<ChatBot> chatbots = getChatBotsByUser(userEmail);

        return chatbots.stream()
                .map(chatbot -> {
                    // Count unique conversations using aggregation
                    long conversations = 0;
                    long messages = 0;
                    
                    try {
                        GroupOperation groupByConversation = Aggregation.group("conversationid");
                        MatchOperation matchChatbot = Aggregation.match(
                                Criteria.where("chatbotId").is(chatbot.getId())
                        );
                        CountOperation count = Aggregation.count().as("total");

                        Aggregation aggregation = Aggregation.newAggregation(
                                matchChatbot,
                                groupByConversation,
                                count
                        );

                        var result = mongoTemplate.aggregate(
                                aggregation,
                                "n8n_chat_session_histories",
                                org.bson.Document.class
                        );
                        
                        conversations = result.getMappedResults().isEmpty() ? 0 : 
                                ((Number) result.getMappedResults().get(0).get("total", 0)).longValue();

                        // Count total messages for this chatbot
                        messages = mongoTemplate.count(
                                new Query().addCriteria(Criteria.where("chatbotId").is(chatbot.getId())),
                                "n8n_chat_session_histories"
                        );
                        
                    } catch (Exception e) {
                        log.warn("Error counting stats for chatbot {}: {}", chatbot.getId(), e.getMessage());
                    }

                    return net.ai.chatbot.dto.aichatbot.ChatBotListItemResponse.builder()
                            .id(chatbot.getId())
                            .name(chatbot.getName())
                            .title(chatbot.getTitle())
                            .createdAt(chatbot.getCreatedAt())
                            .createdBy(chatbot.getCreatedBy())
                            .status(chatbot.getStatus())
                            .totalConversations(conversations)
                            .totalMessages(messages)
                            .canConfigure(chatbotOwnershipService.canConfigureChatbot(chatbot.getId(), userEmail))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Get statistics for a single chatbot
     */
    public net.ai.chatbot.dto.aichatbot.ChatBotStatsItemResponse getChatBotStatsById(String chatbotId) {
        log.info("Getting statistics for chatbot: {}", chatbotId);

        long conversations = 0;
        long messages = 0;

        try {
            // Count unique conversations using aggregation
            GroupOperation groupByConversation = Aggregation.group("conversationid");
            MatchOperation matchChatbot = Aggregation.match(
                    Criteria.where("chatbotId").is(chatbotId)
            );
            CountOperation count = Aggregation.count().as("total");

            Aggregation aggregation = Aggregation.newAggregation(
                    matchChatbot,
                    groupByConversation,
                    count
            );

            var result = mongoTemplate.aggregate(
                    aggregation,
                    "n8n_chat_session_histories",
                    org.bson.Document.class
            );

            conversations = result.getMappedResults().isEmpty() ? 0 :
                    ((Number) result.getMappedResults().get(0).get("total", 0)).longValue();

            // Count total messages
            messages = mongoTemplate.count(
                    new Query().addCriteria(Criteria.where("chatbotId").is(chatbotId)),
                    "n8n_chat_session_histories"
            );

            log.debug("Chatbot {} stats: {} conversations, {} messages",
                    chatbotId, conversations, messages);

        } catch (Exception e) {
            log.error("Error counting stats for chatbot {}: {}", chatbotId, e.getMessage());
        }

        return net.ai.chatbot.dto.aichatbot.ChatBotStatsItemResponse.builder()
                .chatbotId(chatbotId)
                .totalConversations(conversations)
                .totalMessages(messages)
                .build();
    }

    /*
     * Return first chat of every conversation for the given chatbot
     */
    public List<UserChatHistory> getChatConversationList(String chatbotId) {
        return getChatConversationList(chatbotId, null);
    }

    public List<UserChatHistory> getChatConversationList(String chatbotId, String email) {
        log.info("Getting All conversations: {}", chatbotId);

        Criteria criteria = Criteria.where("chatbotId").is(chatbotId);
        if (email != null && !email.isBlank()) {
            criteria = criteria.and("email").is(email);
        }

        MatchOperation match = Aggregation.match(criteria);

        // sort all records inside each conversation by createdAt
        SortOperation sort = Aggregation.sort(Sort.by(Sort.Direction.ASC, "createdAt"));

        // group by conversationid and extract first fields
        GroupOperation group = Aggregation.group("conversationid")
                .first("conversationid").as("conversationid")
                .first("userMessage").as("userMessage")
                .first("aiMessage").as("aiMessage")
                .first("createdAt").as("createdAt")
                .first("email").as("email")
                .first("mode").as("mode")
                .first("isAnonymous").as("isAnonymous");

        // select only required fields
        ProjectionOperation project = Aggregation.project("conversationid", "userMessage", "aiMessage", "createdAt", "email", "mode", "isAnonymous")
                .andExclude("_id");

        Aggregation aggregation = Aggregation.newAggregation(match, sort, group, project);

        return mongoTemplate.aggregate(
                aggregation,
                "n8n_chat_session_histories",
                UserChatHistory.class
        ).getMappedResults();
    }

    @Transactional
    public List<UserChatHistory> getChatHistory(String chatbotId, String conversationId) {
        return getChatHistory(chatbotId, conversationId, true);
    }

    @Transactional
    public List<UserChatHistory> getChatHistory(String chatbotId, String conversationId, boolean restrictToCallerEmail) {
        log.info("Getting All chats of the conversation: {}", conversationId);

        Criteria criteria = Criteria.where("chatbotId").is(chatbotId)
                .and("conversationid").is(conversationId);

        // Owners and admins read the whole thread. Other signed-in users only see rows saved with their email.
        String userEmail = AuthUtils.getEmail();
        if (restrictToCallerEmail && userEmail != null && !AuthUtils.isAdmin()) {
            criteria = criteria.and("email").is(userEmail);
            log.debug("Filtering chat history by caller email");
        }

        return mongoTemplate.find(new Query().addCriteria(criteria), UserChatHistory.class);
    }

    /**
     * Save admin reply to conversation
     * Used when admin/manager sends reply on behalf of chatbot
     */
    @Transactional
    public UserChatHistory saveAdminReply(String conversationId, String chatbotId, 
                                          String message, String adminEmail) {
        log.info("Saving admin reply to conversation: {}, chatbot: {}, admin: {}", 
                conversationId, chatbotId, adminEmail);
        
        // Get the conversation user's email from existing messages
        // This ensures the admin reply appears in the user's conversation history
        String conversationUserEmail = getConversationUserEmail(conversationId, chatbotId);
        
        if (conversationUserEmail == null) {
            log.warn("Could not find user email for conversation: {}, using admin email as fallback", conversationId);
            conversationUserEmail = adminEmail;
        }
        
        // Generate unique message ID
        String messageId = "msg_" + System.currentTimeMillis() + "_" + 
                          UUID.randomUUID().toString().substring(0, 8);
        
        // Create UserChatHistory record for admin reply
        UserChatHistory adminReply = UserChatHistory.builder()
                .id(messageId)
                .conversationid(conversationId)
                .chatbotId(chatbotId)
                .email(conversationUserEmail) // Use conversation user's email, not admin's
                .aiMessage(message)
                .userMessage(null) // No user message for admin replies
                .role("assistant")
                .senderType("admin_reply")
                .adminUserId(adminEmail) // Track who sent the reply
                .status("sent")
                .createdAt(java.time.Instant.now())
                .mode("admin")
                .isAnonymous(false)
                .build();
        
        // Save to MongoDB
        UserChatHistory saved = mongoTemplate.save(adminReply);
        
        log.info("Admin reply saved successfully: messageId={}, conversationUserEmail={}", 
                messageId, conversationUserEmail);
        return saved;
    }
    
    /**
     * Get the user's email from an existing conversation
     * Finds the first message in the conversation and returns the email field
     */
    private String getConversationUserEmail(String conversationId, String chatbotId) {
        try {
            Criteria criteria = Criteria.where("conversationid").is(conversationId)
                    .and("chatbotId").is(chatbotId);
            
            Query query = new Query(criteria);
            query.limit(1);
            query.with(Sort.by(Sort.Direction.ASC, "createdAt"));
            
            UserChatHistory firstMessage = mongoTemplate.findOne(query, UserChatHistory.class);
            
            if (firstMessage != null && firstMessage.getEmail() != null) {
                log.debug("Found conversation user email: {} for conversation: {}", 
                         firstMessage.getEmail(), conversationId);
                return firstMessage.getEmail();
            }
            
            log.warn("No messages found for conversation: {}, chatbot: {}", conversationId, chatbotId);
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching conversation user email: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Verify that conversation exists and belongs to the specified chatbot
     */
    public boolean verifyConversationOwnership(String conversationId, String chatbotId) {
        log.debug("Verifying conversation ownership: conversationId={}, chatbotId={}", 
                 conversationId, chatbotId);
        
        Criteria criteria = Criteria.where("conversationid").is(conversationId)
                .and("chatbotId").is(chatbotId);
        
        Query query = new Query(criteria);
        query.limit(1);
        
        boolean exists = mongoTemplate.exists(query, UserChatHistory.class);
        
        if (!exists) {
            log.warn("Conversation not found or does not belong to chatbot: conversationId={}, chatbotId={}", 
                    conversationId, chatbotId);
        }
        
        return exists;
    }

    /**
     * Get all chatbots owned by the user plus chatbots shared via team membership.
     */
    public List<ChatBot> getChatBotsByUser(String createdBy) {
        log.info("Retrieving chatbots for user: {}", createdBy);
        String u = TeamService.normalizeEmail(createdBy);
        LinkedHashMap<String, ChatBot> byId = new LinkedHashMap<>();
        for (ChatBot b : chatBotDao.findByCreatedByIgnoreCase(u)) {
            byId.put(b.getId(), b);
        }
        for (TeamMembership m : teamMembershipDao.findByMemberEmail(u)) {
            for (ChatBot b : chatBotDao.findByCreatedByIgnoreCase(m.getOwnerEmail())) {
                byId.putIfAbsent(b.getId(), b);
            }
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Event Trigger for post processing of chatbot creation
     */
    private void postSaveEventTrigger(ChatBotTask chatBotTask) {
        ObjectRecord<String, String> record = StreamRecords
                .newRecord()
                .ofObject(chatBotTask.getId())
                .withStreamKey(CHAT_BOT_CREATE_EVENT_STREAM);

        RecordId recordId = redisTemplate.opsForStream().add(record);

        log.info("redis event created for chatBotTask:{} chatbot: {}, redis eventId:{}, for user: {}", chatBotTask.getId(), chatBotTask.getChatbotId(), recordId, AuthUtils.getEmail());
    }
}

