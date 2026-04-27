package net.ai.chatbot.controller.amazon;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.amazon.AmazonDirectProductUpdateRequest;
import net.ai.chatbot.dto.amazon.AmazonEnhanceProductsRequest;
import net.ai.chatbot.dto.amazon.AmazonMarketplaceSwitchRequest;
import net.ai.chatbot.dto.amazon.AmazonOAuthFinalizeRequest;
import net.ai.chatbot.dto.amazon.AmazonOAuthInitRequest;
import net.ai.chatbot.dto.amazon.AmazonProductDto;
import net.ai.chatbot.dto.amazon.AmazonPushProductsRequest;
import net.ai.chatbot.dto.amazon.AmazonSetDefaultProductTypeRequest;
import net.ai.chatbot.dto.amazon.AmazonSqsToggleRequest;
import net.ai.chatbot.dto.amazon.AmazonStoreSwitchRequest;
import net.ai.chatbot.entity.AmazonIntegration;
import net.ai.chatbot.entity.AmazonPendingConnection;
import net.ai.chatbot.entity.AmazonProductEnhancementJob;
import net.ai.chatbot.service.amazon.AmazonIntegrationService;
import net.ai.chatbot.service.amazon.AmazonSpApiService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Locale;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AmazonIntegrationController {

    private final AmazonIntegrationService amazonService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${amazon.lwa.app-id:${AMAZON_LWA_APP_ID:}}")
    private String lwaAppId;

    // ──────────────────────────────────────────────────────────────────────────
    // LWA OAuth flow
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/amazon/oauth/init")
    public ResponseEntity<Map<String, Object>> initAuth(@RequestBody AmazonOAuthInitRequest request) {
        String userId = AuthUtils.getUserId();
        if (lwaAppId == null || lwaAppId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "AMAZON_LWA_APP_ID is not configured. Register an SP-API app in Seller Central first."));
        }
        try {
            String region = request == null ? "NA" : request.getRegion();
            AmazonPendingConnection pending = amazonService.initAuth(userId, region);

            String state       = pending.getState();
            String redirectUri = frontendBaseUrl + "/auth/amazon/callback";
            String authorizeUrl = AmazonSpApiService.lwaAuthorizeHost(pending.getRegion())
                    + "/apps/authorize/consent"
                    + "?application_id=" + enc(lwaAppId)
                    + "&state="          + enc(state)
                    + "&redirect_uri="   + enc(redirectUri);

            Map<String, Object> resp = new HashMap<>();
            resp.put("state",         state);
            resp.put("region",        pending.getRegion());
            resp.put("authorizeUrl",  authorizeUrl);
            resp.put("redirectUri",   redirectUri);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Amazon auth init failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/oauth/finalize")
    public ResponseEntity<Map<String, Object>> finalizeAuth(@RequestBody AmazonOAuthFinalizeRequest request) {
        String userId = AuthUtils.getUserId();
        if (request == null || request.getState() == null || request.getState().isBlank()
                || request.getSpapiOauthCode() == null || request.getSpapiOauthCode().isBlank()
                || request.getSellingPartnerId() == null || request.getSellingPartnerId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "state, spapiOauthCode and sellingPartnerId are required"));
        }
        try {
            String redirectUri = frontendBaseUrl + "/auth/amazon/callback";
            AmazonIntegration integration = amazonService.finalizeAuth(userId,
                    request.getState(), request.getSpapiOauthCode(),
                    request.getSellingPartnerId(), request.getStoreName(),
                    request.getDefaultProductType(), redirectUri);
            return ResponseEntity.ok(integrationToMap(integration));
        } catch (Exception e) {
            log.error("Amazon auth finalize failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Integration management
    // ──────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/v1/api/amazon/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectStore(
            @RequestParam(required = false) String sellerId) {
        String userId = AuthUtils.getUserId();
        try {
            if (sellerId != null && !sellerId.isBlank()) {
                amazonService.disconnectStore(userId, sellerId);
                return ResponseEntity.ok(Map.of("message", "Seller disconnected", "sellerId", sellerId));
            }
            amazonService.disconnectAllStores(userId);
            return ResponseEntity.ok(Map.of("message", "All Amazon sellers disconnected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/v1/api/amazon/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        String userId = AuthUtils.getUserId();
        AmazonIntegration integration = amazonService.getIntegration(userId);
        if (integration == null) return ResponseEntity.ok(Map.of("connected", false));
        return ResponseEntity.ok(integrationToMap(integration));
    }

    @GetMapping("/v1/api/amazon/stores")
    public ResponseEntity<List<Map<String, Object>>> listStores() {
        String userId = AuthUtils.getUserId();
        List<AmazonIntegration> stores = amazonService.listStores(userId);
        return ResponseEntity.ok(stores.stream().map(this::integrationToMap).toList());
    }

    @PostMapping("/v1/api/amazon/stores/switch")
    public ResponseEntity<Map<String, Object>> switchStore(@RequestBody AmazonStoreSwitchRequest body) {
        String userId = AuthUtils.getUserId();
        if (body == null || body.getSellerId() == null || body.getSellerId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sellerId is required"));
        }
        try {
            return ResponseEntity.ok(integrationToMap(
                    amazonService.switchStore(userId, body.getSellerId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/marketplaces/switch")
    public ResponseEntity<Map<String, Object>> switchMarketplace(
            @RequestBody AmazonMarketplaceSwitchRequest body) {
        String userId = AuthUtils.getUserId();
        if (body == null || body.getMarketplaceId() == null || body.getMarketplaceId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "marketplaceId is required"));
        }
        try {
            return ResponseEntity.ok(integrationToMap(
                    amazonService.switchMarketplace(userId, body.getMarketplaceId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/product-type")
    public ResponseEntity<Map<String, Object>> setDefaultProductType(
            @RequestBody AmazonSetDefaultProductTypeRequest body) {
        String userId = AuthUtils.getUserId();
        if (body == null || body.getProductType() == null || body.getProductType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "productType is required"));
        }
        try {
            return ResponseEntity.ok(integrationToMap(
                    amazonService.setDefaultProductType(userId, body.getProductType())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Product sync / upload / parse
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/amazon/sync")
    public ResponseEntity<Map<String, Object>> syncProducts(
            @RequestParam(defaultValue = "50") int limit) {
        String userId = AuthUtils.getUserId();
        try {
            AmazonProductEnhancementJob job = amazonService.syncProductsFromStore(userId, limit);
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            log.error("Amazon sync failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/upload")
    public ResponseEntity<Map<String, Object>> uploadProducts(
            @RequestBody AmazonEnhanceProductsRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            AmazonProductEnhancementJob job = amazonService.createUploadJob(userId, request.getProducts());
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            log.error("Amazon upload job creation failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/parse")
    public ResponseEntity<Map<String, Object>> parseProducts(@RequestBody Map<String, String> request) {
        String content  = request.get("content");
        String fileName = request.get("fileName");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }
        try {
            List<AmazonProductDto> products = amazonService.parseProductsWithAI(content, fileName);
            return ResponseEntity.ok(Map.of("products", products, "count", products.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/parse-image")
    public ResponseEntity<Map<String, Object>> parseProductsFromImages(
            @RequestBody Map<String, Object> request) {
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
            List<AmazonProductDto> products = amazonService.parseProductsFromImages(imageUrls);
            return ResponseEntity.ok(Map.of("products", products, "count", products.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AI enhancement + push
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/amazon/enhance/{jobId}")
    public ResponseEntity<Map<String, Object>> enhanceJob(@PathVariable String jobId) {
        String userId = AuthUtils.getUserId();
        try {
            amazonService.enhanceJobAsync(jobId, userId);
            return ResponseEntity.accepted().body(Map.of("message", "Enhancement started", "jobId", jobId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/push")
    public ResponseEntity<Map<String, Object>> pushProducts(@RequestBody AmazonPushProductsRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            amazonService.pushProductsToAmazon(userId, request.getJobId(), request.getProductIds());
            return ResponseEntity.ok(Map.of("message", "Products pushed to Amazon successfully"));
        } catch (Exception e) {
            log.error("Amazon push failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SQS notifications (Amazon's webhook equivalent)
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/amazon/notifications/toggle")
    public ResponseEntity<Map<String, Object>> toggleSqs(@RequestBody AmazonSqsToggleRequest req) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(integrationToMap(
                    amazonService.toggleSqs(userId, req.isEnabled(), req.getSqsQueueArn())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Public ingest endpoint for SQS messages. A remote worker (or AWS
     * Lambda) pulls notifications off the queue and POSTs the raw
     * payload here, keyed by the user id. We trust the caller because
     * the queue is private to our AWS account — callers outside that
     * boundary will simply supply garbage payloads that fail parsing.
     */
    @PostMapping("/api/public/amazon/sqs-ingest/{userId}")
    public ResponseEntity<Void> sqsIngest(@PathVariable String userId,
                                          @RequestBody String rawPayload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = OBJECT_MAPPER.readValue(rawPayload, Map.class);
            amazonService.handleNotification(userId, payload);
        } catch (Exception e) {
            log.error("Failed to process Amazon SQS payload for user {}: {}", userId, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Single-product direct edit (live Amazon listing)
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/amazon/products/{sku}")
    public ResponseEntity<Map<String, Object>> getProductDetails(@PathVariable String sku) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(amazonService.getProductDetails(userId, sku));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/products/{sku}/enhance")
    public ResponseEntity<Map<String, Object>> enhanceSingleProduct(@PathVariable String sku) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(amazonService.enhanceSingleProductDirect(userId, sku));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/v1/api/amazon/products/{sku}")
    public ResponseEntity<Map<String, Object>> updateProductDirect(
            @PathVariable String sku,
            @RequestBody AmazonDirectProductUpdateRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            amazonService.updateProductDirect(userId, sku, request);
            return ResponseEntity.ok(Map.of("message", "Listing updated successfully", "sku", sku));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Append an image URL to the live Amazon listing for {@code sku}.
     * Body: {@code { "src": "https://...", "alt": "optional alt text" }}.
     */
    @PostMapping("/v1/api/amazon/products/{sku}/images")
    public ResponseEntity<Map<String, Object>> addProductImage(
            @PathVariable String sku,
            @RequestBody Map<String, Object> body) {
        String userId = AuthUtils.getUserId();
        try {
            Object src = body == null ? null : body.get("src");
            List<String> images = amazonService.addProductImage(userId, sku,
                    src == null ? null : src.toString());
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove an image from the live Amazon listing. The {@code imageId}
     * path variable is the URL the client received from {@code GET
     * /products/{sku}} (URL-encoded).
     */
    @DeleteMapping("/v1/api/amazon/products/{sku}/images/{imageId}")
    public ResponseEntity<Map<String, Object>> removeProductImage(
            @PathVariable String sku, @PathVariable String imageId) {
        String userId = AuthUtils.getUserId();
        try {
            List<String> images = amazonService.removeProductImage(userId, sku, imageId);
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Drafts
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/amazon/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> getDraft(
            @PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(amazonService.getDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/v1/api/amazon/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> updateDraft(
            @PathVariable String jobId, @PathVariable String localId,
            @RequestBody AmazonDirectProductUpdateRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(amazonService.updateDraft(userId, jobId, localId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/drafts/{jobId}/{localId}/enhance")
    public ResponseEntity<Map<String, Object>> enhanceDraft(
            @PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(amazonService.enhanceDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/amazon/drafts/{jobId}/{localId}/publish")
    public ResponseEntity<Map<String, Object>> publishDraft(
            @PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(amazonService.publishDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/v1/api/amazon/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> deleteDraft(
            @PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            amazonService.deleteDraft(userId, jobId, localId);
            return ResponseEntity.ok(Map.of("message", "Draft deleted", "jobId", jobId, "localId", localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Jobs
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/amazon/jobs")
    public ResponseEntity<List<AmazonProductEnhancementJob>> getJobs() {
        return ResponseEntity.ok(amazonService.getJobs(AuthUtils.getUserId()));
    }

    @GetMapping("/v1/api/amazon/jobs/{jobId}")
    public ResponseEntity<AmazonProductEnhancementJob> getJob(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(amazonService.getJob(AuthUtils.getUserId(), jobId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, Object> integrationToMap(AmazonIntegration i) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",                       i.getId());
        map.put("sellerId",                 i.getSellerId());
        map.put("storeName",                i.getStoreName());
        map.put("region",                   i.getRegion());
        map.put("connected",                i.isConnected());
        map.put("active",                   i.isActive());
        map.put("sqsEnabled",               i.isSqsEnabled());
        map.put("defaultProductType",       i.getDefaultProductType());
        map.put("availableMarketplaceIds",  i.getAvailableMarketplaceIds() == null
                ? new ArrayList<>() : i.getAvailableMarketplaceIds());
        map.put("activeMarketplaceId",      i.getActiveMarketplaceId());
        map.put("createdAt",                i.getCreatedAt());
        map.put("updatedAt",                i.getUpdatedAt());
        return map;
    }

    private Map<String, Object> jobToMap(AmazonProductEnhancementJob j) {
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

    @SuppressWarnings("unused")
    private static String ucase(String v) {
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }
}
