package net.ai.chatbot.service.social.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.social.MediaItem;
import net.ai.chatbot.service.AttachmentStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;

/**
 * Instagram Graph API publisher for Business/Creator accounts.
 * 
 * Instagram API limitations:
 * - Can only post images and videos (no text-only posts)
 * - Images: JPEG format, max 8MB
 * - Videos: MP4 format, 3-60 seconds, max 100MB
 * - Carousels: 2-10 items
 * - Reels: 3-90 seconds
 * - Stories: 15 seconds max for images, 60 seconds for videos
 * 
 * Required permissions:
 * - instagram_basic
 * - instagram_content_publish
 * - pages_read_engagement (for linked Facebook Page)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InstagramPublisher {

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v18.0";
    
    private final WebClient.Builder webClientBuilder;
    private final AttachmentStorageService attachmentStorageService;

    /**
     * Publish a single image or video post to Instagram.
     *
     * @param instagramAccountId  Instagram Business/Creator account ID
     * @param accessToken         Page access token (from connected Facebook Page)
     * @param caption             Post caption (up to 2200 characters)
     * @param media               Media items (images/videos)
     * @param userId              User ID (for downloading media)
     * @return Instagram media ID
     */
    public String publishSingleMedia(String instagramAccountId, String accessToken, String caption,
                                      List<MediaItem> media, String userId) {
        log.info("Publishing single media to Instagram account: {}", instagramAccountId);
        
        if (media == null || media.isEmpty()) {
            throw new IllegalArgumentException("Instagram requires at least one image or video");
        }

        MediaItem firstMedia = media.get(0);
        String mediaType = determineMediaType(firstMedia.getMimeType());
        String mediaUrl = resolveMediaUrl(firstMedia, userId);

        if (mediaUrl == null) {
            throw new IllegalStateException("Failed to resolve media URL");
        }

        try {
            String containerId = createMediaContainer(instagramAccountId, accessToken, caption, 
                    mediaUrl, mediaType, false);
            
            return publishContainer(instagramAccountId, accessToken, containerId);
        } catch (Exception e) {
            log.error("Failed to publish to Instagram: {}", e.getMessage(), e);
            throw new RuntimeException("Instagram publish failed: " + e.getMessage(), e);
        }
    }

    /**
     * Publish a carousel (multiple images/videos) to Instagram.
     * Carousels support 2-10 items.
     */
    public String publishCarousel(String instagramAccountId, String accessToken, String caption,
                                   List<MediaItem> media, String userId) {
        log.info("Publishing carousel to Instagram account: {} with {} items", 
                instagramAccountId, media.size());
        
        if (media == null || media.size() < 2) {
            throw new IllegalArgumentException("Carousel requires at least 2 items");
        }
        if (media.size() > 10) {
            throw new IllegalArgumentException("Carousel supports maximum 10 items");
        }

        try {
            List<String> childContainerIds = new ArrayList<>();
            
            for (MediaItem item : media) {
                String mediaUrl = resolveMediaUrl(item, userId);
                if (mediaUrl == null) continue;
                
                String childId = createMediaContainer(instagramAccountId, accessToken, null,
                        mediaUrl, determineMediaType(item.getMimeType()), true);
                childContainerIds.add(childId);
            }

            if (childContainerIds.isEmpty()) {
                throw new IllegalStateException("No valid media items for carousel");
            }

            String carouselContainerId = createCarouselContainer(instagramAccountId, accessToken, 
                    caption, childContainerIds);
            
            return publishContainer(instagramAccountId, accessToken, carouselContainerId);
        } catch (Exception e) {
            log.error("Failed to publish carousel to Instagram: {}", e.getMessage(), e);
            throw new RuntimeException("Instagram carousel publish failed: " + e.getMessage(), e);
        }
    }

    /**
     * Publish a Reel (short-form video) to Instagram.
     * Reels: 3-90 seconds, 9:16 aspect ratio recommended.
     */
    public String publishReel(String instagramAccountId, String accessToken, String caption,
                               MediaItem videoMedia, String userId) {
        log.info("Publishing Reel to Instagram account: {}", instagramAccountId);
        
        if (videoMedia == null || !"video".equalsIgnoreCase(determineMediaType(videoMedia.getMimeType()))) {
            throw new IllegalArgumentException("Reel requires a video");
        }

        String videoUrl = resolveMediaUrl(videoMedia, userId);
        if (videoUrl == null) {
            throw new IllegalStateException("Failed to resolve video URL");
        }

        try {
            String containerId = createReelContainer(instagramAccountId, accessToken, caption, videoUrl);
            return publishContainer(instagramAccountId, accessToken, containerId);
        } catch (Exception e) {
            log.error("Failed to publish Reel to Instagram: {}", e.getMessage(), e);
            throw new RuntimeException("Instagram Reel publish failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create a media container (first step in Instagram publishing).
     */
    private String createMediaContainer(String igAccountId, String accessToken, String caption,
                                         String mediaUrl, String mediaType, boolean isCarouselItem) {
        WebClient client = webClientBuilder.baseUrl(GRAPH_API_BASE).build();
        
        Map<String, Object> body = new HashMap<>();
        body.put("access_token", accessToken);
        
        if ("video".equalsIgnoreCase(mediaType)) {
            body.put("video_url", mediaUrl);
            body.put("media_type", "VIDEO");
        } else {
            body.put("image_url", mediaUrl);
        }
        
        if (isCarouselItem) {
            body.put("is_carousel_item", true);
        } else if (caption != null && !caption.trim().isEmpty()) {
            body.put("caption", caption);
        }

        Map<String, Object> response = client.post()
                .uri("/{igAccountId}/media", igAccountId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("id")) {
            throw new RuntimeException("Failed to create Instagram media container");
        }

        String containerId = (String) response.get("id");
        log.info("Created Instagram media container: {}", containerId);
        
        if ("video".equalsIgnoreCase(mediaType)) {
            waitForVideoProcessing(containerId, accessToken);
        }
        
        return containerId;
    }

    /**
     * Create a carousel container with child items.
     */
    private String createCarouselContainer(String igAccountId, String accessToken, String caption,
                                            List<String> childIds) {
        WebClient client = webClientBuilder.baseUrl(GRAPH_API_BASE).build();
        
        Map<String, Object> body = new HashMap<>();
        body.put("access_token", accessToken);
        body.put("media_type", "CAROUSEL");
        body.put("children", String.join(",", childIds));
        
        if (caption != null && !caption.trim().isEmpty()) {
            body.put("caption", caption);
        }

        Map<String, Object> response = client.post()
                .uri("/{igAccountId}/media", igAccountId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("id")) {
            throw new RuntimeException("Failed to create Instagram carousel container");
        }

        String containerId = (String) response.get("id");
        log.info("Created Instagram carousel container: {}", containerId);
        return containerId;
    }

    /**
     * Create a Reel container.
     */
    private String createReelContainer(String igAccountId, String accessToken, String caption,
                                        String videoUrl) {
        WebClient client = webClientBuilder.baseUrl(GRAPH_API_BASE).build();
        
        Map<String, Object> body = new HashMap<>();
        body.put("access_token", accessToken);
        body.put("media_type", "REELS");
        body.put("video_url", videoUrl);
        
        if (caption != null && !caption.trim().isEmpty()) {
            body.put("caption", caption);
        }

        Map<String, Object> response = client.post()
                .uri("/{igAccountId}/media", igAccountId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("id")) {
            throw new RuntimeException("Failed to create Instagram Reel container");
        }

        String containerId = (String) response.get("id");
        log.info("Created Instagram Reel container: {}", containerId);
        
        waitForVideoProcessing(containerId, accessToken);
        
        return containerId;
    }

    /**
     * Publish the media container (second step).
     */
    private String publishContainer(String igAccountId, String accessToken, String containerId) {
        WebClient client = webClientBuilder.baseUrl(GRAPH_API_BASE).build();
        
        Map<String, Object> body = new HashMap<>();
        body.put("access_token", accessToken);
        body.put("creation_id", containerId);

        Map<String, Object> response = client.post()
                .uri("/{igAccountId}/media_publish", igAccountId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("id")) {
            throw new RuntimeException("Failed to publish Instagram media");
        }

        String mediaId = (String) response.get("id");
        log.info("Successfully published to Instagram, media ID: {}", mediaId);
        return mediaId;
    }

    /**
     * Wait for video processing to complete (Instagram processes videos asynchronously).
     */
    private void waitForVideoProcessing(String containerId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(GRAPH_API_BASE).build();
        
        int maxAttempts = 30;
        int waitMs = 2000;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Thread.sleep(waitMs);
                
                Map<String, Object> status = client.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/{containerId}")
                                .queryParam("fields", "status_code")
                                .queryParam("access_token", accessToken)
                                .build(containerId))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (status != null) {
                    String statusCode = (String) status.get("status_code");
                    log.debug("Video processing status: {}", statusCode);
                    
                    if ("FINISHED".equals(statusCode)) {
                        log.info("Video processing completed for container: {}", containerId);
                        return;
                    }
                    if ("ERROR".equals(statusCode)) {
                        throw new RuntimeException("Video processing failed");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Video processing interrupted", e);
            }
        }
        
        throw new RuntimeException("Video processing timeout");
    }

    /**
     * Resolve media URL (either direct URL or download from storage).
     */
    private String resolveMediaUrl(MediaItem mediaItem, String userId) {
        if (mediaItem.getMediaUrl() != null && !mediaItem.getMediaUrl().isEmpty()) {
            return mediaItem.getMediaUrl();
        }
        
        log.warn("Instagram requires publicly accessible media URLs. " +
                "Media must be uploaded to a public CDN first.");
        return null;
    }

    /**
     * Determine media type (image or video) from MIME type.
     */
    private String determineMediaType(String mimeType) {
        if (mimeType == null) {
            return "image";
        }
        String lower = mimeType.toLowerCase();
        if (lower.startsWith("video/")) {
            return "video";
        }
        return "image";
    }

    /**
     * Get Instagram Business Account ID from a Facebook Page.
     * Used during OAuth to discover connected Instagram accounts.
     */
    public String getInstagramBusinessAccountId(String pageId, String pageAccessToken) {
        WebClient client = webClientBuilder.baseUrl(GRAPH_API_BASE).build();
        
        try {
            Map<String, Object> response = client.get()
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
            log.error("Failed to get Instagram Business Account for page {}: {}", pageId, e.getMessage());
        }
        
        return null;
    }

    /**
     * Get Instagram account info (username, profile picture, etc).
     */
    public Map<String, Object> getInstagramAccountInfo(String igAccountId, String accessToken) {
        WebClient client = webClientBuilder.baseUrl(GRAPH_API_BASE).build();
        
        try {
            return client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{igAccountId}")
                            .queryParam("fields", "username,name,profile_picture_url,followers_count,media_count")
                            .queryParam("access_token", accessToken)
                            .build(igAccountId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to get Instagram account info: {}", e.getMessage());
            return null;
        }
    }
}
