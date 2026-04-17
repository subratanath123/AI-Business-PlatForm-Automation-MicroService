package net.ai.chatbot.controller.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.shopify.*;
import net.ai.chatbot.entity.ProductEnhancementJob;
import net.ai.chatbot.entity.ShopifyIntegration;
import net.ai.chatbot.service.shopify.ShopifyIntegrationService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ShopifyIntegrationController {

    private final ShopifyIntegrationService shopifyIntegrationService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ──────────────────────────────────────────────────────────────────────────
    // Integration management
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Called by the Next.js OAuth callback after exchanging the Shopify code for an access token.
     * Saves the encrypted token for the authenticated user.
     */
    @PostMapping("/v1/api/shopify/oauth/connect")
    public ResponseEntity<Map<String, Object>> oauthConnect(
            @RequestBody OAuthConnectRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            ShopifyIntegration integration = shopifyIntegrationService.saveOAuthToken(
                    userId, request.getShopDomain(), request.getAccessToken(), request.getShopName());
            return ResponseEntity.ok(integrationToMap(integration));
        } catch (Exception e) {
            log.error("Failed to save Shopify OAuth token for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Disconnect one specific store by domain. If {@code shopDomain} is omitted,
     * disconnects ALL stores (legacy behaviour).
     */
    @DeleteMapping("/v1/api/shopify/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectStore(
            @RequestParam(required = false) String shopDomain) {
        String userId = AuthUtils.getUserId();
        try {
            if (shopDomain != null && !shopDomain.isBlank()) {
                shopifyIntegrationService.disconnectStore(userId, shopDomain);
                return ResponseEntity.ok(Map.of("message", "Store disconnected", "shopDomain", shopDomain));
            }
            shopifyIntegrationService.disconnectAllStores(userId);
            return ResponseEntity.ok(Map.of("message", "All Shopify stores disconnected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/v1/api/shopify/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        String userId = AuthUtils.getUserId();
        ShopifyIntegration integration = shopifyIntegrationService.getIntegration(userId);
        if (integration == null) {
            return ResponseEntity.ok(Map.of("connected", false));
        }
        return ResponseEntity.ok(integrationToMap(integration));
    }

    /**
     * List every connected Shopify store for the user (for the store switcher UI).
     */
    @GetMapping("/v1/api/shopify/stores")
    public ResponseEntity<List<Map<String, Object>>> listStores() {
        String userId = AuthUtils.getUserId();
        List<ShopifyIntegration> stores = shopifyIntegrationService.listStores(userId);
        return ResponseEntity.ok(stores.stream().map(this::integrationToMap).toList());
    }

    /**
     * Mark a specific store as active. All subsequent operations target this store.
     */
    @PostMapping("/v1/api/shopify/stores/switch")
    public ResponseEntity<Map<String, Object>> switchStore(@RequestBody Map<String, String> body) {
        String userId = AuthUtils.getUserId();
        String shopDomain = body.get("shopDomain");
        if (shopDomain == null || shopDomain.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "shopDomain is required"));
        }
        try {
            ShopifyIntegration integration = shopifyIntegrationService.switchStore(userId, shopDomain);
            return ResponseEntity.ok(integrationToMap(integration));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Product sync
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/shopify/sync")
    public ResponseEntity<Map<String, Object>> syncProducts(
            @RequestParam(defaultValue = "50") int limit) {
        String userId = AuthUtils.getUserId();
        try {
            ProductEnhancementJob job = shopifyIntegrationService.syncProductsFromStore(userId, limit);
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            log.error("Sync failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/shopify/upload")
    public ResponseEntity<Map<String, Object>> uploadProducts(
            @RequestBody EnhanceProductsRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            ProductEnhancementJob job = shopifyIntegrationService.createUploadJob(userId, request.getProducts());
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            log.error("Upload job creation failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AI enhancement
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/shopify/enhance/{jobId}")
    public ResponseEntity<Map<String, Object>> enhanceJob(@PathVariable String jobId) {
        String userId = AuthUtils.getUserId();
        try {
            shopifyIntegrationService.enhanceJobAsync(jobId, userId);
            return ResponseEntity.accepted().body(Map.of(
                    "message", "Enhancement started",
                    "jobId", jobId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Push to Shopify
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/shopify/push")
    public ResponseEntity<Map<String, Object>> pushProducts(
            @RequestBody PushProductsRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            shopifyIntegrationService.pushProductsToShopify(userId, request.getJobId(), request.getProductIds());
            return ResponseEntity.ok(Map.of("message", "Products pushed to Shopify successfully"));
        } catch (Exception e) {
            log.error("Push failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Webhook management
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/v1/api/shopify/webhook/toggle")
    public ResponseEntity<Map<String, Object>> toggleWebhook(
            @RequestBody WebhookToggleRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            ShopifyIntegration integration = shopifyIntegrationService.toggleWebhook(userId, request.isEnabled());
            return ResponseEntity.ok(integrationToMap(integration));
        } catch (Exception e) {
            log.error("Webhook toggle failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Public endpoint — Shopify sends product/created webhook here.
     * No JWT required; Shopify calls this directly.
     */
    @PostMapping("/api/public/shopify/webhook/{userId}")
    public ResponseEntity<Void> receiveWebhook(
            @PathVariable String userId,
            @RequestHeader(value = "X-Shopify-Shop-Domain", required = false) String shopDomain,
            @RequestBody String rawPayload) {
        try {
            JsonNode product = OBJECT_MAPPER.readTree(rawPayload);
            ShopifyProductDto dto = ShopifyProductDto.builder()
                    .shopifyId(product.path("id").asText())
                    .title(product.path("title").asText())
                    .bodyHtml(product.path("body_html").asText())
                    .vendor(product.path("vendor").asText())
                    .productType(product.path("product_type").asText())
                    .tags(product.path("tags").asText())
                    .build();

            shopifyIntegrationService.handleProductCreatedWebhook(userId, shopDomain, dto);
        } catch (Exception e) {
            log.error("Failed to process Shopify webhook for user {}: {}", userId, e.getMessage());
        }
        // Always return 200 to Shopify to prevent retries
        return ResponseEntity.ok().build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Single-product direct edit (full field review + AI per-field + save)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Return every Shopify field for one product: all mutable content fields,
     * SEO metafields, variants (read-only), options, images, and system info.
     */
    @GetMapping("/v1/api/shopify/products/{shopifyId}")
    public ResponseEntity<Map<String, Object>> getProductDetails(@PathVariable String shopifyId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(shopifyIntegrationService.getProductDetails(userId, shopifyId));
        } catch (Exception e) {
            log.error("getProductDetails failed for user {} product {}: {}", userId, shopifyId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Run AI enhancement on a single product and return the suggestions inline.
     * The product is NOT automatically saved — the user reviews and calls PUT to confirm.
     */
    @PostMapping("/v1/api/shopify/products/{shopifyId}/enhance")
    public ResponseEntity<Map<String, Object>> enhanceSingleProduct(@PathVariable String shopifyId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(shopifyIntegrationService.enhanceSingleProductDirect(userId, shopifyId));
        } catch (Exception e) {
            log.error("enhanceSingleProduct failed for user {} product {}: {}", userId, shopifyId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Save user-confirmed edits (manual edits + accepted AI suggestions) back to Shopify.
     * Only mutable fields are accepted; immutable system fields are ignored.
     */
    @PutMapping("/v1/api/shopify/products/{shopifyId}")
    public ResponseEntity<Map<String, Object>> updateProductDirect(
            @PathVariable String shopifyId,
            @RequestBody net.ai.chatbot.dto.shopify.DirectProductUpdateRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            shopifyIntegrationService.updateProductDirect(userId, shopifyId, request);
            return ResponseEntity.ok(Map.of("message", "Product updated successfully", "shopifyId", shopifyId));
        } catch (Exception e) {
            log.error("updateProductDirect failed for user {} product {}: {}", userId, shopifyId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Job history
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping("/v1/api/shopify/jobs")
    public ResponseEntity<List<ProductEnhancementJob>> getJobs() {
        String userId = AuthUtils.getUserId();
        return ResponseEntity.ok(shopifyIntegrationService.getJobs(userId));
    }

    @GetMapping("/v1/api/shopify/jobs/{jobId}")
    public ResponseEntity<ProductEnhancementJob> getJob(@PathVariable String jobId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(shopifyIntegrationService.getJob(userId, jobId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, Object> integrationToMap(ShopifyIntegration i) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", i.getId());
        map.put("shopDomain", i.getShopDomain());
        map.put("shopName", i.getShopName());
        map.put("connected", i.isConnected());
        map.put("active", i.isActive());
        map.put("webhookEnabled", i.isWebhookEnabled());
        map.put("createdAt", i.getCreatedAt());
        map.put("updatedAt", i.getUpdatedAt());
        return map;
    }

    private Map<String, Object> jobToMap(ProductEnhancementJob j) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", j.getId());
        map.put("status", j.getStatus());
        map.put("platform", j.getPlatform());
        map.put("source", j.getSource());
        map.put("rawProducts", j.getRawProducts());
        map.put("enhancedProducts", j.getEnhancedProducts());
        map.put("createdAt", j.getCreatedAt());
        return map;
    }
}
