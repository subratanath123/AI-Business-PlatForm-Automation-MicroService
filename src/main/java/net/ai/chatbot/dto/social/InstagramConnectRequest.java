package net.ai.chatbot.dto.social;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for connecting an Instagram Business/Creator account.
 * 
 * Instagram OAuth flow:
 * 1. User authorizes via Facebook OAuth (Instagram uses Facebook's OAuth)
 * 2. Get page access token for the connected Facebook Page
 * 3. Use page token to get Instagram Business Account ID
 * 4. Store Instagram account info with page token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstagramConnectRequest {

    /**
     * Page access token from Facebook OAuth (for the page connected to Instagram)
     */
    @NotBlank(message = "Page access token is required")
    private String pageAccessToken;

    /**
     * Facebook Page ID that is connected to the Instagram account
     */
    @NotBlank(message = "Facebook Page ID is required")
    private String facebookPageId;

    /**
     * Facebook Page name (for display)
     */
    private String facebookPageName;

    /**
     * Instagram Business/Creator Account ID
     */
    @NotBlank(message = "Instagram Account ID is required")
    private String instagramAccountId;

    /**
     * Instagram username (handle without @)
     */
    @NotBlank(message = "Instagram username is required")
    private String instagramUsername;

    /**
     * Instagram display name
     */
    private String instagramDisplayName;

    /**
     * Profile picture URL
     */
    private String profilePictureUrl;

    /**
     * Followers count (cached)
     */
    private Long followersCount;

    /**
     * Token expiry in seconds
     */
    private Long expiresIn;

    /**
     * Whether to auto-subscribe the page to webhooks for receiving DMs
     */
    private Boolean autoSubscribeWebhook;
}
