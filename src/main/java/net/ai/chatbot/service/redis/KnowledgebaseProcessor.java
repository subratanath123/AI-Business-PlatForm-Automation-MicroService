package net.ai.chatbot.service.redis;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.SecureFileUpload;
import net.ai.chatbot.entity.ChatBot;
import net.ai.chatbot.entity.ChatBotTask;
import net.ai.chatbot.entity.KnowledgeBase;
import net.ai.chatbot.enums.KnowledgeBaseType;
import net.ai.chatbot.service.aiprocess.KnowledgebaseIngestionService;
import net.ai.chatbot.service.mongodb.MongodbVectorService;
import net.ai.chatbot.service.training.PlaywrightWebsiteCrawler;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.ai.chatbot.service.training.HtmlSanitizer.*;

@Slf4j
public class KnowledgebaseProcessor implements StreamListener<String, ObjectRecord<String, String>> {

    private final MongoTemplate mongoTemplate;
    private final MongodbVectorService mongodbVectorService;
    private final KnowledgebaseIngestionService knowledgebaseIngestionService;

    public KnowledgebaseProcessor(MongoTemplate mongoTemplate,
                                  MongodbVectorService mongodbVectorService,
                                  KnowledgebaseIngestionService knowledgebaseIngestionService) {
        this.mongoTemplate = mongoTemplate;
        this.mongodbVectorService = mongodbVectorService;
        this.knowledgebaseIngestionService = knowledgebaseIngestionService;
    }

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        log.info("Message is consuming for website crawl event {}", record.getId());

        ChatBotTask chatBotTask = getChatbotTask(record);
        if (chatBotTask == null) return;

        ChatBot chatBot = getChatbot(chatBotTask.getChatbotId());
        if (chatBot == null) return;

        String knowledgebaseCollectionName = mongodbVectorService.getKnowledgebaseCollectionName(chatBot.getId());
//        String knowledgebaseCollectionName = "test-db";
        mongodbVectorService.createMongodbCollection(knowledgebaseCollectionName);

        String knowledgebaseVectorIndexName = mongodbVectorService.getKnowledgebaseVectorIndexName(chatBot.getId());
//        String knowledgebaseVectorIndexName = "test-vector-index";
        mongodbVectorService.createVectorIndex(
                knowledgebaseCollectionName,
                "embedding",
                knowledgebaseVectorIndexName,
                1536,
                "cosine"
        );

        //Training PDF Files
        if (Objects.nonNull(chatBotTask.getFileIds()) && !chatBotTask.getFileIds().isEmpty()) {
            getFileList(chatBotTask.getFileIds())
                    .forEach(secureFileUpload -> {
                        try {
                        int chunks = knowledgebaseIngestionService.ingestFile(
                                secureFileUpload.getData(),
                                secureFileUpload.getFileName(),
                                secureFileUpload.getEmail(),
                                knowledgebaseCollectionName,
                                secureFileUpload.getContentType()
                        );
                        if (chunks == 0) {
                            log.warn("No text indexed from file {}", secureFileUpload.getFileName());
                            return;
                        }

                        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                                .knowledgeOf(secureFileUpload.getFileName())
                                .chatbotId(chatBot.getId())
                                .knowledgeType(KnowledgeBaseType.PDF)
                                .created(new Date().toInstant())
                                .createdBy(chatBot.getEmail())
                                .build();

                        mongoTemplate.save(knowledgeBase);
                        } catch (Exception e) {
                            log.error("Failed to index file {}: {}", secureFileUpload.getFileName(), e.getMessage());
                        }
                    });
        }

        String knowledgebase = "";

        if (Objects.nonNull(chatBotTask.getQaPairs()) && !chatBotTask.getQaPairs().isEmpty()) {
            knowledgebase += chatBotTask.getQaPairs()
                    .stream()
                    .map(qaPair -> "If user Question is: " + qaPair.getQuestion() + ", Your Answer should be: " + qaPair.getAnswer())
                    .collect(Collectors.joining(";"));

            KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                    .knowledgeOf("QA PAIR")
                    .chatbotId(chatBot.getId())
                    .knowledgeType(KnowledgeBaseType.QA)
                    .created(new Date().toInstant())
                    .createdBy(chatBot.getEmail())
                    .build();

            mongoTemplate.save(knowledgeBase);
        }

        if (Objects.nonNull(chatBotTask.getAddedTexts()) && !chatBotTask.getAddedTexts().isEmpty()) {
            knowledgebase += "Another Knowledge base List: " + String.join(";", chatBotTask.getAddedTexts());
        }

        //Training Textual Knowledegebase
        if (StringUtils.hasLength(knowledgebase)) {
            try {
            knowledgebaseIngestionService.ingestText(
                    knowledgebase,
                    chatBot.getEmail(),
                    knowledgebaseCollectionName,
                    "other knowledge base",
                    "textual-knowledge"
            );

            KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                    .knowledgeOf("Textual Data")
                    .chatbotId(chatBot.getId())
                    .knowledgeType(KnowledgeBaseType.TEXT)
                    .created(new Date().toInstant())
                    .createdBy(chatBot.getEmail())
                    .build();

            mongoTemplate.save(knowledgeBase);
            } catch (Exception e) {
                log.error("Failed to index textual knowledge for chatbot {}: {}", chatBot.getId(), e.getMessage());
            }
        }

        if (Objects.nonNull(chatBotTask.getAddedWebsites())
                && !chatBotTask.getAddedWebsites().isEmpty()) {

            chatBotTask.getAddedWebsites()
                    .forEach(websiteUrl -> crawlWebsite(record, chatBot, websiteUrl, knowledgebaseCollectionName, knowledgebaseVectorIndexName));
        }

    }

    private void crawlWebsite(ObjectRecord<String, String> record,
                              ChatBot chatBot,
                              String websiteUrl,
                              String knowledgebaseCollectionName,
                              String knowledgebaseVectorIndexName) {
        try {
            log.info("Starting Playwright crawl for website: {}", websiteUrl);
            PlaywrightWebsiteCrawler.crawl(websiteUrl,
                    chatBot.getEmail(),
                    15,
                    1000,
                    scrappedData -> {
                        try {
                        knowledgebaseIngestionService.ingestText(
                                mapToSemanticText(extractStructuredContent(scrappedData.html(), scrappedData.url())),
                                chatBot.getEmail(),
                                knowledgebaseCollectionName,
                                "other knowledgebase",
                                scrappedData.url()
                        );

                        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                                .knowledgeOf(scrappedData.url())
                                .chatbotId(chatBot.getId())
                                .knowledgeType(KnowledgeBaseType.WEBSITE)
                                .created(new Date().toInstant())
                                .createdBy("system")
                                .build();

                        mongoTemplate.save(knowledgeBase);
                        } catch (Exception ex) {
                            log.error("Failed to index page {}: {}", scrappedData.url(), ex.getMessage());
                        }
                    }

            );
            log.info("Successfully completed Playwright crawl for website: {}", websiteUrl);
        } catch (Exception e) {
            log.error("Message is failed to process for website crawl event {}: {}", record.getId(), e.getMessage(), e);
        }
    }

    private ChatBot getChatbot(String chatbotId) {
        return mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("id").is(chatbotId)),
                ChatBot.class
        );
    }

    private ChatBotTask getChatbotTask(ObjectRecord<String, String> record) {
        String chatbotTaskId = record.getValue();

        return mongoTemplate.findOne(
                new Query().addCriteria(Criteria.where("id").is(chatbotTaskId)),
                ChatBotTask.class
        );
    }

    private List<SecureFileUpload> getFileList(List<String> fileIdList) {
        return mongoTemplate.find(
                new Query().addCriteria(Criteria.where("id").in(fileIdList)),
                SecureFileUpload.class
        );
    }
}