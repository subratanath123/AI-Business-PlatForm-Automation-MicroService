package net.ai.chatbot.entity.social;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

/**
 * Connected social media account (Facebook, Twitter, LinkedIn, or Instagram).
 * Stored per userId - users can connect multiple accounts per platform.
 */
@Document(collection = "social_accounts")
@CompoundIndex(name = "userId_platform", def = "{'userId': 1, 'platform': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialAccount {

    @Id
    private String id;

    /**
     * Clerk user ID (JWT sub)
     */
    private String userId;

    /**
     * facebook | twitter | linkedin | instagram
     */
    private String platform;

    private Date connectedAt;

    // --- Facebook fields ---
    /**
     * Long-lived user access token (encrypted)
     */
    private String longLivedToken;
    private Long expiresIn;

    /**
     * Facebook pages - one login can have multiple pages
     */
    private List<FacebookPage> pages;

    // --- Twitter fields ---
    private String accessToken;   // encrypted
    private String refreshToken; // encrypted
    private String username;
    
    // --- LinkedIn fields ---
    /**
     * LinkedIn user ID (OpenID Connect sub claim)
     * Format: urn:li:person:AbCdEfGhIj
     */
    private String linkedInUserId;
    
    /**
     * Display name for UI
     */
    private String displayName;
    
    /**
     * LinkedIn email
     */
    private String email;
    
    /**
     * Profile picture URL
     */
    private String profilePicture;

    // --- Instagram fields ---
    /**
     * Instagram Business/Creator Account ID
     */
    private String instagramAccountId;
    
    /**
     * Instagram username (handle)
     */
    private String instagramUsername;
    
    /**
     * Connected Facebook Page ID (Instagram requires a linked FB page)
     */
    private String connectedFacebookPageId;
    
    /**
     * Instagram followers count (cached)
     */
    private Long instagramFollowers;
}
