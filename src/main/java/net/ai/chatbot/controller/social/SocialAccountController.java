package net.ai.chatbot.controller.social;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.social.*;
import net.ai.chatbot.service.social.SocialAccountService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Social Media Suite API - Connect Facebook/Twitter/LinkedIn/Instagram, list accounts, schedule posts.
 * Base URL: https://subratapc.net
 * All endpoints require: Authorization: Bearer &lt;clerk_jwt&gt;
 * userId resolved from JWT sub
 */
@RestController
@RequestMapping("/v1/api/social-accounts")
@RequiredArgsConstructor
@Slf4j
public class SocialAccountController {

    private final SocialAccountService socialAccountService;

    /**
     * Connect Instagram Business/Creator Account
     * POST /v1/api/social-accounts/instagram
     * 
     * Instagram requires:
     * - A Facebook Page connected to the Instagram Business/Creator account
     * - Page access token with instagram_basic and instagram_content_publish permissions
     */
    @PostMapping("/instagram")
    public ResponseEntity<InstagramConnectResponse> connectInstagram(
            @Valid @RequestBody InstagramConnectRequest request) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(socialAccountService.connectInstagram(userId, request));
    }

    /**
     * 1) Connect Facebook Account (store long-lived token + pages)
     * POST /v1/api/social-accounts/facebook
     */
    @PostMapping("/facebook")
    public ResponseEntity<FacebookConnectResponse> connectFacebook(
            @Valid @RequestBody FacebookConnectRequest request) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(socialAccountService.connectFacebook(userId, request));
    }

    /**
     * 2) Connect Twitter/X Account
     * POST /v1/api/social-accounts/twitter
     */
    @PostMapping("/twitter")
    public ResponseEntity<TwitterConnectResponse> connectTwitter(
            @Valid @RequestBody TwitterConnectRequest request) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(socialAccountService.connectTwitter(userId, request));
    }

    /**
     * 3) Connect LinkedIn Account
     * POST /v1/api/social-accounts/linkedin
     */
    @PostMapping("/linkedin")
    public ResponseEntity<LinkedInConnectResponse> connectLinkedIn(
            @Valid @RequestBody LinkedInConnectRequest request) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(socialAccountService.connectLinkedIn(userId, request));
    }

    /**
     * 4) List Connected Accounts (My Accounts page)
     * GET /v1/api/social-accounts
     */
    @GetMapping
    public ResponseEntity<ListAccountsResponse> listAccounts() {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(socialAccountService.listAccounts(userId));
    }

    /**
     * 5) List Posting Targets (dropdown in Create Post)
     * GET /v1/api/social-accounts/targets?platform=facebook|twitter|linkedin|instagram
     */
    @GetMapping("/targets")
    public ResponseEntity<ListTargetsResponse> listTargets(
            @RequestParam(required = false) String platform) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(socialAccountService.listTargets(userId, platform));
    }

    /**
     * 6) Disconnect Single Account
     * DELETE /v1/api/social-accounts/{accountId}
     */
    @DeleteMapping("/{accountId}")
    public ResponseEntity<DisconnectResponse> disconnect(@PathVariable String accountId) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        boolean success = socialAccountService.disconnect(userId, accountId);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new DisconnectResponse(true));
    }

    /**
     * 7) Internal Token Resolution for Worker/Scheduler
     * POST /v1/api/social-accounts/targets/token
     */
    @PostMapping("/targets/token")
    public ResponseEntity<TokenResolutionResponse> resolveToken(
            @Valid @RequestBody TokenResolutionRequest request) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(socialAccountService.resolveToken(userId, request.getTargetId()));
    }
}
