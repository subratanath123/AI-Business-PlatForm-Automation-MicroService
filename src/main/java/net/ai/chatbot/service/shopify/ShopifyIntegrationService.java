package net.ai.chatbot.service.shopify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ProductEnhancementJobDao;
import net.ai.chatbot.dao.ShopifyIntegrationDao;
import net.ai.chatbot.dto.shopify.DirectProductUpdateRequest;
import net.ai.chatbot.dto.shopify.ShopifyProductDto;
import net.ai.chatbot.entity.ProductEnhancementJob;
import net.ai.chatbot.entity.ProductEnhancementJob.ProductItem;
import net.ai.chatbot.entity.ShopifyIntegration;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopifyIntegrationService {

    private final ShopifyIntegrationDao shopifyIntegrationDao;
    private final ProductEnhancementJobDao jobDao;
    private final ShopifyApiService shopifyApiService;
    private final ProductAIEnhancementService aiEnhancementService;
    private final EncryptionUtils encryptionUtils;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    /**
     * Save the OAuth-issued access token after a successful Shopify OAuth flow.
     * Supports multiple connected stores per user.
     *   - Same domain → updates the existing integration in-place.
     *   - New domain  → adds a new integration and marks it as the active one.
     */
    public ShopifyIntegration saveOAuthToken(String userId, String shopDomain, String accessToken, String shopName) {
        log.info("Saving Shopify OAuth token for user {} — store '{}'", userId, shopName);

        String encryptedToken = encryptionUtils.encrypt(accessToken);
        String domain = normalizeDomain(shopDomain);

        ShopifyIntegration existing = shopifyIntegrationDao
                .findByUserIdAndShopDomain(userId, domain).orElse(null);

        if (existing != null) {
            existing.setShopName(shopName);
            existing.setEncryptedAccessToken(encryptedToken);
            existing.setConnected(true);
            existing.setUpdatedAt(Instant.now());
            ShopifyIntegration saved = shopifyIntegrationDao.save(existing);
            activateStore(userId, saved.getShopDomain());
            return shopifyIntegrationDao.findByUserIdAndShopDomain(userId, saved.getShopDomain()).orElse(saved);
        }

        ShopifyIntegration integration = ShopifyIntegration.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .shopDomain(domain)
                .shopName(shopName)
                .encryptedAccessToken(encryptedToken)
                .connected(true)
                .webhookEnabled(false)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ShopifyIntegration saved = shopifyIntegrationDao.save(integration);
        activateStore(userId, saved.getShopDomain());
        return shopifyIntegrationDao.findByUserIdAndShopDomain(userId, saved.getShopDomain()).orElse(saved);
    }

    /**
     * Return every connected Shopify store for a user.
     */
    public List<ShopifyIntegration> listStores(String userId) {
        List<ShopifyIntegration> stores = shopifyIntegrationDao.findAllByUserId(userId);
        // Ensure exactly one is active if any exist.
        boolean anyActive = stores.stream().anyMatch(ShopifyIntegration::isActive);
        if (!anyActive && !stores.isEmpty()) {
            activateStore(userId, stores.get(0).getShopDomain());
            return shopifyIntegrationDao.findAllByUserId(userId);
        }
        return stores;
    }

    /**
     * Mark a specific store as active; all others for the user become inactive.
     */
    public ShopifyIntegration switchStore(String userId, String shopDomain) {
        String domain = normalizeDomain(shopDomain);
        ShopifyIntegration target = shopifyIntegrationDao
                .findByUserIdAndShopDomain(userId, domain)
                .orElseThrow(() -> new RuntimeException("Store not connected: " + domain));
        activateStore(userId, domain);
        return shopifyIntegrationDao.findByUserIdAndShopDomain(userId, target.getShopDomain()).orElse(target);
    }

    /**
     * Disconnect one specific store by domain. Promotes another store as active if needed.
     */
    public void disconnectStore(String userId, String shopDomain) {
        String domain = normalizeDomain(shopDomain);
        ShopifyIntegration integration = shopifyIntegrationDao
                .findByUserIdAndShopDomain(userId, domain)
                .orElseThrow(() -> new RuntimeException("Store not connected: " + domain));

        if (integration.getWebhookId() != null) {
            try {
                String token = encryptionUtils.decrypt(integration.getEncryptedAccessToken());
                shopifyApiService.deleteWebhook(integration.getShopDomain(), token, integration.getWebhookId());
            } catch (Exception e) {
                log.warn("Could not delete webhook during disconnect: {}", e.getMessage());
            }
        }

        shopifyIntegrationDao.deleteByUserIdAndShopDomain(userId, domain);

        if (integration.isActive()) {
            shopifyIntegrationDao.findAllByUserId(userId).stream()
                    .findFirst()
                    .ifPresent(next -> activateStore(userId, next.getShopDomain()));
        }
    }

    /**
     * Disconnect all stores for a user (used by the "Disconnect all" action).
     */
    public void disconnectAllStores(String userId) {
        for (ShopifyIntegration integration : shopifyIntegrationDao.findAllByUserId(userId)) {
            if (integration.getWebhookId() != null) {
                try {
                    String token = encryptionUtils.decrypt(integration.getEncryptedAccessToken());
                    shopifyApiService.deleteWebhook(integration.getShopDomain(), token, integration.getWebhookId());
                } catch (Exception e) {
                    log.warn("Could not delete webhook during disconnect all: {}", e.getMessage());
                }
            }
        }
        shopifyIntegrationDao.deleteByUserId(userId);
    }

    /**
     * Get the currently active integration for a user (the one all operations target).
     */
    public ShopifyIntegration getIntegration(String userId) {
        ShopifyIntegration active = shopifyIntegrationDao.findByUserIdAndActiveTrue(userId).orElse(null);
        if (active != null) return active;
        // Back-compat: if no active flag set yet, promote the first one we find.
        ShopifyIntegration any = shopifyIntegrationDao.findByUserId(userId).orElse(null);
        if (any != null) {
            activateStore(userId, any.getShopDomain());
            return shopifyIntegrationDao.findByUserIdAndShopDomain(userId, any.getShopDomain()).orElse(any);
        }
        return null;
    }

    private void activateStore(String userId, String shopDomain) {
        List<ShopifyIntegration> all = shopifyIntegrationDao.findAllByUserId(userId);
        for (ShopifyIntegration s : all) {
            boolean shouldBeActive = s.getShopDomain().equals(shopDomain);
            if (s.isActive() != shouldBeActive) {
                s.setActive(shouldBeActive);
                s.setUpdatedAt(Instant.now());
                shopifyIntegrationDao.save(s);
            }
        }
    }

    /**
     * Sync products from Shopify store and create an enhancement job.
     */
    public ProductEnhancementJob syncProductsFromStore(String userId, int limit) {
        ShopifyIntegration integration = requireIntegration(userId);
        String apiKey = encryptionUtils.decrypt(integration.getEncryptedAccessToken());

        List<ShopifyProductDto> products = shopifyApiService.listProducts(integration.getShopDomain(), apiKey, limit);

        List<ProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .collect(Collectors.toList());

        ProductEnhancementJob job = ProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("SHOPIFY")
                .status("PENDING")
                .rawProducts(rawItems)
                .source("SYNC")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return jobDao.save(job);
    }

    /**
     * Create a job from uploaded (CSV-parsed) products.
     */
    public ProductEnhancementJob createUploadJob(String userId, List<ShopifyProductDto> products) {
        List<ProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .collect(Collectors.toList());

        ProductEnhancementJob job = ProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("SHOPIFY")
                .status("PENDING")
                .rawProducts(rawItems)
                .source("UPLOAD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return jobDao.save(job);
    }

    /**
     * Run AI enhancement on a job's products asynchronously.
     */
    @Async
    public void enhanceJobAsync(String jobId, String userId) {
        ProductEnhancementJob job = jobDao.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus("PROCESSING");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);

        try {
            List<ShopifyProductDto> rawDtos = job.getRawProducts().stream()
                    .map(this::itemToDto)
                    .collect(Collectors.toList());

            List<ProductItem> enhanced = aiEnhancementService.enhanceProducts(rawDtos);

            job.setEnhancedProducts(enhanced);
            job.setStatus("ENHANCED");
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
            log.info("Enhancement complete for job {}: {} products", jobId, enhanced.size());
        } catch (Exception e) {
            log.error("Enhancement failed for job {}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
        }
    }

    /**
     * Push selected enhanced products back to Shopify.
     */
    public void pushProductsToShopify(String userId, String jobId, List<String> shopifyIds) {
        ShopifyIntegration integration = requireIntegration(userId);
        String apiKey = encryptionUtils.decrypt(integration.getEncryptedAccessToken());

        ProductEnhancementJob job = jobDao.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        if (!"ENHANCED".equals(job.getStatus())) {
            throw new RuntimeException("Job must be in ENHANCED status before publishing");
        }

        List<ProductItem> toPublish = job.getEnhancedProducts().stream()
                .filter(p -> shopifyIds == null || shopifyIds.isEmpty() || shopifyIds.contains(p.getShopifyId()))
                .collect(Collectors.toList());

        int successCount = 0;
        for (ProductItem item : toPublish) {
            try {
                shopifyApiService.updateProduct(integration.getShopDomain(), apiKey, itemToEnhancedDto(item));
                item.setStatus("PUBLISHED");
                successCount++;
            } catch (Exception e) {
                log.error("Failed to push product {}: {}", item.getShopifyId(), e.getMessage());
                item.setStatus("FAILED");
            }
        }

        job.setStatus("PUBLISHED");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        log.info("Pushed {}/{} products to Shopify for job {}", successCount, toPublish.size(), jobId);
    }

    /**
     * Enable or disable webhook auto-enhancement.
     */
    public ShopifyIntegration toggleWebhook(String userId, boolean enable) {
        ShopifyIntegration integration = requireIntegration(userId);
        String apiKey = encryptionUtils.decrypt(integration.getEncryptedAccessToken());

        if (enable && !integration.isWebhookEnabled()) {
            String callbackUrl = appBaseUrl + "/api/public/shopify/webhook/" + userId;
            String webhookId = shopifyApiService.registerProductCreatedWebhook(
                    integration.getShopDomain(), apiKey, callbackUrl);
            integration.setWebhookId(webhookId);
            integration.setWebhookEnabled(true);
        } else if (!enable && integration.isWebhookEnabled() && integration.getWebhookId() != null) {
            shopifyApiService.deleteWebhook(integration.getShopDomain(), apiKey, integration.getWebhookId());
            integration.setWebhookId(null);
            integration.setWebhookEnabled(false);
        }

        integration.setUpdatedAt(Instant.now());
        return shopifyIntegrationDao.save(integration);
    }

    /**
     * Handle incoming Shopify product/created webhook — enhance and push back automatically.
     * The shopDomain (from the X-Shopify-Shop-Domain header) routes the webhook to the
     * correct connected store when the user has multiple.
     */
    @Async
    public void handleProductCreatedWebhook(String userId, String shopDomain, ShopifyProductDto product) {
        log.info("Webhook received: new product '{}' for user {} on store {}", product.getTitle(), userId, shopDomain);

        ShopifyIntegration integration;
        if (shopDomain != null && !shopDomain.isBlank()) {
            integration = shopifyIntegrationDao
                    .findByUserIdAndShopDomain(userId, normalizeDomain(shopDomain))
                    .orElse(null);
        } else {
            integration = getIntegration(userId);
        }
        if (integration == null || !integration.isWebhookEnabled()) {
            log.warn("Ignoring webhook for user {} / store {} — integration missing or webhook disabled", userId, shopDomain);
            return;
        }

        String apiKey = encryptionUtils.decrypt(integration.getEncryptedAccessToken());

        // Create a single-product job for history tracking
        List<ProductItem> rawItems = new ArrayList<>();
        rawItems.add(ProductItem.builder()
                .shopifyId(product.getShopifyId())
                .title(product.getTitle())
                .bodyHtml(product.getBodyHtml())
                .vendor(product.getVendor())
                .productType(product.getProductType())
                .tags(product.getTags())
                .status("PENDING")
                .build());

        ProductEnhancementJob job = ProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("SHOPIFY")
                .status("PROCESSING")
                .rawProducts(rawItems)
                .source("WEBHOOK")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        jobDao.save(job);

        try {
            ProductItem enhanced = aiEnhancementService.enhanceSingle(product);

            shopifyApiService.updateProduct(integration.getShopDomain(), apiKey, itemToEnhancedDto(enhanced));

            enhanced.setStatus("PUBLISHED");
            job.setEnhancedProducts(List.of(enhanced));
            job.setStatus("PUBLISHED");
        } catch (Exception e) {
            log.error("Webhook auto-enhance failed for product {}", product.getShopifyId(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }

        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
    }

    // ── Single-product direct edit flow ──────────────────────────────────────

    /**
     * Fetch full product details (all Shopify fields + SEO metafields + variants + images).
     */
    public Map<String, Object> getProductDetails(String userId, String shopifyId) {
        ShopifyIntegration integration = requireIntegration(userId);
        String accessToken = encryptionUtils.decrypt(integration.getEncryptedAccessToken());
        return shopifyApiService.getFullProduct(integration.getShopDomain(), accessToken, shopifyId);
    }

    /**
     * Fetch the full product and immediately run AI enhancement.
     * Returns the product map with an extra "aiSuggestions" key.
     * Does NOT persist to MongoDB — user reviews and saves manually.
     */
    public Map<String, Object> enhanceSingleProductDirect(String userId, String shopifyId) {
        ShopifyIntegration integration = requireIntegration(userId);
        String accessToken = encryptionUtils.decrypt(integration.getEncryptedAccessToken());

        Map<String, Object> productMap = shopifyApiService.getFullProduct(
                integration.getShopDomain(), accessToken, shopifyId);

        ShopifyProductDto dto = ShopifyProductDto.builder()
                .shopifyId(shopifyId)
                .title(str(productMap, "title"))
                .bodyHtml(str(productMap, "bodyHtml"))
                .vendor(str(productMap, "vendor"))
                .productType(str(productMap, "productType"))
                .tags(str(productMap, "tags"))
                .handle(str(productMap, "handle"))
                .seoTitle(str(productMap, "seoTitle"))
                .seoDescription(str(productMap, "seoDescription"))
                .build();

        ProductItem enhanced = aiEnhancementService.enhanceSingle(dto);

        Map<String, Object> suggestions = new LinkedHashMap<>();
        suggestions.put("title",          enhanced.getEnhancedTitle());
        suggestions.put("bodyHtml",       enhanced.getEnhancedBodyHtml());
        suggestions.put("productType",    enhanced.getEnhancedProductType());
        suggestions.put("tags",           enhanced.getEnhancedTags());
        suggestions.put("handle",         enhanced.getEnhancedHandle());
        suggestions.put("seoTitle",       enhanced.getEnhancedSeoTitle());
        suggestions.put("seoDescription", enhanced.getEnhancedSeoDescription());

        productMap.put("aiSuggestions", suggestions);
        return productMap;
    }

    /**
     * Push user-confirmed field edits directly to Shopify (bypasses job workflow).
     */
    public void updateProductDirect(String userId, String shopifyId, DirectProductUpdateRequest req) {
        ShopifyIntegration integration = requireIntegration(userId);
        String accessToken = encryptionUtils.decrypt(integration.getEncryptedAccessToken());
        shopifyApiService.updateProductDirect(integration.getShopDomain(), accessToken, shopifyId, req);
        log.info("Direct product update applied for user {} product {}", userId, shopifyId);
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : "";
    }

    /**
     * Get all jobs for a user.
     */
    public List<ProductEnhancementJob> getJobs(String userId) {
        return jobDao.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get a specific job.
     */
    public ProductEnhancementJob getJob(String userId, String jobId) {
        return jobDao.findById(jobId)
                .filter(j -> userId.equals(j.getUserId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));
    }

    private ShopifyIntegration requireIntegration(String userId) {
        ShopifyIntegration active = getIntegration(userId);
        if (active == null) {
            throw new RuntimeException("No Shopify integration found. Please connect your store first.");
        }
        return active;
    }

    private String normalizeDomain(String domain) {
        domain = domain.trim().toLowerCase();
        if (!domain.endsWith(".myshopify.com")) domain += ".myshopify.com";
        return domain;
    }

    /** Map a ShopifyProductDto to a raw (un-enhanced) ProductItem. */
    private ProductItem dtoToRawItem(ShopifyProductDto p) {
        return ProductItem.builder()
                .shopifyId(p.getShopifyId())
                .title(p.getTitle())
                .bodyHtml(p.getBodyHtml())
                .vendor(p.getVendor())
                .productType(p.getProductType())
                .tags(p.getTags())
                .handle(p.getHandle())
                .seoTitle(p.getSeoTitle())
                .seoDescription(p.getSeoDescription())
                .status("PENDING")
                .build();
    }

    /** Map a ProductItem back to a ShopifyProductDto for the AI service. */
    private ShopifyProductDto itemToDto(ProductItem item) {
        return ShopifyProductDto.builder()
                .shopifyId(item.getShopifyId())
                .title(item.getTitle())
                .bodyHtml(item.getBodyHtml())
                .vendor(item.getVendor())
                .productType(item.getProductType())
                .tags(item.getTags())
                .handle(item.getHandle())
                .seoTitle(item.getSeoTitle())
                .seoDescription(item.getSeoDescription())
                .build();
    }

    /** Build a ShopifyProductDto carrying the enhanced values for the API push. */
    private ShopifyProductDto itemToEnhancedDto(ProductItem item) {
        return ShopifyProductDto.builder()
                .shopifyId(item.getShopifyId())
                .enhancedTitle(item.getEnhancedTitle())
                .enhancedBodyHtml(item.getEnhancedBodyHtml())
                .enhancedTags(item.getEnhancedTags())
                .enhancedProductType(item.getEnhancedProductType())
                .enhancedHandle(item.getEnhancedHandle())
                .enhancedSeoTitle(item.getEnhancedSeoTitle())
                .enhancedSeoDescription(item.getEnhancedSeoDescription())
                .build();
    }
}
