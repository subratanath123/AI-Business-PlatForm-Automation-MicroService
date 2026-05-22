package net.ai.chatbot.service.social;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.SocialAccountDao;
import net.ai.chatbot.dao.SocialPostDao;
import net.ai.chatbot.dto.social.*;
import net.ai.chatbot.entity.social.FacebookPage;
import net.ai.chatbot.entity.social.SocialAccount;
import net.ai.chatbot.entity.social.SocialPost;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SocialAccountService {

    private final SocialAccountDao socialAccountDao;
    private final SocialPostDao socialPostDao;
    private final EncryptionUtils encryptionUtils;

    /**
     * Connect Facebook account - always add new (do not overwrite).
     * One Facebook login can contain multiple pages.
     */
    @Transactional
    public FacebookConnectResponse connectFacebook(String userId, FacebookConnectRequest request) {
        List<FacebookPage> pages = new ArrayList<>();
        if (request.getPages() != null) {
            for (FacebookConnectRequest.FacebookPageDto dto : request.getPages()) {
                pages.add(FacebookPage.builder()
                        .pageId(dto.getPageId())
                        .pageName(dto.getPageName())
                        .pageAccessToken(encryptionUtils.encrypt(dto.getPageAccessToken()))
                        .build());
            }
        }

        SocialAccount account = SocialAccount.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("facebook")
                .connectedAt(new java.util.Date())
                .longLivedToken(encryptionUtils.encrypt(request.getLongLivedToken()))
                .expiresIn(request.getExpiresIn())
                .pages(pages)
                .build();

        socialAccountDao.save(account);
        log.info("Facebook account connected for user {}: {} pages", userId, pages.size());

        return FacebookConnectResponse.builder()
                .success(true)
                .accountId(account.getId())
                .platform("facebook")
                .pagesCount(pages.size())
                .build();
    }

    /**
     * Connect Twitter account - always add new.
     */
    @Transactional
    public TwitterConnectResponse connectTwitter(String userId, TwitterConnectRequest request) {
        SocialAccount account = SocialAccount.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("twitter")
                .connectedAt(new java.util.Date())
                .accessToken(encryptionUtils.encrypt(request.getAccessToken()))
                .refreshToken(request.getRefreshToken() != null ? encryptionUtils.encrypt(request.getRefreshToken()) : null)
                .username(request.getUsername())
                .build();

        socialAccountDao.save(account);
        log.info("Twitter account connected for user {}: @{}", userId, request.getUsername());

        return TwitterConnectResponse.builder()
                .success(true)
                .accountId(account.getId())
                .platform("twitter")
                .build();
    }

    /**
     * Connect LinkedIn account - always add new.
     * LinkedIn access tokens last ~60 days (Sign In + Share products).
     * No automatic refresh without Marketing Developer Platform.
     */
    @Transactional
    public LinkedInConnectResponse connectLinkedIn(String userId, LinkedInConnectRequest request) {
        SocialAccount account = SocialAccount.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("linkedin")
                .connectedAt(new java.util.Date())
                .accessToken(encryptionUtils.encrypt(request.getAccessToken()))
                .refreshToken(request.getRefreshToken() != null ? encryptionUtils.encrypt(request.getRefreshToken()) : null)
                .expiresIn(request.getExpiresIn())
                .linkedInUserId(request.getLinkedInUserId())
                .displayName(request.getDisplayName())
                .email(request.getEmail())
                .profilePicture(request.getProfilePicture())
                .build();

        socialAccountDao.save(account);
        log.info("LinkedIn account connected for user {}: {} ({})", userId, request.getDisplayName(), request.getLinkedInUserId());

        return LinkedInConnectResponse.builder()
                .success(true)
                .accountId(account.getId())
                .platform("linkedin")
                .build();
    }

    /**
     * Connect Instagram Business/Creator account.
     * Instagram requires a linked Facebook Page for API access.
     */
    @Transactional
    public InstagramConnectResponse connectInstagram(String userId, InstagramConnectRequest request) {
        SocialAccount account = SocialAccount.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("instagram")
                .connectedAt(new java.util.Date())
                .accessToken(encryptionUtils.encrypt(request.getPageAccessToken()))
                .expiresIn(request.getExpiresIn())
                .instagramAccountId(request.getInstagramAccountId())
                .instagramUsername(request.getInstagramUsername())
                .displayName(request.getInstagramDisplayName())
                .profilePicture(request.getProfilePictureUrl())
                .connectedFacebookPageId(request.getFacebookPageId())
                .instagramFollowers(request.getFollowersCount())
                .build();

        socialAccountDao.save(account);
        log.info("Instagram account connected for user {}: @{} (ID: {})", 
                userId, request.getInstagramUsername(), request.getInstagramAccountId());

        return InstagramConnectResponse.builder()
                .success(true)
                .accountId(account.getId())
                .instagramAccountId(request.getInstagramAccountId())
                .instagramUsername(request.getInstagramUsername())
                .message("Instagram account connected successfully")
                .build();
    }

    /**
     * List all connected accounts for user.
     */
    public ListAccountsResponse listAccounts(String userId) {
        List<SocialAccount> accounts = socialAccountDao.findByUserId(userId);
        List<SocialAccountResponse> responses = accounts.stream()
                .map(this::toAccountResponse)
                .collect(Collectors.toList());

        return ListAccountsResponse.builder()
                .accounts(responses)
                .build();
    }

    /**
     * List posting targets (for Create Post dropdown).
     * Facebook: targetId = accountId:pageId
     * Twitter: targetId = accountId
     * LinkedIn: targetId = accountId
     */
    public ListTargetsResponse listTargets(String userId, String platform) {
        List<SocialAccount> accounts = platform != null && !platform.isBlank()
                ? socialAccountDao.findByUserIdAndPlatform(userId, platform)
                : socialAccountDao.findByUserId(userId);

        List<SocialTargetResponse> targets = new ArrayList<>();
        for (SocialAccount acc : accounts) {
            if ("facebook".equals(acc.getPlatform()) && acc.getPages() != null) {
                for (FacebookPage page : acc.getPages()) {
                    targets.add(SocialTargetResponse.builder()
                            .targetId(acc.getId() + ":" + page.getPageId())
                            .accountId(acc.getId())
                            .platform("facebook")
                            .displayName("Facebook - " + page.getPageName())
                            .pageId(page.getPageId())
                            .pageName(page.getPageName())
                            .build());
                }
            } else if ("twitter".equals(acc.getPlatform())) {
                targets.add(SocialTargetResponse.builder()
                        .targetId(acc.getId())
                        .accountId(acc.getId())
                        .platform("twitter")
                        .displayName("X (Twitter) - @" + acc.getUsername())
                        .username(acc.getUsername())
                        .build());
            } else if ("linkedin".equals(acc.getPlatform())) {
                targets.add(SocialTargetResponse.builder()
                        .targetId(acc.getId())
                        .accountId(acc.getId())
                        .platform("linkedin")
                        .displayName("LinkedIn - " + acc.getDisplayName())
                        .build());
            } else if ("instagram".equals(acc.getPlatform())) {
                targets.add(SocialTargetResponse.builder()
                        .targetId(acc.getId())
                        .accountId(acc.getId())
                        .platform("instagram")
                        .displayName("Instagram - @" + acc.getInstagramUsername())
                        .instagramAccountId(acc.getInstagramAccountId())
                        .instagramUsername(acc.getInstagramUsername())
                        .profilePicture(acc.getProfilePicture())
                        .build());
            }
        }

        return ListTargetsResponse.builder()
                .targets(targets)
                .build();
    }

    /**
     * Disconnect single account.
     */
    @Transactional
    public boolean disconnect(String userId, String accountId) {
        if (!socialAccountDao.existsByIdAndUserId(accountId, userId)) {
            return false;
        }
        socialAccountDao.deleteById(accountId);
        log.info("Social account {} disconnected for user {}", accountId, userId);
        return true;
    }

    /**
     * Schedule post (or publish immediately).
     * 
     * Strategy:
     * - Facebook scheduled: Uses native API (published by controller)
     * - Twitter scheduled: Saved to DB, published by cron job
     * - Both immediate: Published by controller
     */
    @Transactional
    public SchedulePostResponse schedulePost(String userId, SchedulePostRequest request) {
        List<String> targetIds = request.getTargetIds();
        if (targetIds == null || targetIds.isEmpty()) {
            throw new IllegalArgumentException("targetIds is required");
        }

        // Validate: must have either content or media
        String content = request.getContent();
        List<net.ai.chatbot.dto.social.MediaItem> media = request.getMedia();
        
        if ((content == null || content.trim().isEmpty()) && 
            (media == null || media.isEmpty())) {
            throw new IllegalArgumentException("At least one of 'content' or 'media' is required");
        }

        boolean isImmediate = request.isImmediate();
        Instant scheduledAt = request.getScheduledAt();
        
        if (!isImmediate && scheduledAt == null) {
            throw new IllegalArgumentException("scheduledAt is required when immediate=false");
        }
        if (isImmediate) {
            scheduledAt = Instant.now();
        }

        // Determine if any Facebook targets exist
        boolean hasFacebookTargets = targetIds.stream().anyMatch(id -> id.contains(":"));
        
        String status;
        if (isImmediate) {
            status = "pending_publish"; // Will be published by controller
        } else if (hasFacebookTargets) {
            // Facebook uses native scheduling
            status = "pending_publish"; // Will be published by controller
        } else {
            // Twitter needs cron job
            status = "scheduled";
        }

        SocialPost post = SocialPost.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .targetIds(targetIds)
                .content(content)
                .media(media)
                .status(status)
                .scheduledAt(scheduledAt)
                .publishedAt(null)
                .createdAt(Instant.now())
                .build();

        socialPostDao.save(post);
        
        log.info("Post {} saved with status: {}", post.getId(), status);

        return SchedulePostResponse.builder()
                .success(true)
                .postId(post.getId())
                .status(status)
                .scheduledAt(scheduledAt)
                .build();
    }

    /**
     * Get posts by date range for calendar view.
     * Filters by user, date range, and optionally by platform/status.
     */
    public GetPostsResponse getPostsByDateRange(String userId, GetPostsRequest request) {
        Instant startDate = request.getStartDate();
        Instant endDate = request.getEndDate();
        String status = request.getStatus();
        
        List<SocialPost> posts;
        
        if (status != null && !status.isBlank()) {
            posts = socialPostDao.findByUserIdAndStatusAndScheduledAtBetweenOrderByScheduledAtAsc(
                    userId, status, startDate, endDate);
        } else {
            posts = socialPostDao.findByUserIdAndScheduledAtBetweenOrderByScheduledAtAsc(
                    userId, startDate, endDate);
        }
        
        // Filter by platform if specified
        String platform = request.getPlatform();
        if (platform != null && !platform.isBlank()) {
            posts = posts.stream()
                    .filter(post -> {
                        // Check if any target matches the platform
                        return post.getTargetIds().stream()
                                .anyMatch(targetId -> isPlatformMatch(targetId, platform));
                    })
                    .toList();
        }
        
        // Convert to response DTOs with target info
        List<SocialPostResponse> responses = posts.stream()
                .map(post -> toPostResponse(post, userId))
                .toList();
        
        return GetPostsResponse.builder()
                .posts(responses)
                .totalCount(responses.size())
                .build();
    }
    
    /**
     * Check if targetId matches platform.
     */
    private boolean isPlatformMatch(String targetId, String platform) {
        if ("facebook".equals(platform)) {
            return targetId.contains(":"); // Facebook format: accountId:pageId
        } else if ("twitter".equals(platform) || "linkedin".equals(platform)) {
            return !targetId.contains(":"); // Twitter/LinkedIn format: accountId
        }
        return false;
    }
    
    /**
     * Convert SocialPost to response DTO with target display info.
     */
    private SocialPostResponse toPostResponse(SocialPost post, String userId) {
        List<SocialPostResponse.TargetInfo> targets = post.getTargetIds().stream()
                .map(targetId -> {
                    try {
                        TokenResolutionResponse token = resolveToken(userId, targetId);
                        String displayName;
                        if ("facebook".equals(token.getPlatform())) {
                            // Get page name from account
                            displayName = "Facebook - " + getPageName(targetId);
                        } else if ("twitter".equals(token.getPlatform())) {
                            displayName = "X (Twitter) - @" + token.getUsername();
                        } else if ("linkedin".equals(token.getPlatform())) {
                            displayName = "LinkedIn - " + token.getDisplayName();
                        } else {
                            displayName = "Unknown Platform";
                        }
                        
                        return SocialPostResponse.TargetInfo.builder()
                                .targetId(targetId)
                                .platform(token.getPlatform())
                                .displayName(displayName)
                                .build();
                    } catch (Exception e) {
                        log.warn("Could not resolve target {}: {}", targetId, e.getMessage());
                        return SocialPostResponse.TargetInfo.builder()
                                .targetId(targetId)
                                .platform("unknown")
                                .displayName("Unknown Target")
                                .build();
                    }
                })
                .toList();
        
        return SocialPostResponse.builder()
                .postId(post.getId())
                .userId(post.getUserId())
                .targetIds(post.getTargetIds())
                .content(post.getContent())
                .media(post.getMedia())
                .status(post.getStatus())
                .scheduledAt(post.getScheduledAt())
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .targets(targets)
                .build();
    }
    
    /**
     * Get Facebook page name from targetId.
     */
    private String getPageName(String targetId) {
        if (!targetId.contains(":")) return "Unknown Page";
        String[] parts = targetId.split(":", 2);
        String accountId = parts[0];
        String pageId = parts[1];
        
        return socialAccountDao.findById(accountId)
                .filter(acc -> "facebook".equals(acc.getPlatform()) && acc.getPages() != null)
                .flatMap(acc -> acc.getPages().stream()
                        .filter(p -> pageId.equals(p.getPageId()))
                        .findFirst()
                        .map(FacebookPage::getPageName))
                .orElse("Unknown Page");
    }

    /**
     * Internal: resolve token for a target (worker/scheduler).
     * Verifies the target belongs to the user.
     */
    public TokenResolutionResponse resolveToken(String userId, String targetId) {
        if (targetId.contains(":")) {
            // Facebook: accountId:pageId
            String[] parts = targetId.split(":", 2);
            String accountId = parts[0];
            String pageId = parts[1];
            return socialAccountDao.findByIdAndUserId(accountId, userId)
                    .filter(acc -> "facebook".equals(acc.getPlatform()) && acc.getPages() != null)
                    .flatMap(acc -> acc.getPages().stream()
                            .filter(p -> pageId.equals(p.getPageId()))
                            .findFirst()
                            .map(p -> TokenResolutionResponse.builder()
                                    .platform("facebook")
                                    .pageAccessToken(encryptionUtils.decrypt(p.getPageAccessToken()))
                                    .pageId(p.getPageId())
                                    .build()))
                    .orElseThrow(() -> new IllegalArgumentException("Target not found: " + targetId));
        } else {
            // Twitter or LinkedIn: accountId
            return socialAccountDao.findByIdAndUserId(targetId, userId)
                    .map(acc -> {
                        if ("twitter".equals(acc.getPlatform())) {
                            return TokenResolutionResponse.builder()
                                    .platform("twitter")
                                    .accessToken(encryptionUtils.decrypt(acc.getAccessToken()))
                                    .username(acc.getUsername())
                                    .build();
                        } else if ("linkedin".equals(acc.getPlatform())) {
                            return TokenResolutionResponse.builder()
                                    .platform("linkedin")
                                    .accessToken(encryptionUtils.decrypt(acc.getAccessToken()))
                                    .linkedInUserId(acc.getLinkedInUserId())
                                    .displayName(acc.getDisplayName())
                                    .build();
                        } else {
                            throw new IllegalArgumentException("Unsupported platform: " + acc.getPlatform());
                        }
                    })
                    .orElseThrow(() -> new IllegalArgumentException("Target not found: " + targetId));
        }
    }

    private SocialAccountResponse toAccountResponse(SocialAccount acc) {
        List<SocialAccountResponse.PageInfo> pages = null;
        if (acc.getPages() != null) {
            pages = acc.getPages().stream()
                    .map(p -> SocialAccountResponse.PageInfo.builder()
                            .pageId(p.getPageId())
                            .pageName(p.getPageName())
                            .build())
                    .collect(Collectors.toList());
        }

        SocialAccountResponse.SocialAccountResponseBuilder builder = SocialAccountResponse.builder()
                .accountId(acc.getId())
                .platform(acc.getPlatform())
                .connectedAt(acc.getConnectedAt() != null ? acc.getConnectedAt().toInstant() : null)
                .pages(pages)
                .username(acc.getUsername());

        // Add Instagram-specific fields
        if ("instagram".equals(acc.getPlatform())) {
            builder.instagramAccountId(acc.getInstagramAccountId())
                   .instagramUsername(acc.getInstagramUsername())
                   .displayName(acc.getDisplayName())
                   .profilePicture(acc.getProfilePicture())
                   .followersCount(acc.getInstagramFollowers());
        }

        // Add LinkedIn-specific fields
        if ("linkedin".equals(acc.getPlatform())) {
            builder.displayName(acc.getDisplayName())
                   .profilePicture(acc.getProfilePicture());
        }

        return builder.build();
    }
}
