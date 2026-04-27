package net.ai.chatbot.service.woocommerce;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.ProductEnhancementJobDao;
import net.ai.chatbot.dao.WooCommerceIntegrationDao;
import net.ai.chatbot.dao.WooCommercePendingConnectionDao;
import net.ai.chatbot.dto.shopify.DirectProductUpdateRequest;
import net.ai.chatbot.dto.shopify.ShopifyProductDto;
import net.ai.chatbot.entity.ProductEnhancementJob;
import net.ai.chatbot.entity.ProductEnhancementJob.ProductItem;
import net.ai.chatbot.entity.WooCommerceIntegration;
import net.ai.chatbot.entity.WooCommercePendingConnection;
import net.ai.chatbot.service.shopify.ProductAIEnhancementService;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * WooCommerce counterpart to {@link net.ai.chatbot.service.shopify.ShopifyIntegrationService}.
 * Shares {@link ProductEnhancementJob} and {@link ProductAIEnhancementService}
 * with Shopify; the only difference is the platform string ("WOOCOMMERCE"),
 * the store-connection entity, and the REST client.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WooCommerceIntegrationService {

    private final WooCommerceIntegrationDao wooDao;
    private final WooCommercePendingConnectionDao pendingDao;
    private final ProductEnhancementJobDao jobDao;
    private final WooCommerceApiService wooApiService;
    private final ProductAIEnhancementService aiEnhancementService;
    private final EncryptionUtils encryptionUtils;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    // ── WC Auth handshake ────────────────────────────────────────────────────

    /**
     * Create a short-lived pending-connection record keyed by {@code nonce}.
     * The browser embeds this nonce in both the {@code return_url} (GET to us)
     * and {@code callback_url} (server-to-server POST from the WC store) so
     * the two sides of the handshake can correlate without shared sessions.
     */
    public WooCommercePendingConnection initAuth(String userId, String storeUrl) {
        String normalized = normalizeStoreUrl(storeUrl);
        WooCommercePendingConnection pending = WooCommercePendingConnection.builder()
                .id(UUID.randomUUID().toString())
                .nonce(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .storeUrl(normalized)
                .credentialsReceived(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return pendingDao.save(pending);
    }

    /**
     * Called from the public {@code /api/public/woocommerce/auth-callback/{nonce}}
     * endpoint — this is a server-to-server POST from the WC store itself with
     * the freshly-issued consumer key + secret. The credentials are encrypted
     * and parked on the pending record for the browser to claim.
     */
    public void receiveAuthCallback(String nonce, String consumerKey,
                                    String consumerSecret, String keyPermissions) {
        WooCommercePendingConnection pending = pendingDao.findByNonce(nonce)
                .orElseThrow(() -> new RuntimeException("Unknown connection nonce"));
        pending.setEncryptedConsumerKey(encryptionUtils.encrypt(consumerKey));
        pending.setEncryptedConsumerSecret(encryptionUtils.encrypt(consumerSecret));
        pending.setKeyPermissions(keyPermissions);
        pending.setCredentialsReceived(true);
        pending.setUpdatedAt(Instant.now());
        pendingDao.save(pending);
        log.info("Parked WC credentials for nonce {} (scope {})", nonce, keyPermissions);
    }

    /**
     * Browser-side finalize call: claims the pending credentials for the
     * current user, promotes them to a real {@link WooCommerceIntegration},
     * tests the connection and stores the resulting display name.
     */
    public WooCommerceIntegration finalizeAuth(String userId, String nonce, String storeName) {
        WooCommercePendingConnection pending = pendingDao.findByNonce(nonce)
                .orElseThrow(() -> new RuntimeException("Unknown connection nonce"));
        if (!userId.equals(pending.getUserId())) {
            throw new RuntimeException("Nonce does not belong to the current user");
        }
        if (!pending.isCredentialsReceived()) {
            throw new RuntimeException("WooCommerce has not yet delivered credentials for this connection");
        }

        String consumerKey    = encryptionUtils.decrypt(pending.getEncryptedConsumerKey());
        String consumerSecret = encryptionUtils.decrypt(pending.getEncryptedConsumerSecret());

        String resolvedName;
        try {
            resolvedName = wooApiService.testConnection(pending.getStoreUrl(), consumerKey, consumerSecret);
        } catch (Exception e) {
            pendingDao.deleteByNonce(nonce);
            throw new RuntimeException("Could not verify WooCommerce credentials: " + e.getMessage());
        }
        if (storeName != null && !storeName.isBlank()) resolvedName = storeName;

        WooCommerceIntegration saved = saveCredentials(userId, pending.getStoreUrl(),
                consumerKey, consumerSecret, resolvedName);
        pendingDao.deleteByNonce(nonce);
        return saved;
    }

    /**
     * Internal helper that inserts / updates a {@link WooCommerceIntegration}
     * with already-plaintext credentials and re-encrypts them. Also marks the
     * integration active so downstream calls pick it up automatically.
     */
    private WooCommerceIntegration saveCredentials(String userId, String storeUrl,
                                                   String consumerKey, String consumerSecret,
                                                   String storeName) {
        String normalized = normalizeStoreUrl(storeUrl);
        WooCommerceIntegration existing = wooDao
                .findByUserIdAndStoreUrl(userId, normalized).orElse(null);

        String encKey    = encryptionUtils.encrypt(consumerKey);
        String encSecret = encryptionUtils.encrypt(consumerSecret);

        if (existing != null) {
            existing.setStoreName(storeName);
            existing.setEncryptedConsumerKey(encKey);
            existing.setEncryptedConsumerSecret(encSecret);
            existing.setConnected(true);
            existing.setUpdatedAt(Instant.now());
            wooDao.save(existing);
            activateStore(userId, normalized);
            return wooDao.findByUserIdAndStoreUrl(userId, normalized).orElse(existing);
        }

        WooCommerceIntegration integration = WooCommerceIntegration.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .storeUrl(normalized)
                .storeName(storeName)
                .encryptedConsumerKey(encKey)
                .encryptedConsumerSecret(encSecret)
                .connected(true)
                .webhookEnabled(false)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        wooDao.save(integration);
        activateStore(userId, normalized);
        return wooDao.findByUserIdAndStoreUrl(userId, normalized).orElse(integration);
    }

    // ── Store management ─────────────────────────────────────────────────────

    public List<WooCommerceIntegration> listStores(String userId) {
        List<WooCommerceIntegration> stores = wooDao.findAllByUserId(userId);
        boolean anyActive = stores.stream().anyMatch(WooCommerceIntegration::isActive);
        if (!anyActive && !stores.isEmpty()) {
            activateStore(userId, stores.get(0).getStoreUrl());
            return wooDao.findAllByUserId(userId);
        }
        return stores;
    }

    public WooCommerceIntegration switchStore(String userId, String storeUrl) {
        String normalized = normalizeStoreUrl(storeUrl);
        WooCommerceIntegration target = wooDao.findByUserIdAndStoreUrl(userId, normalized)
                .orElseThrow(() -> new RuntimeException("Store not connected: " + normalized));
        activateStore(userId, normalized);
        return wooDao.findByUserIdAndStoreUrl(userId, target.getStoreUrl()).orElse(target);
    }

    public void disconnectStore(String userId, String storeUrl) {
        String normalized = normalizeStoreUrl(storeUrl);
        WooCommerceIntegration integration = wooDao.findByUserIdAndStoreUrl(userId, normalized)
                .orElseThrow(() -> new RuntimeException("Store not connected: " + normalized));

        if (integration.getWebhookId() != null) {
            try {
                String[] creds = decryptCredentials(integration);
                wooApiService.deleteWebhook(integration.getStoreUrl(), creds[0], creds[1],
                        integration.getWebhookId());
            } catch (Exception e) {
                log.warn("Could not delete webhook during disconnect: {}", e.getMessage());
            }
        }

        wooDao.deleteByUserIdAndStoreUrl(userId, normalized);
        if (integration.isActive()) {
            wooDao.findAllByUserId(userId).stream().findFirst()
                    .ifPresent(next -> activateStore(userId, next.getStoreUrl()));
        }
    }

    public void disconnectAllStores(String userId) {
        for (WooCommerceIntegration integration : wooDao.findAllByUserId(userId)) {
            if (integration.getWebhookId() != null) {
                try {
                    String[] creds = decryptCredentials(integration);
                    wooApiService.deleteWebhook(integration.getStoreUrl(), creds[0], creds[1],
                            integration.getWebhookId());
                } catch (Exception e) {
                    log.warn("Could not delete webhook during disconnect all: {}", e.getMessage());
                }
            }
        }
        wooDao.deleteByUserId(userId);
    }

    public WooCommerceIntegration getIntegration(String userId) {
        WooCommerceIntegration active = wooDao.findByUserIdAndActiveTrue(userId).orElse(null);
        if (active != null) return active;
        WooCommerceIntegration any = wooDao.findByUserId(userId).orElse(null);
        if (any != null) {
            activateStore(userId, any.getStoreUrl());
            return wooDao.findByUserIdAndStoreUrl(userId, any.getStoreUrl()).orElse(any);
        }
        return null;
    }

    private void activateStore(String userId, String storeUrl) {
        List<WooCommerceIntegration> all = wooDao.findAllByUserId(userId);
        for (WooCommerceIntegration s : all) {
            boolean shouldBeActive = s.getStoreUrl().equals(storeUrl);
            if (s.isActive() != shouldBeActive) {
                s.setActive(shouldBeActive);
                s.setUpdatedAt(Instant.now());
                wooDao.save(s);
            }
        }
    }

    // ── Sync / parse / upload ────────────────────────────────────────────────

    public ProductEnhancementJob syncProductsFromStore(String userId, int limit) {
        WooCommerceIntegration integration = requireIntegration(userId);
        String[] creds = decryptCredentials(integration);

        List<ShopifyProductDto> products = wooApiService.listProducts(
                integration.getStoreUrl(), creds[0], creds[1], limit);

        List<ProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .peek(item -> item.setLifecycleStatus("SYNCED"))
                .collect(Collectors.toList());

        ProductEnhancementJob job = ProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("WOOCOMMERCE")
                .status("PENDING")
                .rawProducts(rawItems)
                .source("SYNC")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return jobDao.save(job);
    }

    public List<ShopifyProductDto> parseProductsWithAI(String content, String fileName) {
        log.info("WC: parsing products with AI from file {}", fileName);
        return aiEnhancementService.parseProductsWithAI(content, fileName);
    }

    public List<ShopifyProductDto> parseProductsFromImages(List<String> imageUrls) {
        log.info("WC: parsing product from {} image(s)", imageUrls == null ? 0 : imageUrls.size());
        return aiEnhancementService.parseProductsFromImages(imageUrls);
    }

    public ProductEnhancementJob createUploadJob(String userId, List<ShopifyProductDto> products) {
        List<ProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .peek(item -> item.setLifecycleStatus("DRAFT"))
                .collect(Collectors.toList());

        ProductEnhancementJob job = ProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("WOOCOMMERCE")
                .status("PENDING")
                .rawProducts(rawItems)
                .source("UPLOAD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return jobDao.save(job);
    }

    @Async
    public void enhanceJobAsync(String jobId, String userId) {
        ProductEnhancementJob job = jobDao.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus("PROCESSING");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);

        try {
            List<ShopifyProductDto> rawDtos = job.getRawProducts().stream()
                    .map(this::itemToDto).collect(Collectors.toList());
            List<ProductItem> enhanced = aiEnhancementService.enhanceProducts(rawDtos);

            job.setEnhancedProducts(enhanced);
            job.setStatus("ENHANCED");
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
            log.info("WC enhancement complete for job {}: {} products", jobId, enhanced.size());
        } catch (Exception e) {
            log.error("WC enhancement failed for job {}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
        }
    }

    public void pushProductsToWooCommerce(String userId, String jobId, List<String> targetIds) {
        WooCommerceIntegration integration = requireIntegration(userId);
        String[] creds = decryptCredentials(integration);

        ProductEnhancementJob job = jobDao.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        if (!"ENHANCED".equals(job.getStatus())) {
            throw new RuntimeException("Job must be in ENHANCED status before publishing");
        }

        List<ProductItem> toPublish = job.getEnhancedProducts().stream()
                .filter(p -> targetIds == null || targetIds.isEmpty()
                        || targetIds.contains(p.getWooId())
                        || targetIds.contains(p.getLocalId()))
                .collect(Collectors.toList());

        int successCount = 0;
        for (ProductItem item : toPublish) {
            try {
                wooApiService.updateProduct(integration.getStoreUrl(), creds[0], creds[1],
                        itemToEnhancedDto(item));
                item.setStatus("PUBLISHED");
                successCount++;
            } catch (Exception e) {
                log.error("Failed to push WC product {}: {}", item.getWooId(), e.getMessage());
                item.setStatus("FAILED");
            }
        }

        job.setStatus("PUBLISHED");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        log.info("Pushed {}/{} products to WooCommerce for job {}", successCount, toPublish.size(), jobId);
    }

    // ── Webhooks ─────────────────────────────────────────────────────────────

    public WooCommerceIntegration toggleWebhook(String userId, boolean enable) {
        WooCommerceIntegration integration = requireIntegration(userId);
        String[] creds = decryptCredentials(integration);

        if (enable && !integration.isWebhookEnabled()) {
            String callbackUrl = appBaseUrl + "/api/public/woocommerce/webhook/" + userId;
            String webhookId = wooApiService.registerProductCreatedWebhook(
                    integration.getStoreUrl(), creds[0], creds[1], callbackUrl);
            integration.setWebhookId(webhookId);
            integration.setWebhookEnabled(true);
        } else if (!enable && integration.isWebhookEnabled() && integration.getWebhookId() != null) {
            wooApiService.deleteWebhook(integration.getStoreUrl(), creds[0], creds[1],
                    integration.getWebhookId());
            integration.setWebhookId(null);
            integration.setWebhookEnabled(false);
        }

        integration.setUpdatedAt(Instant.now());
        return wooDao.save(integration);
    }

    @Async
    public void handleProductCreatedWebhook(String userId, String storeUrlHeader,
                                            ShopifyProductDto product) {
        log.info("WC webhook received: new product '{}' for user {}", product.getTitle(), userId);

        WooCommerceIntegration integration;
        if (storeUrlHeader != null && !storeUrlHeader.isBlank()) {
            integration = wooDao.findByUserIdAndStoreUrl(userId, normalizeStoreUrl(storeUrlHeader))
                    .orElse(null);
        } else {
            integration = getIntegration(userId);
        }
        if (integration == null || !integration.isWebhookEnabled()) {
            log.warn("Ignoring WC webhook for user {} — integration missing or webhook disabled", userId);
            return;
        }

        String[] creds = decryptCredentials(integration);

        List<ProductItem> rawItems = new ArrayList<>();
        rawItems.add(ProductItem.builder()
                .localId(UUID.randomUUID().toString())
                .wooId(product.getWooId())
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
                .platform("WOOCOMMERCE")
                .status("PROCESSING")
                .rawProducts(rawItems)
                .source("WEBHOOK")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        jobDao.save(job);

        try {
            ProductItem enhanced = aiEnhancementService.enhanceSingle(product);
            enhanced.setWooId(product.getWooId());
            wooApiService.updateProduct(integration.getStoreUrl(), creds[0], creds[1],
                    itemToEnhancedDto(enhanced));
            enhanced.setStatus("PUBLISHED");
            job.setEnhancedProducts(List.of(enhanced));
            job.setStatus("PUBLISHED");
        } catch (Exception e) {
            log.error("WC webhook auto-enhance failed for product {}", product.getWooId(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
    }

    // ── Single-product direct edit (live WC product) ─────────────────────────

    public Map<String, Object> getProductDetails(String userId, String wooId) {
        WooCommerceIntegration integration = requireIntegration(userId);
        String[] creds = decryptCredentials(integration);
        return wooApiService.getFullProduct(integration.getStoreUrl(), creds[0], creds[1], wooId);
    }

    public Map<String, Object> enhanceSingleProductDirect(String userId, String wooId) {
        WooCommerceIntegration integration = requireIntegration(userId);
        String[] creds = decryptCredentials(integration);

        Map<String, Object> productMap = wooApiService.getFullProduct(
                integration.getStoreUrl(), creds[0], creds[1], wooId);

        ShopifyProductDto dto = ShopifyProductDto.builder()
                .wooId(wooId)
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

    public void updateProductDirect(String userId, String wooId, DirectProductUpdateRequest req) {
        WooCommerceIntegration integration = requireIntegration(userId);
        String[] creds = decryptCredentials(integration);
        wooApiService.updateProductDirect(integration.getStoreUrl(), creds[0], creds[1], wooId, req);
        log.info("Direct WC product update applied for user {} product {}", userId, wooId);
    }

    public Map<String, Object> addProductImage(String userId, String wooId,
                                               String src, String alt, Integer position) {
        WooCommerceIntegration integration = requireIntegration(userId);
        String[] creds = decryptCredentials(integration);
        return wooApiService.addProductImage(integration.getStoreUrl(), creds[0], creds[1],
                wooId, src, alt, position);
    }

    public void deleteProductImage(String userId, String wooId, String imageId) {
        WooCommerceIntegration integration = requireIntegration(userId);
        String[] creds = decryptCredentials(integration);
        wooApiService.deleteProductImage(integration.getStoreUrl(), creds[0], creds[1], wooId, imageId);
    }

    // ── Jobs ─────────────────────────────────────────────────────────────────

    public List<ProductEnhancementJob> getJobs(String userId) {
        List<ProductEnhancementJob> all = jobDao.findByUserIdOrderByCreatedAtDesc(userId);
        List<ProductEnhancementJob> out = new ArrayList<>();
        for (ProductEnhancementJob job : all) {
            if (!"WOOCOMMERCE".equalsIgnoreCase(job.getPlatform())) continue;
            if (backfillProductItems(job)) jobDao.save(job);
            out.add(job);
        }
        return out;
    }

    public ProductEnhancementJob getJob(String userId, String jobId) {
        ProductEnhancementJob job = jobDao.findById(jobId)
                .filter(j -> userId.equals(j.getUserId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));
        if (backfillProductItems(job)) jobDao.save(job);
        return job;
    }

    // ── Drafts ───────────────────────────────────────────────────────────────

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

            String current = item.getLifecycleStatus();
            if ("SYNCED".equals(current) || "PUBLISHED".equals(current)) {
                item.setLifecycleStatus("PENDING_SYNC");
            }

            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
            return draftToDetailMap(job, item);
        } catch (Exception e) {
            log.error("WC AI enhancement failed for draft {} (job {}): {}", localId, jobId, e.getMessage());
            throw new RuntimeException("AI enhancement failed: " + e.getMessage());
        }
    }

    public Map<String, Object> getDraft(String userId, String jobId, String localId) {
        ProductEnhancementJob job = getJob(userId, jobId);
        ProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        return draftToDetailMap(job, item);
    }

    public Map<String, Object> updateDraft(String userId, String jobId, String localId,
                                           DirectProductUpdateRequest req) {
        ProductEnhancementJob job = getJob(userId, jobId);
        ProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        if (req.getTitle() != null)          { item.setTitle(req.getTitle());                   item.setEnhancedTitle(null); }
        if (req.getBodyHtml() != null)       { item.setBodyHtml(req.getBodyHtml());             item.setEnhancedBodyHtml(null); }
        if (req.getVendor() != null)         item.setVendor(req.getVendor());
        if (req.getProductType() != null)    { item.setProductType(req.getProductType());       item.setEnhancedProductType(null); }
        if (req.getTags() != null)           { item.setTags(req.getTags());                     item.setEnhancedTags(null); }
        if (req.getHandle() != null)         { item.setHandle(req.getHandle());                 item.setEnhancedHandle(null); }
        if (req.getSeoTitle() != null)       { item.setSeoTitle(req.getSeoTitle());             item.setEnhancedSeoTitle(null); }
        if (req.getSeoDescription() != null) { item.setSeoDescription(req.getSeoDescription()); item.setEnhancedSeoDescription(null); }

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

    public Map<String, Object> publishDraft(String userId, String jobId, String localId) {
        WooCommerceIntegration integration = requireIntegration(userId);
        String[] creds = decryptCredentials(integration);

        ProductEnhancementJob job = getJob(userId, jobId);
        ProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        try {
            if (item.getWooId() == null || item.getWooId().isBlank()) {
                String newWooId = wooApiService.createProduct(
                        integration.getStoreUrl(), creds[0], creds[1], itemToCreateDto(item));
                item.setWooId(newWooId);
            } else {
                DirectProductUpdateRequest req = new DirectProductUpdateRequest();
                req.setTitle(item.getTitle());
                req.setBodyHtml(item.getBodyHtml());
                req.setVendor(item.getVendor());
                req.setProductType(item.getProductType());
                req.setTags(item.getTags());
                req.setHandle(item.getHandle());
                req.setSeoTitle(item.getSeoTitle());
                req.setSeoDescription(item.getSeoDescription());
                req.setPrice(item.getPrice());
                req.setCompareAtPrice(item.getCompareAtPrice());
                req.setSku(item.getSku());
                req.setInventoryQuantity(item.getInventoryQuantity());
                req.setStatus(item.getShopifyStatus());
                wooApiService.updateProductDirect(integration.getStoreUrl(), creds[0], creds[1],
                        item.getWooId(), req);
            }
            item.setLifecycleStatus("PUBLISHED");
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("wooId", item.getWooId());
            result.put("shopifyId", item.getWooId()); // alias so the frontend's existing router can use either
            result.put("lifecycleStatus", item.getLifecycleStatus());
            result.put("jobId", job.getId());
            result.put("localId", item.getLocalId());
            return result;
        } catch (Exception e) {
            log.error("Failed to publish WC draft {} (job {}): {}", localId, jobId, e.getMessage());
            throw new RuntimeException("Failed to publish draft: " + e.getMessage());
        }
    }

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

    // ── Helpers ──────────────────────────────────────────────────────────────

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
                boolean inStore = (item.getWooId() != null && !item.getWooId().isBlank())
                        || (item.getShopifyId() != null && !item.getShopifyId().isBlank());
                item.setLifecycleStatus(inStore ? "SYNCED" : "DRAFT");
                mutated = true;
            }
        }
        return mutated;
    }

    private Optional<ProductItem> findItem(ProductEnhancementJob job, String localId) {
        if (localId == null || localId.isBlank()) return Optional.empty();
        if (job.getEnhancedProducts() != null) {
            for (ProductItem p : job.getEnhancedProducts()) {
                if (localId.equals(p.getLocalId())) return Optional.of(p);
            }
        }
        if (job.getRawProducts() != null) {
            for (ProductItem p : job.getRawProducts()) {
                if (localId.equals(p.getLocalId())) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> draftToDetailMap(ProductEnhancementJob job, ProductItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId",           job.getId());
        result.put("localId",         item.getLocalId());
        result.put("lifecycleStatus", nullToEmpty(item.getLifecycleStatus()));
        result.put("id",              nullToEmpty(item.getWooId()));
        result.put("wooId",           nullToEmpty(item.getWooId()));

        result.put("title",          firstNonBlank(item.getEnhancedTitle(),          item.getTitle()));
        result.put("bodyHtml",       firstNonBlank(item.getEnhancedBodyHtml(),       item.getBodyHtml()));
        result.put("vendor",         nullToEmpty(item.getVendor()));
        result.put("productType",    firstNonBlank(item.getEnhancedProductType(),    item.getProductType()));
        result.put("tags",           firstNonBlank(item.getEnhancedTags(),           item.getTags()));
        result.put("handle",         firstNonBlank(item.getEnhancedHandle(),         item.getHandle()));
        result.put("seoTitle",       firstNonBlank(item.getEnhancedSeoTitle(),       item.getSeoTitle()));
        result.put("seoDescription", firstNonBlank(item.getEnhancedSeoDescription(), item.getSeoDescription()));

        result.put("status",         firstNonBlank(item.getShopifyStatus(), "active"));
        result.put("publishedScope", "");
        result.put("templateSuffix", "");
        result.put("createdAt",      job.getCreatedAt() == null ? "" : job.getCreatedAt().toString());
        result.put("updatedAt",      job.getUpdatedAt() == null ? "" : job.getUpdatedAt().toString());
        result.put("publishedAt",    "");

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

    private ShopifyProductDto itemToCreateDto(ProductItem item) {
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

    private ProductItem dtoToRawItem(ShopifyProductDto p) {
        return ProductItem.builder()
                .localId(UUID.randomUUID().toString())
                .wooId(p.getWooId())
                .title(p.getTitle())
                .bodyHtml(p.getBodyHtml())
                .vendor(p.getVendor())
                .productType(p.getProductType())
                .tags(p.getTags())
                .handle(p.getHandle())
                .seoTitle(p.getSeoTitle())
                .seoDescription(p.getSeoDescription())
                .price(p.getPrice())
                .compareAtPrice(p.getCompareAtPrice())
                .sku(p.getSku())
                .inventoryQuantity(p.getInventoryQuantity())
                .status("PENDING")
                .build();
    }

    private ShopifyProductDto itemToDto(ProductItem item) {
        return ShopifyProductDto.builder()
                .wooId(item.getWooId())
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

    private ShopifyProductDto itemToEnhancedDto(ProductItem item) {
        return ShopifyProductDto.builder()
                .wooId(item.getWooId())
                .enhancedTitle(item.getEnhancedTitle())
                .enhancedBodyHtml(item.getEnhancedBodyHtml())
                .enhancedTags(item.getEnhancedTags())
                .enhancedProductType(item.getEnhancedProductType())
                .enhancedHandle(item.getEnhancedHandle())
                .enhancedSeoTitle(item.getEnhancedSeoTitle())
                .enhancedSeoDescription(item.getEnhancedSeoDescription())
                .build();
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : "";
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private WooCommerceIntegration requireIntegration(String userId) {
        WooCommerceIntegration active = getIntegration(userId);
        if (active == null) {
            throw new RuntimeException("No WooCommerce integration found. Please connect your store first.");
        }
        return active;
    }

    /** Returns [consumerKey, consumerSecret] decrypted. */
    private String[] decryptCredentials(WooCommerceIntegration integration) {
        return new String[]{
                encryptionUtils.decrypt(integration.getEncryptedConsumerKey()),
                encryptionUtils.decrypt(integration.getEncryptedConsumerSecret()),
        };
    }

    /** Strip trailing slash, lowercase the host portion, ensure an https:// prefix. */
    private static String normalizeStoreUrl(String storeUrl) {
        if (storeUrl == null) return "";
        String u = storeUrl.trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        return u;
    }
}
