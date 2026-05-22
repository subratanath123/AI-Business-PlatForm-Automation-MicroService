package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document(collection = "chatbots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBot {

    @Id
    private String id;

    private String title;
    private String email;
    private String name;
    private Boolean hideName;

    private String instructions;
    private Boolean restrictToDataSource;
    private String fallbackMessage;
    private String greetingMessage;
    private String selectedDataSource;
    private String width;
    private String height;

    // AI Model
    private String model;

    // Widget Theme
    private String headerBackground;
    private String headerText;
    private String aiBackground;
    private String aiText;
    private String userBackground;
    private String userText;
    private String widgetPosition;
    private String aiAvatarUrl;
    private String avatarFileId;
    private Boolean hideMainBannerLogo;

    // Q&A pairs
    private List<QAPair> qaPairs;

    // Uploaded files (file names or IDs)
    private List<String> fileIds;

    // Website URLs to scrape
    private List<String> addedWebsites;

    // Plain text content
    private List<String> addedTexts;

    // Metadata
    private String createdBy;
    private Date createdAt;
    private Date updatedAt;
    private String status; // CREATED, TRAINING, COMPLETED, FAILED

    // Inner class for Q&A pairs
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QAPair {
        private String question;
        private String answer;
    }

    @Transient
    private MessengerIntegration messengerIntegration;

    @Transient
    private WhatsAppIntegration whatsAppIntegration;

    @Transient
    private InstagramIntegration instagramIntegration;

    /** Set on API responses only: whether the current user may edit this chatbot (not persisted). */
    @Transient
    private Boolean canConfigure;

    public String getChatbotknowledgebasecollection() {
        return "jade-ai-knowledgebase-" + id;
    }

    public String getVectorIndexName() {
        return "jade-ai-vector-index-" + id;
    }

}

