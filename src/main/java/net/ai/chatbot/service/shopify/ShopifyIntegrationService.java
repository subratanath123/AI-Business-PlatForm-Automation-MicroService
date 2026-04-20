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
                .peek(item -> item.setLifecycleStatus("SYNCED"))
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
     * Parse raw file content using AI to extract product information.
     * Uses OpenAI to intelligently parse ANY format (CSV, JSON, text, unstructured).
     */
    public List<ShopifyProductDto> parseProductsWithAI(String content, String fileName) {
        log.info("Parsing products with AI from file: {}", fileName);
        return aiEnhancementService.parseProductsWithAI(content, fileName);
    }

    /**
     * Generate a product draft from one or more images (base64 data URLs or
     * public image URLs). Treats all supplied images as different views of
     * the same product.
     */
    public List<ShopifyProductDto> parseProductsFromImages(List<String> imageUrls) {
        int count = imageUrls == null ? 0 : imageUrls.size();
        log.info("Parsing product from {} image(s) via vision model", count);
        return aiEnhancementService.parseProductsFromImages(imageUrls);
    }

    /**
     * Create a job from uploaded (CSV-parsed) products.
     */
    public ProductEnhancementJob createUploadJob(String userId, List<ShopifyProductDto> products) {
        List<ProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .peek(item -> item.setLifecycleStatus("DRAFT"))
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
                .localId(UUID.randomUUID().toString())
                .shopifyId(product.getShopifyId())
                .title(product.getTitle())
                .bodyHtml(product.getBodyHtml())
                .vendor(product.getVendor())
                .productType(product.getProductType())
                .tags(product.getTags())
                .status("PENDING")
                .lifecycleStatus("SYNCED")
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

    /**
     * Attach an image (by public URL — e.g. from the internal Asset library) to a Shopify product.
     */
    public Map<String, Object> addProductImage(String userId, String shopifyId,
                                                String src, String alt, Integer position) {
        ShopifyIntegration integration = requireIntegration(userId);
        String accessToken = encryptionUtils.decrypt(integration.getEncryptedAccessToken());
        return shopifyApiService.addProductImage(
                integration.getShopDomain(), accessToken, shopifyId, src, alt, position);
    }

    /**
     * Remove an image from a Shopify product.
     */
    public void deleteProductImage(String userId, String shopifyId, String imageId) {
        ShopifyIntegration integration = requireIntegration(userId);
        String accessToken = encryptionUtils.decrypt(integration.getEncryptedAccessToken());
        shopifyApiService.deleteProductImage(
                integration.getShopDomain(), accessToken, shopifyId, imageId);
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : "";
    }

    /**
     * Get all jobs for a user. Backfills localId / lifecycleStatus on any legacy
     * items that were persisted before these fields existed.
     */
    public List<ProductEnhancementJob> getJobs(String userId) {
        List<ProductEnhancementJob> jobs = jobDao.findByUserIdOrderByCreatedAtDesc(userId);
        for (ProductEnhancementJob job : jobs) {
            if (backfillProductItems(job)) {
                jobDao.save(job);
            }
        }
        return jobs;
    }

    /**
     * Get a specific job. Backfills missing lifecycle fields on read.
     */
    public ProductEnhancementJob getJob(String userId, String jobId) {
        ProductEnhancementJob job = jobDao.findById(jobId)
                .filter(j -> userId.equals(j.getUserId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));
        if (backfillProductItems(job)) {
            jobDao.save(job);
        }
        return job;
    }

    // ── Draft product CRUD ───────────────────────────────────────────────────

    /**
     * Run AI enhancement on a single draft item. Persists the enhanced fields
     * onto the ProductItem so the draft editor can show a diff / adopt the
     * suggestions. Does NOT push anything to Shopify — that's still a separate
     * {@link #publishDraft} step.
     */
    public Map<String, Object> enhanceDraft(String userId, String jobId, String localId) {
        ProductEnhancementJob job = getJob(userId, jobId);
        ProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new RuntimeException("Title is required before running AI enhancement");
        }

        try {
            ShopifyProductDto dto = itemToDto(item);
            ProductItem enhanced = aiEnhancementService.enhanceSingle(dto);

            item.setEnhancedTitle(          enhanced.getEnhancedTitle());
            item.setEnhancedBodyHtml(       enhanced.getEnhancedBodyHtml());
            item.setEnhancedTags(           enhanced.getEnhancedTags());
            item.setEnhancedProductType(    enhanced.getEnhancedProductType());
            item.setEnhancedHandle(         enhanced.getEnhancedHandle());
            item.setEnhancedSeoTitle(       enhanced.getEnhancedSeoTitle());
            item.setEnhancedSeoDescription(enhanced.getEnhancedSeoDescription());
            item.setStatus("ENHANCED");

            // Enhancing a SYNCED/PUBLISHED item creates unpushed changes.
            String current = item.getLifecycleStatus();
            if ("SYNCED".equals(current) || "PUBLISHED".equals(current)) {
                item.setLifecycleStatus("PENDING_SYNC");
            }

            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
            return draftToDetailMap(job, item);
        } catch (Exception e) {
            log.error("AI enhancement failed for draft {} (job {}): {}", localId, jobId, e.getMessage());
            throw new RuntimeException("AI enhancement failed: " + e.getMessage());
        }
    }

    /**
     * Get a single draft / item from a job by its stable local id. Returns a
     * detail-page-shaped map (matching {@link #getProductDetails}) so the
     * frontend can reuse the same editor UI for both Shopify products and
     * unpublished drafts.
     */
    public Map<String, Object> getDraft(String userId, String jobId, String localId) {
        ProductEnhancementJob job = getJob(userId, jobId);
        ProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        return draftToDetailMap(job, item);
    }

    /**
     * Update a draft's editable fields. Pushing to Shopify is a separate step
     * (publishDraft). If the draft was already PUBLISHED/SYNCED, the status
     * becomes PENDING_SYNC until it is republished.
     */
    public Map<String, Object> updateDraft(String userId, String jobId, String localId,
                                           DirectProductUpdateRequest req) {
        ProductEnhancementJob job = getJob(userId, jobId);
        ProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        // When the user saves an edit, the raw field becomes the source of
        // truth again. Clear the matching enhancedX so draftToDetailMap's
        // firstNonBlank(enhancedX, rawX) logic doesn't keep shadowing the new
        // value with a stale AI suggestion.
        if (req.getTitle() != null)          { item.setTitle(req.getTitle());                   item.setEnhancedTitle(null); }
        if (req.getBodyHtml() != null)       { item.setBodyHtml(req.getBodyHtml());             item.setEnhancedBodyHtml(null); }
        if (req.getVendor() != null)         item.setVendor(req.getVendor());
        if (req.getProductType() != null)    { item.setProductType(req.getProductType());       item.setEnhancedProductType(null); }
        if (req.getTags() != null)           { item.setTags(req.getTags());                     item.setEnhancedTags(null); }
        if (req.getHandle() != null)         { item.setHandle(req.getHandle());                 item.setEnhancedHandle(null); }
        if (req.getSeoTitle() != null)       { item.setSeoTitle(req.getSeoTitle());             item.setEnhancedSeoTitle(null); }
        if (req.getSeoDescription() != null) { item.setSeoDescription(req.getSeoDescription()); item.setEnhancedSeoDescription(null); }

        // Draft-editor specific: pricing, SKU, inventory, Shopify status, images.
        if (req.getStatus() != null)             item.setShopifyStatus(req.getStatus());
        if (req.getPrice() != null)              item.setPrice(req.getPrice());
        if (req.getCompareAtPrice() != null)     item.setCompareAtPrice(req.getCompareAtPrice());
        if (req.getSku() != null)                item.setSku(req.getSku());
        if (req.getInventoryQuantity() != null)  item.setInventoryQuantity(req.getInventoryQuantity());
        if (req.getImages() != null)             item.setImages(req.getImages());

        String current = item.getLifecycleStatus();
        if ("SYNCED".equals(current) || "PUBLISHED".equals(current)) {
            item.setLifecycleStatus("PENDING_SYNC");
        }

        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        return draftToDetailMap(job, item);
    }

    /**
     * Publish a draft to Shopify. For DRAFT items this creates a new product;
     * for PENDING_SYNC items it pushes the local edits to the existing product.
     * On success, lifecycleStatus becomes PUBLISHED and shopifyId is set.
     */
    public Map<String, Object> publishDraft(String userId, String jobId, String localId) {
        ShopifyIntegration integration = requireIntegration(userId);
        String accessToken = encryptionUtils.decrypt(integration.getEncryptedAccessToken());

        ProductEnhancementJob job = getJob(userId, jobId);
        ProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        try {
            if (item.getShopifyId() == null || item.getShopifyId().isBlank()) {
                // Brand-new draft: create in Shopify
                String newShopifyId = shopifyApiService.createProduct(
                        integration.getShopDomain(), accessToken, itemToCreateDto(item));
                item.setShopifyId(newShopifyId);
            } else {
                // Existing product with pending edits: push edits
                DirectProductUpdateRequest req = new DirectProductUpdateRequest();
                req.setTitle(item.getTitle());
                req.setBodyHtml(item.getBodyHtml());
                req.setVendor(item.getVendor());
                req.setProductType(item.getProductType());
                req.setTags(item.getTags());
                req.setHandle(item.getHandle());
                req.setSeoTitle(item.getSeoTitle());
                req.setSeoDescription(item.getSeoDescription());
                shopifyApiService.updateProductDirect(
                        integration.getShopDomain(), accessToken, item.getShopifyId(), req);
            }
            item.setLifecycleStatus("PUBLISHED");
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("shopifyId", item.getShopifyId());
            result.put("lifecycleStatus", item.getLifecycleStatus());
            result.put("jobId", job.getId());
            result.put("localId", item.getLocalId());
            return result;
        } catch (Exception e) {
            log.error("Failed to publish draft {} (job {}): {}", localId, jobId, e.getMessage());
            throw new RuntimeException("Failed to publish draft: " + e.getMessage());
        }
    }

    /**
     * Remove a draft item from its job. Only local-only DRAFT items can be deleted
     * here — for SYNCED/PUBLISHED items the caller should use the regular
     * Shopify delete flow (not implemented).
     */
    public void deleteDraft(String userId, String jobId, String localId) {
        ProductEnhancementJob job = getJob(userId, jobId);
        ProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        if (!"DRAFT".equals(item.getLifecycleStatus())) {
            throw new RuntimeException(
                    "Only DRAFT items can be deleted locally. Current status: " + item.getLifecycleStatus());
        }

        boolean removedRaw = job.getRawProducts() != null
                && job.getRawProducts().removeIf(p -> localId.equals(p.getLocalId()));
        boolean removedEnh = job.getEnhancedProducts() != null
                && job.getEnhancedProducts().removeIf(p -> localId.equals(p.getLocalId()));
        if (!removedRaw && !removedEnh) {
            throw new RuntimeException("Draft not found in job: " + localId);
        }
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
    }

    // ── Backfill + lookup helpers ────────────────────────────────────────────

    private boolean backfillProductItems(ProductEnhancementJob job) {
        boolean mutated = false;
        mutated |= backfillList(job.getRawProducts());
        mutated |= backfillList(job.getEnhancedProducts());
        return mutated;
    }

    private boolean backfillList(List<ProductItem> items) {
        if (items == null) return false;
        boolean mutated = false;
        for (ProductItem item : items) {
            if (item.getLocalId() == null || item.getLocalId().isBlank()) {
                item.setLocalId(UUID.randomUUID().toString());
                mutated = true;
            }
            if (item.getLifecycleStatus() == null || item.getLifecycleStatus().isBlank()) {
                boolean inShopify = item.getShopifyId() != null && !item.getShopifyId().isBlank();
                item.setLifecycleStatus(inShopify ? "SYNCED" : "DRAFT");
                mutated = true;
            }
        }
        return mutated;
    }

    private java.util.Optional<ProductItem> findItem(ProductEnhancementJob job, String localId) {
        if (localId == null || localId.isBlank()) return java.util.Optional.empty();
        if (job.getEnhancedProducts() != null) {
            for (ProductItem p : job.getEnhancedProducts()) {
                if (localId.equals(p.getLocalId())) return java.util.Optional.of(p);
            }
        }
        if (job.getRawProducts() != null) {
            for (ProductItem p : job.getRawProducts()) {
                if (localId.equals(p.getLocalId())) return java.util.Optional.of(p);
            }
        }
        return java.util.Optional.empty();
    }

    /** Shape a draft item into the same map format the product detail page expects. */
    private Map<String, Object> draftToDetailMap(ProductEnhancementJob job, ProductItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId",           job.getId());
        result.put("localId",         item.getLocalId());
        result.put("lifecycleStatus", nullToEmpty(item.getLifecycleStatus()));
        result.put("id",              nullToEmpty(item.getShopifyId()));
        result.put("shopifyId",       nullToEmpty(item.getShopifyId()));

        // Prefer enhanced values (they are the user's latest edits if AI enhanced them)
        result.put("title",          firstNonBlank(item.getEnhancedTitle(),          item.getTitle()));
        result.put("bodyHtml",       firstNonBlank(item.getEnhancedBodyHtml(),       item.getBodyHtml()));
        result.put("vendor",         nullToEmpty(item.getVendor()));
        result.put("productType",    firstNonBlank(item.getEnhancedProductType(),    item.getProductType()));
        result.put("tags",           firstNonBlank(item.getEnhancedTags(),           item.getTags()));
        result.put("handle",         firstNonBlank(item.getEnhancedHandle(),         item.getHandle()));
        result.put("seoTitle",       firstNonBlank(item.getEnhancedSeoTitle(),       item.getSeoTitle()));
        result.put("seoDescription", firstNonBlank(item.getEnhancedSeoDescription(), item.getSeoDescription()));

        // Shopify status the user wants when the draft is published. Default
        // to "active" so the product shows up on the storefront immediately.
        result.put("status",         firstNonBlank(item.getShopifyStatus(), "active"));
        result.put("publishedScope", "");
        result.put("templateSuffix", "");
        result.put("createdAt",      job.getCreatedAt() == null ? "" : job.getCreatedAt().toString());
        result.put("updatedAt",      job.getUpdatedAt() == null ? "" : job.getUpdatedAt().toString());
        result.put("publishedAt",    "");

        // Draft-editor: expose the default-variant + images so the frontend can
        // render and edit them even before the product exists in Shopify.
        result.put("price",             nullToEmpty(item.getPrice()));
        result.put("compareAtPrice",    nullToEmpty(item.getCompareAtPrice()));
        result.put("sku",               nullToEmpty(item.getSku()));
        result.put("inventoryQuantity", item.getInventoryQuantity() == null ? 0 : item.getInventoryQuantity());

        List<Map<String, Object>> draftImages = new ArrayList<>();
        List<String> imageUrls = item.getImages();
        if (imageUrls != null) {
            int position = 1;
            for (String src : imageUrls) {
                if (src == null || src.isBlank()) continue;
                Map<String, Object> img = new LinkedHashMap<>();
                img.put("id",       "local-" + position);
                img.put("src",      src);
                img.put("position", position++);
                img.put("alt",      nullToEmpty(item.getTitle()));
                draftImages.add(img);
            }
        }

        result.put("variants", new ArrayList<>());
        result.put("options",  new ArrayList<>());
        result.put("images",   draftImages);
        return result;
    }

    /** Build a DTO for creating a new Shopify product from a local draft. */
    private ShopifyProductDto itemToCreateDto(ProductItem item) {
        // For drafts, enhanced fields may be the user's canonical edits. Prefer them when present.
        return ShopifyProductDto.builder()
                .title(firstNonBlank(item.getEnhancedTitle(),          item.getTitle()))
                .bodyHtml(firstNonBlank(item.getEnhancedBodyHtml(),    item.getBodyHtml()))
                .vendor(item.getVendor())
                .productType(firstNonBlank(item.getEnhancedProductType(), item.getProductType()))
                .tags(firstNonBlank(item.getEnhancedTags(),            item.getTags()))
                .handle(firstNonBlank(item.getEnhancedHandle(),        item.getHandle()))
                .seoTitle(firstNonBlank(item.getEnhancedSeoTitle(),    item.getSeoTitle()))
                .seoDescription(firstNonBlank(item.getEnhancedSeoDescription(), item.getSeoDescription()))
                .status(firstNonBlank(item.getShopifyStatus(),         "active"))
                .price(item.getPrice())
                .compareAtPrice(item.getCompareAtPrice())
                .sku(item.getSku())
                .inventoryQuantity(item.getInventoryQuantity())
                .images(item.getImages())
                .build();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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
                .localId(UUID.randomUUID().toString())
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
