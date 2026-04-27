package net.ai.chatbot.controller.aliexpress;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.aliexpress.AliExpressDirectProductUpdateRequest;
import net.ai.chatbot.dto.aliexpress.AliExpressEnhanceProductsRequest;
import net.ai.chatbot.dto.aliexpress.AliExpressOAuthFinalizeRequest;
import net.ai.chatbot.dto.aliexpress.AliExpressOAuthInitRequest;
import net.ai.chatbot.dto.aliexpress.AliExpressPushProductsRequest;
import net.ai.chatbot.dto.aliexpress.AliExpressStoreSwitchRequest;
import net.ai.chatbot.entity.AliExpressIntegration;
import net.ai.chatbot.entity.AliExpressPendingConnection;
import net.ai.chatbot.entity.AliExpressProductEnhancementJob;
import net.ai.chatbot.service.aliexpress.AliExpressIntegrationService;
import net.ai.chatbot.service.aliexpress.AliExpressOAuthService;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AliExpressIntegrationController {

    private final AliExpressIntegrationService aliExpressService;
    private final AliExpressOAuthService oauth;

    @PostMapping("/v1/api/aliexpress/oauth/init")
    public ResponseEntity<Map<String, Object>> initAuth(@RequestBody(required = false) AliExpressOAuthInitRequest request) {
        String userId = AuthUtils.getUserId();
        if (oauth.appKey() == null || oauth.appKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "ALIEXPRESS_APP_KEY is not configured. Register an app on the AliExpress Open Platform."));
        }
        if (oauth.redirectUri() == null || oauth.redirectUri().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "ALIEXPRESS_REDIRECT_URI is not configured. It must match the callback URL in your app."));
        }
        try {
            AliExpressPendingConnection pending = aliExpressService.initAuth(userId);
            String authorizeUrl = oauth.buildAuthorizeUrl(pending.getState());
            Map<String, Object> resp = new HashMap<>();
            resp.put("state", pending.getState());
            resp.put("authorizeUrl", authorizeUrl);
            resp.put("redirectUri", oauth.redirectUri());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("AliExpress auth init failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/oauth/finalize")
    public ResponseEntity<Map<String, Object>> finalizeAuth(@RequestBody AliExpressOAuthFinalizeRequest request) {
        String userId = AuthUtils.getUserId();
        if (request == null || request.getState() == null || request.getState().isBlank()
                || request.getCode() == null || request.getCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "state and code are required"));
        }
        try {
            AliExpressIntegration i = aliExpressService.finalizeAuth(userId,
                    request.getState(), request.getCode(), request.getStoreName());
            return ResponseEntity.ok(integrationToMap(i));
        } catch (Exception e) {
            log.error("AliExpress auth finalize failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/v1/api/aliexpress/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@RequestParam(required = false) String sellerId) {
        String userId = AuthUtils.getUserId();
        try {
            if (sellerId != null && !sellerId.isBlank()) {
                aliExpressService.disconnectStore(userId, sellerId);
                return ResponseEntity.ok(Map.of("message", "Seller disconnected", "sellerId", sellerId));
            }
            aliExpressService.disconnectAllStores(userId);
            return ResponseEntity.ok(Map.of("message", "All AliExpress sellers disconnected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/v1/api/aliexpress/status")
    public ResponseEntity<Map<String, Object>> status() {
        String userId = AuthUtils.getUserId();
        AliExpressIntegration i = aliExpressService.getIntegration(userId);
        if (i == null) return ResponseEntity.ok(Map.of("connected", false));
        return ResponseEntity.ok(integrationToMap(i));
    }

    @GetMapping("/v1/api/aliexpress/stores")
    public ResponseEntity<List<Map<String, Object>>> stores() {
        String userId = AuthUtils.getUserId();
        return ResponseEntity.ok(aliExpressService.listStores(userId).stream().map(this::integrationToMap).toList());
    }

    @PostMapping("/v1/api/aliexpress/stores/switch")
    public ResponseEntity<Map<String, Object>> switchStore(@RequestBody AliExpressStoreSwitchRequest body) {
        String userId = AuthUtils.getUserId();
        if (body == null || body.getSellerId() == null || body.getSellerId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sellerId is required"));
        }
        try {
            return ResponseEntity.ok(integrationToMap(aliExpressService.switchStore(userId, body.getSellerId())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/sync")
    public ResponseEntity<Map<String, Object>> sync(@RequestParam(defaultValue = "50") int limit) {
        String userId = AuthUtils.getUserId();
        try {
            AliExpressProductEnhancementJob job = aliExpressService.syncProductsFromStore(userId, limit);
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            log.error("AliExpress sync failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestBody AliExpressEnhanceProductsRequest request) {
        String userId = AuthUtils.getUserId();
        try {
            AliExpressProductEnhancementJob job = aliExpressService.createUploadJob(userId,
                    request == null ? List.of() : request.getProducts());
            return ResponseEntity.ok(jobToMap(job));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/parse")
    public ResponseEntity<Map<String, Object>> parse(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String fileName = request.get("fileName");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }
        try {
            var products = aliExpressService.parseProductsWithAI(content, fileName);
            return ResponseEntity.ok(Map.of("products", products, "count", products.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/parse-image")
    public ResponseEntity<Map<String, Object>> parseImage(@RequestBody Map<String, Object> request) {
        Object imagesObj = request.get("images");
        if (!(imagesObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "At least one image is required"));
        }
        List<String> urls = new ArrayList<>();
        for (Object o : rawList) {
            if (o instanceof String s && !s.isBlank()) urls.add(s);
            if (urls.size() >= 6) break;
        }
        if (urls.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No valid images supplied"));
        }
        try {
            var products = aliExpressService.parseProductsFromImages(urls);
            return ResponseEntity.ok(Map.of("products", products, "count", products.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/enhance/{jobId}")
    public ResponseEntity<Map<String, Object>> enhanceJob(@PathVariable String jobId) {
        String userId = AuthUtils.getUserId();
        try {
            aliExpressService.enhanceJobAsync(jobId, userId);
            return ResponseEntity.accepted().body(Map.of("message", "Enhancement started", "jobId", jobId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/push")
    public ResponseEntity<Map<String, Object>> push(@RequestBody AliExpressPushProductsRequest request) {
        String userId = AuthUtils.getUserId();
        if (request == null || request.getJobId() == null || request.getJobId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "jobId is required"));
        }
        try {
            aliExpressService.pushProductsToAliExpress(userId, request.getJobId(), request.getProductIds());
            return ResponseEntity.ok(Map.of("message", "Products updated on AliExpress"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/notifications/toggle")
    public ResponseEntity<Map<String, Object>> toggleNotifications(@RequestBody Map<String, Object> body) {
        String userId = AuthUtils.getUserId();
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        try {
            return ResponseEntity.ok(integrationToMap(aliExpressService.toggleNotifications(userId, enabled)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/v1/api/aliexpress/products/{productId}")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable String productId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(aliExpressService.getProductDetails(userId, productId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/products/{productId}/enhance")
    public ResponseEntity<Map<String, Object>> enhanceProduct(@PathVariable String productId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(aliExpressService.enhanceSingleProductDirect(userId, productId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/v1/api/aliexpress/products/{productId}")
    public ResponseEntity<Map<String, Object>> updateProduct(@PathVariable String productId,
                                                             @RequestBody AliExpressDirectProductUpdateRequest req) {
        String userId = AuthUtils.getUserId();
        try {
            aliExpressService.updateProductDirect(userId, productId, req);
            return ResponseEntity.ok(Map.of("message", "Product updated", "productId", productId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/products/{productId}/images")
    public ResponseEntity<Map<String, Object>> addImage(@PathVariable String productId,
                                                        @RequestBody Map<String, Object> body) {
        String userId = AuthUtils.getUserId();
        Object src = body == null ? null : body.get("src");
        try {
            List<String> images = aliExpressService.addProductImage(userId, productId,
                    src == null ? null : src.toString());
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/v1/api/aliexpress/products/{productId}/images/{imageId}")
    public ResponseEntity<Map<String, Object>> removeImage(@PathVariable String productId,
                                                           @PathVariable String imageId) {
        String userId = AuthUtils.getUserId();
        try {
            String decoded = java.net.URLDecoder.decode(imageId, StandardCharsets.UTF_8);
            List<String> images = aliExpressService.removeProductImage(userId, productId, decoded);
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/v1/api/aliexpress/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> getDraft(@PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(aliExpressService.getDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/v1/api/aliexpress/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> updateDraft(@PathVariable String jobId, @PathVariable String localId,
                                                           @RequestBody AliExpressDirectProductUpdateRequest req) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(aliExpressService.updateDraft(userId, jobId, localId, req));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/drafts/{jobId}/{localId}/enhance")
    public ResponseEntity<Map<String, Object>> enhanceDraft(@PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(aliExpressService.enhanceDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/v1/api/aliexpress/drafts/{jobId}/{localId}/publish")
    public ResponseEntity<Map<String, Object>> publishDraft(@PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            return ResponseEntity.ok(aliExpressService.publishDraft(userId, jobId, localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/v1/api/aliexpress/drafts/{jobId}/{localId}")
    public ResponseEntity<Map<String, Object>> deleteDraft(@PathVariable String jobId, @PathVariable String localId) {
        String userId = AuthUtils.getUserId();
        try {
            aliExpressService.deleteDraft(userId, jobId, localId);
            return ResponseEntity.ok(Map.of("message", "Draft deleted", "jobId", jobId, "localId", localId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/v1/api/aliexpress/jobs")
    public ResponseEntity<List<AliExpressProductEnhancementJob>> jobs() {
        return ResponseEntity.ok(aliExpressService.getJobs(AuthUtils.getUserId()));
    }

    @GetMapping("/v1/api/aliexpress/jobs/{jobId}")
    public ResponseEntity<AliExpressProductEnhancementJob> job(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(aliExpressService.getJob(AuthUtils.getUserId(), jobId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Map<String, Object> integrationToMap(AliExpressIntegration i) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", i.getId());
        m.put("sellerId", i.getSellerId());
        m.put("sellerLoginId", i.getSellerLoginId());
        m.put("storeName", i.getStoreName());
        m.put("connected", i.isConnected());
        m.put("active", i.isActive());
        m.put("notificationsEnabled", i.isNotificationsEnabled());
        m.put("defaultContentLocale", i.getDefaultContentLocale());
        m.put("createdAt", i.getCreatedAt());
        m.put("updatedAt", i.getUpdatedAt());
        return m;
    }

    private Map<String, Object> jobToMap(AliExpressProductEnhancementJob j) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", j.getId());
        m.put("status", j.getStatus());
        m.put("platform", j.getPlatform());
        m.put("source", j.getSource());
        m.put("rawProducts", j.getRawProducts());
        m.put("enhancedProducts", j.getEnhancedProducts());
        m.put("createdAt", j.getCreatedAt());
        return m;
    }

}
