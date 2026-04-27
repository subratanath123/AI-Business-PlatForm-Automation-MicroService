package net.ai.chatbot.controller.ebay;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.ebay.EbayDirectProductUpdateRequest;
import net.ai.chatbot.dto.ebay.EbayEnhanceProductsRequest;
import net.ai.chatbot.dto.ebay.EbayMarketplaceSwitchRequest;
import net.ai.chatbot.dto.ebay.EbayNotificationsToggleRequest;
import net.ai.chatbot.dto.ebay.EbayOAuthFinalizeRequest;
import net.ai.chatbot.dto.ebay.EbayOAuthInitRequest;
import net.ai.chatbot.dto.ebay.EbayProductDto;
import net.ai.chatbot.dto.ebay.EbayPushProductsRequest;
import net.ai.chatbot.dto.ebay.EbaySetDefaultsRequest;
import net.ai.chatbot.dto.ebay.EbayStoreSwitchRequest;
import net.ai.chatbot.entity.EbayIntegration;
import net.ai.chatbot.entity.EbayPendingConnection;
import net.ai.chatbot.entity.EbayProductEnhancementJob;
import net.ai.chatbot.service.ebay.EbayIntegrationService;
import net.ai.chatbot.service.ebay.EbayOAuthService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EbayIntegrationController {

    private final EbayIntegrationService ebayService;
    private final EbayOAuthService oauth;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ──────────────────────────────────────────────────────────────────────────
    // OAuth flow
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/ebay/oauth/init")
    public ResponseEntity<Map<String, Object>> initAuth(@RequestBody(required = false) EbayOAuthInitRequest request) {
        String userId = AuthUtils.getUserId();
        if (oauth.appId() == null || oauth.appId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "EBAY_APP_ID is not configured. Register an eBay developer app first."));
        }
        if (oauth.ruName() == null || oauth.ruName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "EBAY_RU_NAME is not configured. Add a RuName in your eBay developer dashboard."));
        }
        try {
            String env = request == null ? null : request.getEnvironment();
            EbayPendingConnection pending = ebayService.initAuth(userId, env);

            String authorizeUrl = oauth.authorizeHost(pending.getEnvironment())
                    + "/oauth2/authorize"
                    + "?client_id="     + enc(oauth.appId())
                    + "&response_type=" + enc("code")
                    + "&redirect_uri="  + enc(oauth.ruName())
                    + "&state="         + enc(pending.getState())
                    + "&scope="         + enc(EbayIntegrationService.OAUTH_SCOPES);

            Map<String, Object> resp = new HashMap<>();
            resp.put("state",        pending.getState());
            resp.put("environment",  pending.getEnvironment());
            resp.put("authorizeUrl", authorizeUrl);
            resp.put("ruName",       oauth.ruName());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("eBay auth init failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/oauth/finalize")
    public ResponseEntity<Map<String, Object>> finalizeAuth(@RequestBody EbayOAuthFinalizeRequest request) {
        String userId = AuthUtils.getUserId();
        if (request == null || request.getState() == null || request.getState().isBlank()
                || request.getCode() == null || request.getCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "state and code are required"));
        }
        try {
            EbayIntegration integration = ebayService.finalizeAuth(userId,
                    request.getState(), request.getCode(), request.getStoreName());
            return ResponseEntity.ok(integrationToMap(integration));
        } catch (Exception e) {
            log.error("eBay auth finalize failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Integration management
    // ──────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/v1/api/ebay/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@RequestParam(required = false) String sellerId) {
        String userId = AuthUtils.getUserId();
        try {
            if (sellerId != null && !sellerId.isBlank()) {
                ebayService.disconnectStore(userId, sellerId);
                return ResponseEntity.ok(Map.of("message", "Seller disconnected", "sellerId", sellerId));
            }
            ebayService.disconnectAllStores(userId);
            return ResponseEntity.ok(Map.of("message", "All eBay sellers disconnected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/v1/api/ebay/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        String userId = AuthUtils.getUserId();
        EbayIntegration integration = ebayService.getIntegration(userId);
        if (integration == null) return ResponseEntity.ok(Map.of("connected", false));
        return ResponseEntity.ok(integrationToMap(integration));
    }

    @GetMapping("/v1/api/ebay/stores")
    public ResponseEntity<List<Map<String, Object>>> listStores() {
        String userId = AuthUtils.getUserId();
        List<EbayIntegration> stores = ebayService.listStores(userId);
        return ResponseEntity.ok(stores.stream().map(this::integrationToMap).toList());
    }

    @PostMapping("/v1/api/ebay/stores/switch")
    public ResponseEntity<Map<String, Object>> switchStore(@RequestBody EbayStoreSwitchRequest body) {
        String userId = AuthUtils.getUserId();
        if (body == null || body.getSellerId() == null || body.getSellerId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sellerId is required"));
        }
        try {
            return ResponseEntity.ok(integrationToMap(ebayService.switchStore(userId, body.getSellerId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/marketplaces/switch")
    public ResponseEntity<Map<String, Object>> switchMarketplace(@RequestBody EbayMarketplaceSwitchRequest body) {
        String userId = AuthUtils.getUserId();
        if (body == null || body.getMarketplaceId() == null || body.getMarketplaceId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "marketplaceId is required"));
        }
        try {
            return ResponseEntity.ok(integrationToMap(ebayService.switchMarketplace(userId, body.getMarketplaceId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/defaults")
    public ResponseEntity<Map<String, Object>> setDefaults(@RequestBody EbaySetDefaultsRequest body) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(integrationToMap(ebayService.setStoreDefaults(userId,
                    body.getCategoryId(), body.getMerchantLocationKey(),
                    body.getFulfillmentPolicyId(), body.getPaymentPolicyId(), body.getReturnPolicyId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sync / upload / parse
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/ebay/sync")
    public ResponseEntity<Map<String, Object>> syncProducts(@RequestParam(defaultValue = "50") int limit) {
        String userId = AuthUtils.getUserId();
        try {
            EbayProductEnhancementJob job = ebayService.syncProductsFromStore(userId, limit);
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            log.error("eBay sync failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/upload")
    public ResponseEntity<Map<String, Object>> uploadProducts(@RequestBody EbayEnhanceProductsRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            EbayProductEnhancementJob job = ebayService.createUploadJob(userId, request.getProducts());
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            log.error("eBay upload job creation failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/parse")
    public ResponseEntity<Map<String, Object>> parseProducts(@RequestBody Map<String, String> request) {
        String content  = request.get("content");
        String fileName = request.get("fileName");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }
        try {
            List<EbayProductDto> products = ebayService.parseProductsWithAI(content, fileName);
            return ResponseEntity.ok(Map.of("products", products, "count", products.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/parse-image")
    public ResponseEntity<Map<String, Object>> parseProductsFromImages(@RequestBody Map<String, Object> request) {
        Object imagesObj = request.get("images");
        if (!(imagesObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "At least one image is required"));
        }
        final int MAX_IMAGES = 6;
        final int MAX_URL_LEN = 12 * 1024 * 1024;
        List<String> imageUrls = new ArrayList<>();
        for (Object o : rawList) {
            if (!(o instanceof String s) || s.isBlank()) continue;
            if (s.length() > MAX_URL_LEN) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "One of the images is too large. Please keep each image under 8 MB."));
            }
            imageUrls.add(s);
            if (imageUrls.size() >= MAX_IMAGES) break;
        }
        if (imageUrls.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No valid images supplied"));
        }
        try {
            List<EbayProductDto> products = ebayService.parseProductsFromImages(imageUrls);
            return ResponseEntity.ok(Map.of("products", products, "count", products.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AI enhancement + push
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/ebay/enhance/{jobId}")
    public ResponseEntity<Map<String, Object>> enhanceJob(@PathVariable String jobId) {
        String userId = AuthUtils.getUserId();
        try {
            ebayService.enhanceJobAsync(jobId, userId);
            return ResponseEntity.accepted().body(Map.of("message", "Enhancement started", "jobId", jobId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/push")
    public ResponseEntity<Map<String, Object>> pushProducts(@RequestBody EbayPushProductsRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            ebayService.pushProductsToEbay(userId, request.getJobId(), request.getProductIds());
            return ResponseEntity.ok(Map.of("message", "Products pushed to eBay successfully"));
        } catch (Exception e) {
            log.error("eBay push failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notifications
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/ebay/notifications/toggle")
    public ResponseEntity<Map<String, Object>> toggleNotifications(
            @RequestBody EbayNotificationsToggleRequest req) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(integrationToMap(ebayService.toggleNotifications(userId,
                    req.isEnabled(), req.getEndpointUrl(), req.getVerificationToken())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Public ingest endpoint for eBay platform notifications. eBay POSTs
     * a signed payload to this URL; we trust the caller because the
     * inbound signature / verification token handling is performed by
     * the eBay SDK before this endpoint is invoked. A misbehaving
     * caller will simply supply garbage payloads that fail parsing.
     */
    @PostMapping("/api/public/ebay/notify/{userId}")
    public ResponseEntity<Void> notify(@PathVariable String userId, @RequestBody String rawPayload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = OBJECT_MAPPER.readValue(rawPayload, Map.class);
            ebayService.handleNotification(userId, payload);
        } catch (Exception e) {
            log.error("Failed to process eBay notification for user {}: {}", userId, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Single-product direct edit (live eBay listing)
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/ebay/products/{sku}")
    public ResponseEntity<Map<String, Object>> getProductDetails(@PathVariable String sku) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(ebayService.getProductDetails(userId, sku));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/products/{sku}/enhance")
    public ResponseEntity<Map<String, Object>> enhanceSingleProduct(@PathVariable String sku) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(ebayService.enhanceSingleProductDirect(userId, sku));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/v1/api/ebay/products/{sku}")
    public ResponseEntity<Map<String, Object>> updateProductDirect(@PathVariable String sku,
                                                                   @RequestBody EbayDirectProductUpdateRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            ebayService.updateProductDirect(userId, sku, request);
            return ResponseEntity.ok(Map.of("message", "Listing updated successfully", "sku", sku));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Append an image URL to the live eBay listing for {@code sku}. */
    @PostMapping("/v1/api/ebay/products/{sku}/images")
    public ResponseEntity<Map<String, Object>> addProductImage(@PathVariable String sku,
                                                               @RequestBody Map<String, Object> body) {
        String userId = AuthUtils.getUserId();
        try {
            Object src = body == null ? null : body.get("src");
            List<String> images = ebayService.addProductImage(userId, sku,
                    src == null ? null : src.toString());
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove an image from the live eBay listing. The {@code imageId}
     * path variable is the URL the client received from {@code GET
     * /products/{sku}} (URL-encoded).
     */
    @DeleteMapping("/v1/api/ebay/products/{sku}/images/{imageId}")
    public ResponseEntity<Map<String, Object>> removeProductImage(@PathVariable String sku,
                                                                  @PathVariable String imageId) {
        String userId = AuthUtils.getUserId();
        try {
            List<String> images = ebayService.removeProductImage(userId, sku, imageId);
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Drafts
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/ebay/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> getDraft(@PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(ebayService.getDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/v1/api/ebay/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> updateDraft(@PathVariable String jobId, @PathVariable String localId,
                                                           @RequestBody EbayDirectProductUpdateRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(ebayService.updateDraft(userId, jobId, localId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/drafts/{jobId}/{localId}/enhance")
    public ResponseEntity<Map<String, Object>> enhanceDraft(@PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(ebayService.enhanceDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/ebay/drafts/{jobId}/{localId}/publish")
    public ResponseEntity<Map<String, Object>> publishDraft(@PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(ebayService.publishDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/v1/api/ebay/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> deleteDraft(@PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            ebayService.deleteDraft(userId, jobId, localId);
            return ResponseEntity.ok(Map.of("message", "Draft deleted", "jobId", jobId, "localId", localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Jobs
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/ebay/jobs")
    public ResponseEntity<List<EbayProductEnhancementJob>> getJobs() {
        return ResponseEntity.ok(ebayService.getJobs(AuthUtils.getUserId()));
    }

    @GetMapping("/v1/api/ebay/jobs/{jobId}")
    public ResponseEntity<EbayProductEnhancementJob> getJob(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(ebayService.getJob(AuthUtils.getUserId(), jobId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, Object> integrationToMap(EbayIntegration i) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",                          i.getId());
        map.put("sellerId",                    i.getSellerId());
        map.put("storeName",                   i.getStoreName());
        map.put("environment",                 i.getEnvironment());
        map.put("connected",                   i.isConnected());
        map.put("active",                      i.isActive());
        map.put("notificationsEnabled",        i.isNotificationsEnabled());
        map.put("availableMarketplaceIds",     i.getAvailableMarketplaceIds() == null
                ? new ArrayList<>() : i.getAvailableMarketplaceIds());
        map.put("activeMarketplaceId",         i.getActiveMarketplaceId());
        map.put("defaultCategoryId",           i.getDefaultCategoryId());
        map.put("defaultMerchantLocationKey",  i.getDefaultMerchantLocationKey());
        map.put("defaultFulfillmentPolicyId",  i.getDefaultFulfillmentPolicyId());
        map.put("defaultPaymentPolicyId",      i.getDefaultPaymentPolicyId());
        map.put("defaultReturnPolicyId",       i.getDefaultReturnPolicyId());
        map.put("createdAt",                   i.getCreatedAt());
        map.put("updatedAt",                   i.getUpdatedAt());
        return map;
    }

    private Map<String, Object> jobToMap(EbayProductEnhancementJob j) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",               j.getId());
        map.put("status",           j.getStatus());
        map.put("platform",         j.getPlatform());
        map.put("source",           j.getSource());
        map.put("marketplaceId",    j.getMarketplaceId());
        map.put("rawProducts",      j.getRawProducts());
        map.put("enhancedProducts", j.getEnhancedProducts());
        map.put("createdAt",        j.getCreatedAt());
        return map;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
