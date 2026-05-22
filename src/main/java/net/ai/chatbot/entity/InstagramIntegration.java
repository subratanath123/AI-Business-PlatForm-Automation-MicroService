package net.ai.chatbot.entity;

import lombok.Builder;
import nonapi.io.github.classgraph.json.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Instagram Business Account integration for chatbot DM handling.
 * Uses Instagram Messaging API (part of Meta Graph API).
 * 
 * Required Meta permissions:
 * - instagram_basic
 * - instagram_manage_messages
 * - pages_manage_metadata (for webhook setup)
 */
@Builder
@Document(collection = "instagramIntegration")
public record InstagramIntegration(
        @Id
        String id,
        String chatbotId,
        String accountName,
        String instagramAccountId,
        String instagramUsername,
        String connectedPageId,
        String accessToken,
        String webhookVerifyToken,
        Boolean enabled
) {
}
