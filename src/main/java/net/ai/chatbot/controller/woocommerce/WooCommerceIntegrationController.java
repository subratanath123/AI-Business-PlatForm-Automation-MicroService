package net.ai.chatbot.controller.woocommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.shopify.DirectProductUpdateRequest;
import net.ai.chatbot.dto.shopify.EnhanceProductsRequest;
import net.ai.chatbot.dto.shopify.ShopifyProductDto;
import net.ai.chatbot.dto.woocommerce.WooCommerceAuthCallbackPayload;
import net.ai.chatbot.dto.woocommerce.WooCommerceAuthInitRequest;
import net.ai.chatbot.dto.woocommerce.WooCommerceOAuthClaimRequest;
import net.ai.chatbot.dto.woocommerce.WooPushProductsRequest;
import net.ai.chatbot.dto.woocommerce.WooStoreSwitchRequest;
import net.ai.chatbot.dto.woocommerce.WooWebhookToggleRequest;
import net.ai.chatbot.entity.ProductEnhancementJob;
import net.ai.chatbot.entity.WooCommerceIntegration;
import net.ai.chatbot.entity.WooCommercePendingConnection;
import net.ai.chatbot.service.woocommerce.WooCommerceIntegrationService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WooCommerceIntegrationController {

    private final WooCommerceIntegrationService wooService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.name:Subrata Commerce}")
    private String appName;

    // ──────────────────────────────────────────────────────────────────────────
    // WC Auth redirect flow
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Kick off the WC Auth flow. Generates a nonce, parks the intended storeUrl
     * against it, and returns the canonical WC-Auth URL the browser should
     * redirect to. The return/callback URLs are server-controlled so a malicious
     * store can't redirect the user anywhere dangerous.
     */
    @PostMapping("/v1/api/woocommerce/oauth/init")
    public ResponseEntity<Map<String, Object>> initAuth(@RequestBody WooCommerceAuthInitRequest request) {
        String userId = AuthUtils.getUserId();
        if (request == null || request.getStoreUrl() == null || request.getStoreUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "storeUrl is required"));
        }
        try {
            WooCommercePendingConnection pending = wooService.initAuth(userId, request.getStoreUrl());

            String nonce       = pending.getNonce();
            String storeUrl    = pending.getStoreUrl();
            String returnUrl   = frontendBaseUrl + "/auth/woocommerce/callback?nonce=" + nonce;
            String callbackUrl = appBaseUrl + "/api/public/woocommerce/auth-callback/" + nonce;

            String authorizeUrl = storeUrl + "/wc-auth/v1/authorize"
                    + "?app_name="     + enc(appName)
                    + "&scope=read_write"
                    + "&user_id="      + enc(nonce)
                    + "&return_url="   + enc(returnUrl)
                    + "&callback_url=" + enc(callbackUrl);

            Map<String, Object> resp = new HashMap<>();
            resp.put("nonce", nonce);
            resp.put("authorizeUrl", authorizeUrl);
            resp.put("storeUrl", storeUrl);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("WC auth init failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Public server-to-server callback from the WooCommerce store itself.
     * This endpoint is unauthenticated by design — WC sends the credentials
     * directly, keyed by our pre-registered nonce.
     */
    @PostMapping("/api/public/woocommerce/auth-callback/{nonce}")
    public ResponseEntity<Void> authCallback(@PathVariable String nonce,
                                             @RequestBody WooCommerceAuthCallbackPayload payload) {
        try {
            if (payload == null || payload.getConsumer_key() == null || payload.getConsumer_secret() == null) {
                log.warn("WC auth callback for nonce {} had missing credentials", nonce);
                return ResponseEntity.badRequest().build();
            }
            wooService.receiveAuthCallback(nonce, payload.getConsumer_key(),
                    payload.getConsumer_secret(), payload.getKey_permissions());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("WC auth callback failed for nonce {}: {}", nonce, e.getMessage());
            // Always respond 200 so WC doesn't retry; the finalize call will
            // surface the "credentials not delivered" error to the user.
            return ResponseEntity.ok().build();
        }
    }

    /**
     * Browser-side finalize — claims the pending credentials, tests them,
     * and creates the real integration row.
     */
    @PostMapping("/v1/api/woocommerce/oauth/finalize")
    public ResponseEntity<Map<String, Object>> finalizeAuth(@RequestBody WooCommerceOAuthClaimRequest request) {
        String userId = AuthUtils.getUserId();
        if (request == null || request.getNonce() == null || request.getNonce().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "nonce is required"));
        }
        try {
            WooCommerceIntegration integration = wooService.finalizeAuth(
                    userId, request.getNonce(), request.getStoreName());
            return ResponseEntity.ok(integrationToMap(integration));
        } catch (Exception e) {
            log.error("WC auth finalize failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Integration management
    // ──────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/v1/api/woocommerce/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectStore(
            @RequestParam(required = false) String storeUrl) {
        String userId = AuthUtils.getUserId();
        try {
            if (storeUrl != null && !storeUrl.isBlank()) {
                wooService.disconnectStore(userId, storeUrl);
                return ResponseEntity.ok(Map.of("message", "Store disconnected", "storeUrl", storeUrl));
            }
            wooService.disconnectAllStores(userId);
            return ResponseEntity.ok(Map.of("message", "All WooCommerce stores disconnected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/v1/api/woocommerce/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        String userId = AuthUtils.getUserId();
        WooCommerceIntegration integration = wooService.getIntegration(userId);
        if (integration == null) return ResponseEntity.ok(Map.of("connected", false));
        return ResponseEntity.ok(integrationToMap(integration));
    }

    @GetMapping("/v1/api/woocommerce/stores")
    public ResponseEntity<List<Map<String, Object>>> listStores() {
        String userId = AuthUtils.getUserId();
        List<WooCommerceIntegration> stores = wooService.listStores(userId);
        return ResponseEntity.ok(stores.stream().map(this::integrationToMap).toList());
    }

    @PostMapping("/v1/api/woocommerce/stores/switch")
    public ResponseEntity<Map<String, Object>> switchStore(@RequestBody WooStoreSwitchRequest body) {
        String userId = AuthUtils.getUserId();
        if (body == null || body.getStoreUrl() == null || body.getStoreUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "storeUrl is required"));
        }
        try {
            return ResponseEntity.ok(integrationToMap(
                    wooService.switchStore(userId, body.getStoreUrl())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Product sync / upload / parse
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/woocommerce/sync")
    public ResponseEntity<Map<String, Object>> syncProducts(
            @RequestParam(defaultValue = "50") int limit) {
        String userId = AuthUtils.getUserId();
        try {
            ProductEnhancementJob job = wooService.syncProductsFromStore(userId, limit);
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            log.error("WC sync failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/woocommerce/upload")
    public ResponseEntity<Map<String, Object>> uploadProducts(
            @RequestBody EnhanceProductsRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            ProductEnhancementJob job = wooService.createUploadJob(userId, request.getProducts());
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            log.error("WC upload job creation failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/woocommerce/parse")
    public ResponseEntity<Map<String, Object>> parseProducts(@RequestBody Map<String, String> request) {
        String content  = request.get("content");
        String fileName = request.get("fileName");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }
        try {
            List<ShopifyProductDto> products = wooService.parseProductsWithAI(content, fileName);
            return ResponseEntity.ok(Map.of("products", products, "count", products.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/woocommerce/parse-image")
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
            List<ShopifyProductDto> products = wooService.parseProductsFromImages(imageUrls);
            return ResponseEntity.ok(Map.of("products", products, "count", products.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AI enhancement + push
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/woocommerce/enhance/{jobId}")
    public ResponseEntity<Map<String, Object>> enhanceJob(@PathVariable String jobId) {
        String userId = AuthUtils.getUserId();
        try {
            wooService.enhanceJobAsync(jobId, userId);
            return ResponseEntity.accepted().body(Map.of("message", "Enhancement started", "jobId", jobId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/woocommerce/push")
    public ResponseEntity<Map<String, Object>> pushProducts(@RequestBody WooPushProductsRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            wooService.pushProductsToWooCommerce(userId, request.getJobId(), request.getProductIds());
            return ResponseEntity.ok(Map.of("message", "Products pushed to WooCommerce successfully"));
        } catch (Exception e) {
            log.error("WC push failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Webhooks
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/woocommerce/webhook/toggle")
    public ResponseEntity<Map<String, Object>> toggleWebhook(@RequestBody WooWebhookToggleRequest req) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(integrationToMap(
                    wooService.toggleWebhook(userId, req.isEnabled())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/public/woocommerce/webhook/{userId}")
    public ResponseEntity<Void> receiveWebhook(
            @PathVariable String userId,
            @RequestHeader(value = "X-WC-Webhook-Source", required = false) String storeUrlHeader,
            @RequestBody String rawPayload) {
        try {
            JsonNode product = OBJECT_MAPPER.readTree(rawPayload);
            ShopifyProductDto dto = ShopifyProductDto.builder()
                    .wooId(product.path("id").asText())
                    .title(product.path("name").asText())
                    .bodyHtml(product.path("description").asText())
                    .productType(product.path("categories").isArray() && product.path("categories").size() > 0
                            ? product.path("categories").get(0).path("name").asText() : "")
                    .tags("")
                    .build();
            wooService.handleProductCreatedWebhook(userId, storeUrlHeader, dto);
        } catch (Exception e) {
            log.error("Failed to process WC webhook for user {}: {}", userId, e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Single-product direct edit (live WC product)
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/woocommerce/products/{wooId}")
    public ResponseEntity<Map<String, Object>> getProductDetails(@PathVariable String wooId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(wooService.getProductDetails(userId, wooId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/woocommerce/products/{wooId}/enhance")
    public ResponseEntity<Map<String, Object>> enhanceSingleProduct(@PathVariable String wooId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(wooService.enhanceSingleProductDirect(userId, wooId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/v1/api/woocommerce/products/{wooId}")
    public ResponseEntity<Map<String, Object>> updateProductDirect(
            @PathVariable String wooId,
            @RequestBody DirectProductUpdateRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            wooService.updateProductDirect(userId, wooId, request);
            return ResponseEntity.ok(Map.of("message", "Product updated successfully", "wooId", wooId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/woocommerce/products/{wooId}/images")
    public ResponseEntity<Map<String, Object>> addProductImage(
            @PathVariable String wooId,
            @RequestBody Map<String, Object> request) {
        String userId = AuthUtils.getUserId();
        try {
            String src = request.get("src") == null ? null : request.get("src").toString();
            String alt = request.get("alt") == null ? null : request.get("alt").toString();
            Integer position = null;
            Object posObj = request.get("position");
            if (posObj instanceof Number) position = ((Number) posObj).intValue();
            else if (posObj instanceof String s && !s.isBlank()) {
                try { position = Integer.parseInt(s); } catch (NumberFormatException ignored) { }
            }
            if (src == null || src.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "'src' (image URL) is required"));
            }
            return ResponseEntity.ok(wooService.addProductImage(userId, wooId, src, alt, position));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/v1/api/woocommerce/products/{wooId}/images/{imageId}")
    public ResponseEntity<Map<String, Object>> deleteProductImage(
            @PathVariable String wooId, @PathVariable String imageId) {
        String userId = AuthUtils.getUserId();
        try {
            wooService.deleteProductImage(userId, wooId, imageId);
            return ResponseEntity.ok(Map.of("message", "Image deleted", "wooId", wooId, "imageId", imageId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Drafts
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/woocommerce/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> getDraft(
            @PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(wooService.getDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/v1/api/woocommerce/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> updateDraft(
            @PathVariable String jobId, @PathVariable String localId,
            @RequestBody DirectProductUpdateRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(wooService.updateDraft(userId, jobId, localId, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/woocommerce/drafts/{jobId}/{localId}/enhance")
    public ResponseEntity<Map<String, Object>> enhanceDraft(
            @PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(wooService.enhanceDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/woocommerce/drafts/{jobId}/{localId}/publish")
    public ResponseEntity<Map<String, Object>> publishDraft(
            @PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(wooService.publishDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/v1/api/woocommerce/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> deleteDraft(
            @PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            wooService.deleteDraft(userId, jobId, localId);
            return ResponseEntity.ok(Map.of("message", "Draft deleted", "jobId", jobId, "localId", localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Jobs
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/woocommerce/jobs")
    public ResponseEntity<List<ProductEnhancementJob>> getJobs() {
        return ResponseEntity.ok(wooService.getJobs(AuthUtils.getUserId()));
    }

    @GetMapping("/v1/api/woocommerce/jobs/{jobId}")
    public ResponseEntity<ProductEnhancementJob> getJob(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(wooService.getJob(AuthUtils.getUserId(), jobId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, Object> integrationToMap(WooCommerceIntegration i) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",              i.getId());
        map.put("storeUrl",        i.getStoreUrl());
        map.put("storeName",       i.getStoreName());
        map.put("connected",       i.isConnected());
        map.put("active",          i.isActive());
        map.put("webhookEnabled",  i.isWebhookEnabled());
        map.put("createdAt",       i.getCreatedAt());
        map.put("updatedAt",       i.getUpdatedAt());
        return map;
    }

    private Map<String, Object> jobToMap(ProductEnhancementJob j) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",               j.getId());
        map.put("status",           j.getStatus());
        map.put("platform",         j.getPlatform());
        map.put("source",           j.getSource());
        map.put("rawProducts",      j.getRawProducts());
        map.put("enhancedProducts", j.getEnhancedProducts());
        map.put("createdAt",        j.getCreatedAt());
        return map;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
