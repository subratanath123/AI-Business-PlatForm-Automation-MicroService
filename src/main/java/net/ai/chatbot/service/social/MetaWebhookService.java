package net.ai.chatbot.service.social;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Service for managing Meta (Facebook/Instagram) webhook subscriptions.
 * Automatically subscribes pages to receive messages via webhooks.
 */
@Service
@Slf4j
public class MetaWebhookService {

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v18.0";
    
    private final WebClient webClient;
    
    @Value("${meta.webhook-url:}")
    private String webhookUrl;
    
    @Value("${meta.webhook-verify-token:}")
    private String webhookVerifyToken;

    public MetaWebhookService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(GRAPH_API_BASE).build();
    }

    /**
     * Subscribe a Facebook Page to receive Messenger webhook events.
     * This enables the page to receive messages at your n8n webhook.
     *
     * @param pageId The Facebook Page ID
     * @param pageAccessToken The Page Access Token
     * @return true if subscription successful
     */
    public boolean subscribePageToMessengerWebhook(String pageId, String pageAccessToken) {
        log.info("Subscribing page {} to Messenger webhooks", pageId);
        
        try {
            // Subscribe the page to the app's webhooks
            Map<String, Object> response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{pageId}/subscribed_apps")
                            .queryParam("subscribed_fields", "messages,messaging_postbacks,messaging_optins,message_deliveries,message_reads")
                            .queryParam("access_token", pageAccessToken)
                            .build(pageId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Successfully subscribed page {} to Messenger webhooks", pageId);
                return true;
            } else {
                log.warn("Failed to subscribe page {} to webhooks: {}", pageId, response);
                return false;
            }
        } catch (Exception e) {
            log.error("Error subscribing page {} to Messenger webhooks: {}", pageId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Subscribe a Facebook Page to receive Instagram webhook events.
     * This enables the connected Instagram account to receive DMs at your n8n webhook.
     *
     * @param pageId The Facebook Page ID (connected to Instagram)
     * @param pageAccessToken The Page Access Token with instagram_manage_messages permission
     * @return true if subscription successful
     */
    public boolean subscribePageToInstagramWebhook(String pageId, String pageAccessToken) {
        log.info("Subscribing page {} to Instagram webhooks", pageId);
        
        try {
            // Subscribe the page to Instagram messaging webhooks
            Map<String, Object> response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{pageId}/subscribed_apps")
                            .queryParam("subscribed_fields", "messages,messaging_postbacks")
                            .queryParam("access_token", pageAccessToken)
                            .build(pageId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Successfully subscribed page {} to Instagram webhooks", pageId);
                return true;
            } else {
                log.warn("Failed to subscribe page {} to Instagram webhooks: {}", pageId, response);
                return false;
            }
        } catch (Exception e) {
            log.error("Error subscribing page {} to Instagram webhooks: {}", pageId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get the Instagram Business Account ID connected to a Facebook Page.
     *
     * @param pageId The Facebook Page ID
     * @param pageAccessToken The Page Access Token
     * @return The Instagram Business Account ID, or null if not connected
     */
    public String getInstagramBusinessAccountId(String pageId, String pageAccessToken) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{pageId}")
                            .queryParam("fields", "instagram_business_account")
                            .queryParam("access_token", pageAccessToken)
                            .build(pageId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("instagram_business_account")) {
                Map<String, String> igAccount = (Map<String, String>) response.get("instagram_business_account");
                return igAccount.get("id");
            }
        } catch (Exception e) {
            log.error("Error getting Instagram Business Account for page {}: {}", pageId, e.getMessage());
        }
        return null;
    }

    /**
     * Get Instagram account details (username, profile picture, etc).
     *
     * @param instagramAccountId The Instagram Business Account ID
     * @param accessToken The Page Access Token
     * @return Map containing account details
     */
    public Map<String, Object> getInstagramAccountInfo(String instagramAccountId, String accessToken) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{igAccountId}")
                            .queryParam("fields", "id,username,name,profile_picture_url,followers_count,media_count")
                            .queryParam("access_token", accessToken)
                            .build(instagramAccountId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("Error getting Instagram account info: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Unsubscribe a page from webhook events.
     *
     * @param pageId The Facebook Page ID
     * @param pageAccessToken The Page Access Token
     * @return true if unsubscription successful
     */
    public boolean unsubscribePageFromWebhooks(String pageId, String pageAccessToken) {
        log.info("Unsubscribing page {} from webhooks", pageId);
        
        try {
            Map<String, Object> response = webClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{pageId}/subscribed_apps")
                            .queryParam("access_token", pageAccessToken)
                            .build(pageId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Successfully unsubscribed page {} from webhooks", pageId);
                return true;
            }
        } catch (Exception e) {
            log.error("Error unsubscribing page {} from webhooks: {}", pageId, e.getMessage());
        }
        return false;
    }

    /**
     * Check if webhooks are properly configured.
     */
    public boolean isWebhookConfigured() {
        return webhookUrl != null && !webhookUrl.isEmpty() 
            && webhookVerifyToken != null && !webhookVerifyToken.isEmpty();
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }
}
