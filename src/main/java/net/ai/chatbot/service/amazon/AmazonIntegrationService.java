package net.ai.chatbot.service.amazon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.AmazonIntegrationDao;
import net.ai.chatbot.dao.AmazonPendingConnectionDao;
import net.ai.chatbot.dao.AmazonProductEnhancementJobDao;
import net.ai.chatbot.dto.amazon.AmazonDirectProductUpdateRequest;
import net.ai.chatbot.dto.amazon.AmazonProductDto;
import net.ai.chatbot.dto.shopify.ShopifyProductDto;
import net.ai.chatbot.entity.AmazonIntegration;
import net.ai.chatbot.entity.AmazonPendingConnection;
import net.ai.chatbot.entity.AmazonProductEnhancementJob;
import net.ai.chatbot.entity.AmazonProductEnhancementJob.AmazonProductItem;
import net.ai.chatbot.entity.ProductEnhancementJob;
import net.ai.chatbot.service.shopify.ProductAIEnhancementService;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Amazon counterpart to {@link net.ai.chatbot.service.shopify.ShopifyIntegrationService}
 * and {@link net.ai.chatbot.service.woocommerce.WooCommerceIntegrationService}.
 * <p>
 * Uses a dedicated {@link AmazonProductEnhancementJob} collection (rather
 * than the Shopify/Woo one) because Amazon listings carry concepts that
 * don't map cleanly onto the Shopify schema (bullet points, seller SKUs,
 * marketplace scoping, product-type slugs). AI enhancement is shared via
 * {@link ProductAIEnhancementService} by projecting the Amazon product
 * through {@link ShopifyProductDto}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AmazonIntegrationService {

    /** Default marketplace id (US) used when the caller doesn't specify one. */
    private static final String DEFAULT_MARKETPLACE_ID = "ATVPDKIKX0DER";
    /** Notification types we subscribe to when SQS is enabled. */
    private static final List<String> SQS_NOTIFICATION_TYPES =
            List.of("LISTINGS_ITEM_STATUS_CHANGE", "LISTINGS_ITEM_ISSUES_CHANGE");

    private final AmazonIntegrationDao amazonDao;
    private final AmazonPendingConnectionDao pendingDao;
    private final AmazonProductEnhancementJobDao jobDao;
    private final AmazonSpApiService spApiService;
    private final LwaTokenService lwaTokenService;
    private final ProductAIEnhancementService aiEnhancementService;
    private final EncryptionUtils encryptionUtils;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    // ──────────────────────────────────────────────────────────────────────────
    // LWA OAuth handshake
    // ──────────────────────────────────────────────────────────────────────────

    /** Creates a pending-connection record keyed by a fresh CSRF {@code state}. */
    public AmazonPendingConnection initAuth(String userId, String region) {
        AmazonPendingConnection pending = AmazonPendingConnection.builder()
                .id(UUID.randomUUID().toString())
                .state(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .region(normalizeRegion(region))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return pendingDao.save(pending);
    }

    /**
     * Browser-side finalize: exchange the one-time {@code spapi_oauth_code}
     * for a refresh token, verify we can mint an access token, persist the
     * integration, and wipe the pending record.
     */
    public AmazonIntegration finalizeAuth(String userId, String state, String spapiOauthCode,
                                          String sellingPartnerId, String storeName,
                                          String defaultProductType, String redirectUri) {
        AmazonPendingConnection pending = pendingDao.findByState(state)
                .orElseThrow(() -> new RuntimeException("Unknown connection state"));
        if (!userId.equals(pending.getUserId())) {
            throw new RuntimeException("State does not belong to the current user");
        }

        Map<String, Object> tokens = lwaTokenService.exchangeAuthorizationCode(spapiOauthCode, redirectUri);
        String refreshToken = Objects.toString(tokens.get("refresh_token"), null);
        String accessToken  = Objects.toString(tokens.get("access_token"), null);
        if (refreshToken == null || refreshToken.isBlank()) {
            pendingDao.deleteByState(state);
            throw new RuntimeException("Amazon did not return a refresh_token — is the SP-API app approved?");
        }

        // Enumerate marketplaces so we can pick a sensible default active marketplace.
        List<String> availableMarketplaceIds = new ArrayList<>();
        String activeMarketplaceId = null;
        try {
            if (accessToken != null) {
                List<Map<String, Object>> mps = spApiService.getMarketplaceParticipations(
                        accessToken, pending.getRegion());
                for (Map<String, Object> m : mps) {
                    String id = Objects.toString(m.get("id"), "");
                    if (!id.isBlank()) availableMarketplaceIds.add(id);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch marketplaces for seller {}: {}", sellingPartnerId, e.getMessage());
        }
        if (!availableMarketplaceIds.isEmpty()) {
            activeMarketplaceId = availableMarketplaceIds.contains(DEFAULT_MARKETPLACE_ID)
                    ? DEFAULT_MARKETPLACE_ID : availableMarketplaceIds.get(0);
        } else {
            activeMarketplaceId = DEFAULT_MARKETPLACE_ID;
        }

        AmazonIntegration saved = saveCredentials(userId, sellingPartnerId, pending.getRegion(),
                refreshToken, storeName,
                defaultProductType == null || defaultProductType.isBlank() ? "PRODUCT" : defaultProductType,
                availableMarketplaceIds, activeMarketplaceId);
        pendingDao.deleteByState(state);
        return saved;
    }

    private AmazonIntegration saveCredentials(String userId, String sellerId, String region,
                                              String refreshToken, String storeName,
                                              String defaultProductType,
                                              List<String> availableMarketplaceIds,
                                              String activeMarketplaceId) {
        AmazonIntegration existing = amazonDao.findByUserIdAndSellerId(userId, sellerId).orElse(null);
        String encRefresh = encryptionUtils.encrypt(refreshToken);
        String resolvedName = storeName != null && !storeName.isBlank() ? storeName : sellerId;

        if (existing != null) {
            existing.setRegion(region);
            existing.setStoreName(resolvedName);
            existing.setEncryptedRefreshToken(encRefresh);
            existing.setDefaultProductType(defaultProductType);
            existing.setAvailableMarketplaceIds(availableMarketplaceIds);
            existing.setActiveMarketplaceId(activeMarketplaceId);
            existing.setConnected(true);
            existing.setUpdatedAt(Instant.now());
            amazonDao.save(existing);
            activateStore(userId, sellerId);
            return amazonDao.findByUserIdAndSellerId(userId, sellerId).orElse(existing);
        }

        AmazonIntegration integration = AmazonIntegration.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .sellerId(sellerId)
                .storeName(resolvedName)
                .region(region)
                .encryptedRefreshToken(encRefresh)
                .defaultProductType(defaultProductType)
                .availableMarketplaceIds(availableMarketplaceIds)
                .activeMarketplaceId(activeMarketplaceId)
                .connected(true)
                .sqsEnabled(false)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        amazonDao.save(integration);
        activateStore(userId, sellerId);
        return amazonDao.findByUserIdAndSellerId(userId, sellerId).orElse(integration);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Store / marketplace management
    // ──────────────────────────────────────────────────────────────────────────

    public List<AmazonIntegration> listStores(String userId) {
        List<AmazonIntegration> stores = amazonDao.findAllByUserId(userId);
        boolean anyActive = stores.stream().anyMatch(AmazonIntegration::isActive);
        if (!anyActive && !stores.isEmpty()) {
            activateStore(userId, stores.get(0).getSellerId());
            return amazonDao.findAllByUserId(userId);
        }
        return stores;
    }

    public AmazonIntegration switchStore(String userId, String sellerId) {
        AmazonIntegration target = amazonDao.findByUserIdAndSellerId(userId, sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not connected: " + sellerId));
        activateStore(userId, sellerId);
        return amazonDao.findByUserIdAndSellerId(userId, target.getSellerId()).orElse(target);
    }

    public AmazonIntegration switchMarketplace(String userId, String marketplaceId) {
        AmazonIntegration active = requireIntegration(userId);
        if (active.getAvailableMarketplaceIds() != null
                && !active.getAvailableMarketplaceIds().isEmpty()
                && !active.getAvailableMarketplaceIds().contains(marketplaceId)) {
            throw new RuntimeException("Seller has not authorized marketplace: " + marketplaceId);
        }
        active.setActiveMarketplaceId(marketplaceId);
        active.setUpdatedAt(Instant.now());
        return amazonDao.save(active);
    }

    public AmazonIntegration setDefaultProductType(String userId, String productType) {
        AmazonIntegration active = requireIntegration(userId);
        if (productType == null || productType.isBlank()) {
            throw new RuntimeException("productType is required");
        }
        active.setDefaultProductType(productType.toUpperCase(Locale.ROOT));
        active.setUpdatedAt(Instant.now());
        return amazonDao.save(active);
    }

    public void disconnectStore(String userId, String sellerId) {
        AmazonIntegration integration = amazonDao.findByUserIdAndSellerId(userId, sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not connected: " + sellerId));
        teardownSqs(integration);
        amazonDao.deleteByUserIdAndSellerId(userId, sellerId);
        if (integration.isActive()) {
            amazonDao.findAllByUserId(userId).stream().findFirst()
                    .ifPresent(next -> activateStore(userId, next.getSellerId()));
        }
    }

    public void disconnectAllStores(String userId) {
        for (AmazonIntegration integration : amazonDao.findAllByUserId(userId)) {
            teardownSqs(integration);
        }
        amazonDao.deleteByUserId(userId);
    }

    public AmazonIntegration getIntegration(String userId) {
        AmazonIntegration active = amazonDao.findByUserIdAndActiveTrue(userId).orElse(null);
        if (active != null) return active;
        AmazonIntegration any = amazonDao.findByUserId(userId).orElse(null);
        if (any != null) {
            activateStore(userId, any.getSellerId());
            return amazonDao.findByUserIdAndSellerId(userId, any.getSellerId()).orElse(any);
        }
        return null;
    }

    private void activateStore(String userId, String sellerId) {
        List<AmazonIntegration> all = amazonDao.findAllByUserId(userId);
        for (AmazonIntegration s : all) {
            boolean shouldBeActive = s.getSellerId().equals(sellerId);
            if (s.isActive() != shouldBeActive) {
                s.setActive(shouldBeActive);
                s.setUpdatedAt(Instant.now());
                amazonDao.save(s);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sync / parse / upload
    // ──────────────────────────────────────────────────────────────────────────

    public AmazonProductEnhancementJob syncProductsFromStore(String userId, int limit) {
        AmazonIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String marketplaceId = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();

        List<AmazonProductDto> products = spApiService.listSellerListings(
                accessToken, integration.getRegion(), marketplaceId, limit);

        List<AmazonProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .peek(item -> {
                    item.setLifecycleStatus("SYNCED");
                    item.setMarketplaceId(marketplaceId);
                })
                .collect(Collectors.toList());

        AmazonProductEnhancementJob job = AmazonProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("AMAZON")
                .status("PENDING")
                .marketplaceId(marketplaceId)
                .rawProducts(rawItems)
                .source("SYNC")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return jobDao.save(job);
    }

    public List<AmazonProductDto> parseProductsWithAI(String content, String fileName) {
        log.info("Amazon: parsing products with AI from file {}", fileName);
        List<ShopifyProductDto> parsed = aiEnhancementService.parseProductsWithAI(content, fileName);
        return parsed.stream().map(this::shopifyDtoToAmazonDto).collect(Collectors.toList());
    }

    public List<AmazonProductDto> parseProductsFromImages(List<String> imageUrls) {
        log.info("Amazon: parsing product from {} image(s)", imageUrls == null ? 0 : imageUrls.size());
        List<ShopifyProductDto> parsed = aiEnhancementService.parseProductsFromImages(imageUrls);
        return parsed.stream().map(this::shopifyDtoToAmazonDto).collect(Collectors.toList());
    }

    public AmazonProductEnhancementJob createUploadJob(String userId, List<AmazonProductDto> products) {
        AmazonIntegration integration = getIntegration(userId);
        String marketplaceId = integration != null && integration.getActiveMarketplaceId() != null
                ? integration.getActiveMarketplaceId() : DEFAULT_MARKETPLACE_ID;
        String productType = integration != null && integration.getDefaultProductType() != null
                ? integration.getDefaultProductType() : "PRODUCT";

        List<AmazonProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .peek(item -> {
                    item.setLifecycleStatus("DRAFT");
                    if (item.getMarketplaceId() == null || item.getMarketplaceId().isBlank()) {
                        item.setMarketplaceId(marketplaceId);
                    }
                    if (item.getProductType() == null || item.getProductType().isBlank()) {
                        item.setProductType(productType);
                    }
                    if (item.getCondition() == null || item.getCondition().isBlank()) {
                        item.setCondition("new_new");
                    }
                })
                .collect(Collectors.toList());

        AmazonProductEnhancementJob job = AmazonProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("AMAZON")
                .status("PENDING")
                .marketplaceId(marketplaceId)
                .rawProducts(rawItems)
                .source("UPLOAD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return jobDao.save(job);
    }

    @Async
    public void enhanceJobAsync(String jobId, String userId) {
        AmazonProductEnhancementJob job = jobDao.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus("PROCESSING");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);

        try {
            List<ShopifyProductDto> shopifyDtos = job.getRawProducts().stream()
                    .map(this::amazonItemToShopifyDto).collect(Collectors.toList());
            List<ProductEnhancementJob.ProductItem> enhanced =
                    aiEnhancementService.enhanceProducts(shopifyDtos);

            List<AmazonProductItem> mapped = new ArrayList<>();
            for (int i = 0; i < enhanced.size() && i < job.getRawProducts().size(); i++) {
                mapped.add(mergeEnhancementIntoRaw(job.getRawProducts().get(i), enhanced.get(i)));
            }

            job.setEnhancedProducts(mapped);
            job.setStatus("ENHANCED");
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
            log.info("Amazon enhancement complete for job {}: {} products", jobId, mapped.size());
        } catch (Exception e) {
            log.error("Amazon enhancement failed for job {}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
        }
    }

    public void pushProductsToAmazon(String userId, String jobId, List<String> targetIds) {
        AmazonIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);

        AmazonProductEnhancementJob job = jobDao.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        if (!"ENHANCED".equals(job.getStatus())) {
            throw new RuntimeException("Job must be in ENHANCED status before publishing");
        }

        List<AmazonProductItem> toPublish = job.getEnhancedProducts().stream()
                .filter(p -> targetIds == null || targetIds.isEmpty()
                        || targetIds.contains(p.getSellerSku())
                        || targetIds.contains(p.getLocalId()))
                .collect(Collectors.toList());

        int successCount = 0;
        for (AmazonProductItem item : toPublish) {
            try {
                publishItem(integration, accessToken, item);
                item.setStatus("PUBLISHED");
                successCount++;
            } catch (Exception e) {
                log.error("Failed to push Amazon product {} / {}: {}",
                        item.getAsin(), item.getSellerSku(), e.getMessage());
                item.setStatus("FAILED");
            }
        }

        job.setStatus("PUBLISHED");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        log.info("Pushed {}/{} products to Amazon for job {}", successCount, toPublish.size(), jobId);
    }

    private void publishItem(AmazonIntegration integration, String accessToken, AmazonProductItem item) {
        String marketplaceId = item.getMarketplaceId() == null || item.getMarketplaceId().isBlank()
                ? integration.getActiveMarketplaceId() : item.getMarketplaceId();
        String productType = item.getProductType() == null || item.getProductType().isBlank()
                ? integration.getDefaultProductType() : item.getProductType();
        if (productType == null || productType.isBlank()) productType = "PRODUCT";

        if (item.getSellerSku() == null || item.getSellerSku().isBlank()) {
            item.setSellerSku(generateSku(item));
        }

        Map<String, Object> attributes = buildListingAttributes(item, marketplaceId);
        spApiService.createOrReplaceListing(accessToken, integration.getRegion(),
                integration.getSellerId(), item.getSellerSku(), marketplaceId, productType, attributes);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SQS Notifications (Amazon's webhook-equivalent)
    // ──────────────────────────────────────────────────────────────────────────

    public AmazonIntegration toggleSqs(String userId, boolean enable, String sqsArn) {
        AmazonIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);

        if (enable && !integration.isSqsEnabled()) {
            if (sqsArn == null || sqsArn.isBlank()) {
                throw new RuntimeException("sqsQueueArn is required to enable SQS notifications");
            }
            String destinationId = spApiService.createSqsDestination(
                    accessToken, integration.getRegion(),
                    "jade-" + integration.getSellerId(), sqsArn);
            integration.setNotificationDestinationId(destinationId);

            List<String> subscriptionIds = new ArrayList<>();
            for (String type : SQS_NOTIFICATION_TYPES) {
                try {
                    String subId = spApiService.createSubscription(
                            accessToken, integration.getRegion(), type, destinationId);
                    if (subId != null) subscriptionIds.add(type + ":" + subId);
                } catch (Exception e) {
                    log.warn("Failed to subscribe to {} for seller {}: {}",
                            type, integration.getSellerId(), e.getMessage());
                }
            }
            integration.setNotificationSubscriptionIds(subscriptionIds);
            integration.setSqsEnabled(true);
        } else if (!enable && integration.isSqsEnabled()) {
            teardownSqs(integration);
        }

        integration.setUpdatedAt(Instant.now());
        return amazonDao.save(integration);
    }

    /** Fire-and-forget teardown — swallows errors so disconnect paths always succeed. */
    private void teardownSqs(AmazonIntegration integration) {
        try {
            String accessToken = accessToken(integration);
            if (integration.getNotificationSubscriptionIds() != null) {
                for (String combined : integration.getNotificationSubscriptionIds()) {
                    String[] parts = combined.split(":", 2);
                    if (parts.length == 2) {
                        try {
                            spApiService.deleteSubscription(accessToken, integration.getRegion(),
                                    parts[0], parts[1]);
                        } catch (Exception e) {
                            log.warn("Failed to delete Amazon subscription {}: {}", combined, e.getMessage());
                        }
                    }
                }
            }
            if (integration.getNotificationDestinationId() != null) {
                try {
                    spApiService.deleteDestination(accessToken, integration.getRegion(),
                            integration.getNotificationDestinationId());
                } catch (Exception e) {
                    log.warn("Failed to delete Amazon destination {}: {}",
                            integration.getNotificationDestinationId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error tearing down SQS for seller {}: {}",
                    integration.getSellerId(), e.getMessage());
        }
        integration.setNotificationDestinationId(null);
        integration.setNotificationSubscriptionIds(new ArrayList<>());
        integration.setSqsEnabled(false);
    }

    /**
     * Called from the public notification-ingest endpoint once a remote
     * worker has pulled an SQS message and forwarded it to us. The payload
     * is whatever shape SP-API uses for the matched notification type;
     * we pull out the SKU/ASIN and kick off an auto-enhance pass.
     */
    @Async
    public void handleNotification(String userId, Map<String, Object> payload) {
        if (payload == null) return;
        log.info("Amazon notification received for user {}: {}", userId, payload.get("notificationType"));

        String sellerSku = extractString(payload, "sku");
        if (sellerSku == null) sellerSku = extractString(payload, "sellerSku");
        String asin = extractString(payload, "asin");
        String marketplaceId = extractString(payload, "marketplaceId");
        if (sellerSku == null || sellerSku.isBlank()) {
            log.info("Amazon notification had no SKU — skipping auto-enhance");
            return;
        }

        AmazonIntegration integration = getIntegration(userId);
        if (integration == null || !integration.isSqsEnabled()) {
            log.warn("Ignoring Amazon notification for user {} — integration missing or SQS disabled", userId);
            return;
        }

        AmazonProductItem raw = AmazonProductItem.builder()
                .localId(UUID.randomUUID().toString())
                .asin(asin)
                .sellerSku(sellerSku)
                .marketplaceId(marketplaceId == null
                        ? integration.getActiveMarketplaceId() : marketplaceId)
                .productType(integration.getDefaultProductType())
                .status("PENDING")
                .lifecycleStatus("SYNCED")
                .condition("new_new")
                .build();

        AmazonProductEnhancementJob job = AmazonProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("AMAZON")
                .status("PROCESSING")
                .marketplaceId(raw.getMarketplaceId())
                .rawProducts(List.of(raw))
                .source("SQS_NOTIFICATION")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        jobDao.save(job);

        try {
            String accessToken = accessToken(integration);
            Map<String, Object> listing = spApiService.getListing(accessToken, integration.getRegion(),
                    integration.getSellerId(), sellerSku, raw.getMarketplaceId());

            raw.setTitle(Objects.toString(listing.get("title"), ""));
            raw.setBodyHtml(Objects.toString(listing.get("bodyHtml"), ""));
            raw.setBrand(Objects.toString(listing.get("brand"), ""));
            raw.setCategory(Objects.toString(listing.get("category"), ""));
            raw.setSearchKeywords(Objects.toString(listing.get("searchKeywords"), ""));

            ShopifyProductDto shopifyDto = amazonItemToShopifyDto(raw);
            ProductEnhancementJob.ProductItem enhancedShopify = aiEnhancementService.enhanceSingle(shopifyDto);
            AmazonProductItem enhanced = mergeEnhancementIntoRaw(raw, enhancedShopify);

            // Apply the enhancement back to Amazon
            AmazonDirectProductUpdateRequest patch = new AmazonDirectProductUpdateRequest();
            patch.setTitle(enhanced.getEnhancedTitle());
            patch.setBodyHtml(enhanced.getEnhancedBodyHtml());
            patch.setSearchKeywords(enhanced.getEnhancedSearchKeywords());
            patch.setCategory(enhanced.getEnhancedCategory());
            if (enhanced.getEnhancedBulletPoints() != null
                    && !enhanced.getEnhancedBulletPoints().isEmpty()) {
                patch.setBulletPoints(enhanced.getEnhancedBulletPoints());
            }

            spApiService.patchListing(accessToken, integration.getRegion(),
                    integration.getSellerId(), sellerSku, raw.getMarketplaceId(),
                    raw.getProductType() == null ? integration.getDefaultProductType() : raw.getProductType(),
                    patch);

            enhanced.setStatus("PUBLISHED");
            job.setEnhancedProducts(List.of(enhanced));
            job.setStatus("PUBLISHED");
        } catch (Exception e) {
            log.error("Amazon SQS auto-enhance failed for SKU {}", sellerSku, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Single-product direct edit (live Amazon listing)
    // ──────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getProductDetails(String userId, String sku) {
        AmazonIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String mp = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();
        return spApiService.getListing(accessToken, integration.getRegion(),
                integration.getSellerId(), sku, mp);
    }

    public Map<String, Object> enhanceSingleProductDirect(String userId, String sku) {
        AmazonIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String mp = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();

        Map<String, Object> productMap = spApiService.getListing(accessToken, integration.getRegion(),
                integration.getSellerId(), sku, mp);

        ShopifyProductDto dto = ShopifyProductDto.builder()
                .title(str(productMap, "title"))
                .bodyHtml(str(productMap, "bodyHtml"))
                .vendor(str(productMap, "brand"))
                .productType(str(productMap, "category"))
                .tags(str(productMap, "searchKeywords"))
                .build();

        ProductEnhancementJob.ProductItem enhanced = aiEnhancementService.enhanceSingle(dto);

        Map<String, Object> suggestions = new LinkedHashMap<>();
        suggestions.put("title",          enhanced.getEnhancedTitle());
        suggestions.put("bodyHtml",       enhanced.getEnhancedBodyHtml());
        suggestions.put("brand",          enhanced.getEnhancedProductType()); // best-effort mapping
        suggestions.put("category",       enhanced.getEnhancedProductType());
        suggestions.put("searchKeywords", enhanced.getEnhancedTags());
        suggestions.put("bulletPoints",   deriveBullets(enhanced.getEnhancedBodyHtml()));
        productMap.put("aiSuggestions",   suggestions);
        return productMap;
    }

    public void updateProductDirect(String userId, String sku, AmazonDirectProductUpdateRequest req) {
        AmazonIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String mp = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();
        String productType = req.getProductType() == null || req.getProductType().isBlank()
                ? integration.getDefaultProductType() : req.getProductType();
        if (productType == null || productType.isBlank()) productType = "PRODUCT";

        spApiService.patchListing(accessToken, integration.getRegion(),
                integration.getSellerId(), sku, mp, productType, req);
        log.info("Direct Amazon listing patch applied for user {} sku {}", userId, sku);
    }

    /**
     * Append a new image URL to the end of a live Amazon listing's image
     * list. Fetches the current images, pushes the new URL, and patches
     * the listing back with the full array. Returns the resulting image
     * list so the UI can render it without a second round-trip.
     */
    public List<String> addProductImage(String userId, String sku, String src) {
        if (src == null || src.isBlank()) {
            throw new RuntimeException("Image URL is required");
        }
        AmazonIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String mp = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();

        Map<String, Object> listing = spApiService.getListing(accessToken,
                integration.getRegion(), integration.getSellerId(), sku, mp);

        List<String> images = new ArrayList<>();
        Object existing = listing.get("images");
        if (existing instanceof List<?> l) {
            for (Object o : l) if (o != null) images.add(o.toString());
        }
        if (images.contains(src)) return images;
        if (images.size() >= 9) {
            throw new RuntimeException("Amazon listings support at most 9 images (1 main + 8 secondary)");
        }
        images.add(src);

        String productType = integration.getDefaultProductType();
        if (productType == null || productType.isBlank()) {
            Object pt = listing.get("productType");
            productType = pt == null ? "PRODUCT" : pt.toString();
        }

        AmazonDirectProductUpdateRequest req = new AmazonDirectProductUpdateRequest();
        req.setImages(images);
        spApiService.patchListing(accessToken, integration.getRegion(),
                integration.getSellerId(), sku, mp, productType, req);
        return images;
    }

    /**
     * Remove an image URL from a live Amazon listing. {@code imageId} is
     * the URL-encoded image URL the frontend received from {@code getListing}.
     */
    public List<String> removeProductImage(String userId, String sku, String imageId) {
        if (imageId == null || imageId.isBlank()) {
            throw new RuntimeException("Image id is required");
        }
        AmazonIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String mp = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();

        Map<String, Object> listing = spApiService.getListing(accessToken,
                integration.getRegion(), integration.getSellerId(), sku, mp);

        List<String> images = new ArrayList<>();
        Object existing = listing.get("images");
        if (existing instanceof List<?> l) {
            for (Object o : l) if (o != null) images.add(o.toString());
        }
        images.removeIf(url -> url.equals(imageId));

        String productType = integration.getDefaultProductType();
        if (productType == null || productType.isBlank()) {
            Object pt = listing.get("productType");
            productType = pt == null ? "PRODUCT" : pt.toString();
        }

        AmazonDirectProductUpdateRequest req = new AmazonDirectProductUpdateRequest();
        req.setImages(images);
        spApiService.patchListing(accessToken, integration.getRegion(),
                integration.getSellerId(), sku, mp, productType, req);
        return images;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Jobs
    // ──────────────────────────────────────────────────────────────────────────

    public List<AmazonProductEnhancementJob> getJobs(String userId) {
        List<AmazonProductEnhancementJob> all = jobDao.findByUserIdOrderByCreatedAtDesc(userId);
        List<AmazonProductEnhancementJob> out = new ArrayList<>();
        for (AmazonProductEnhancementJob job : all) {
            if (backfillProductItems(job)) jobDao.save(job);
            out.add(job);
        }
        return out;
    }

    public AmazonProductEnhancementJob getJob(String userId, String jobId) {
        AmazonProductEnhancementJob job = jobDao.findById(jobId)
                .filter(j -> userId.equals(j.getUserId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));
        if (backfillProductItems(job)) jobDao.save(job);
        return job;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Drafts
    // ──────────────────────────────────────────────────────────────────────────

    public Map<String, Object> enhanceDraft(String userId, String jobId, String localId) {
        AmazonProductEnhancementJob job = getJob(userId, jobId);
        AmazonProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new RuntimeException("Title is required before running AI enhancement");
        }
        try {
            ShopifyProductDto dto = amazonItemToShopifyDto(item);
            ProductEnhancementJob.ProductItem enhancedShopify = aiEnhancementService.enhanceSingle(dto);
            AmazonProductItem enhanced = mergeEnhancementIntoRaw(item, enhancedShopify);

            item.setEnhancedTitle(enhanced.getEnhancedTitle());
            item.setEnhancedBodyHtml(enhanced.getEnhancedBodyHtml());
            item.setEnhancedBrand(enhanced.getEnhancedBrand());
            item.setEnhancedCategory(enhanced.getEnhancedCategory());
            item.setEnhancedBulletPoints(enhanced.getEnhancedBulletPoints());
            item.setEnhancedSearchKeywords(enhanced.getEnhancedSearchKeywords());
            item.setStatus("ENHANCED");

            String current = item.getLifecycleStatus();
            if ("SYNCED".equals(current) || "PUBLISHED".equals(current)) {
                item.setLifecycleStatus("PENDING_SYNC");
            }

            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
            return draftToDetailMap(job, item);
        } catch (Exception e) {
            log.error("Amazon AI enhancement failed for draft {} (job {}): {}",
                    localId, jobId, e.getMessage());
            throw new RuntimeException("AI enhancement failed: " + e.getMessage());
        }
    }

    public Map<String, Object> getDraft(String userId, String jobId, String localId) {
        AmazonProductEnhancementJob job = getJob(userId, jobId);
        AmazonProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        return draftToDetailMap(job, item);
    }

    public Map<String, Object> updateDraft(String userId, String jobId, String localId,
                                           AmazonDirectProductUpdateRequest req) {
        AmazonProductEnhancementJob job = getJob(userId, jobId);
        AmazonProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        if (req.getTitle() != null)          { item.setTitle(req.getTitle());                   item.setEnhancedTitle(null); }
        if (req.getBodyHtml() != null)       { item.setBodyHtml(req.getBodyHtml());             item.setEnhancedBodyHtml(null); }
        if (req.getBrand() != null)          { item.setBrand(req.getBrand());                   item.setEnhancedBrand(null); }
        if (req.getCategory() != null)       { item.setCategory(req.getCategory());             item.setEnhancedCategory(null); }
        if (req.getBulletPoints() != null)   { item.setBulletPoints(req.getBulletPoints());     item.setEnhancedBulletPoints(null); }
        if (req.getSearchKeywords() != null) { item.setSearchKeywords(req.getSearchKeywords()); item.setEnhancedSearchKeywords(null); }
        if (req.getCondition() != null)      item.setCondition(req.getCondition());
        if (req.getProductType() != null)    item.setProductType(req.getProductType());

        if (req.getStatus() != null)             item.setAmazonStatus(req.getStatus());
        if (req.getPrice() != null)              item.setPrice(req.getPrice());
        if (req.getListPrice() != null)          item.setListPrice(req.getListPrice());
        if (req.getSellerSku() != null)          item.setSellerSku(req.getSellerSku());
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
        AmazonIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);

        AmazonProductEnhancementJob job = getJob(userId, jobId);
        AmazonProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        try {
            if (item.getMarketplaceId() == null || item.getMarketplaceId().isBlank()) {
                item.setMarketplaceId(integration.getActiveMarketplaceId() == null
                        ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId());
            }
            if (item.getProductType() == null || item.getProductType().isBlank()) {
                item.setProductType(integration.getDefaultProductType() == null
                        ? "PRODUCT" : integration.getDefaultProductType());
            }
            if (item.getSellerSku() == null || item.getSellerSku().isBlank()) {
                item.setSellerSku(generateSku(item));
            }

            publishItem(integration, accessToken, item);
            item.setLifecycleStatus("PUBLISHED");
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sellerSku",       item.getSellerSku());
            result.put("sku",             item.getSellerSku());
            result.put("asin",            item.getAsin() == null ? "" : item.getAsin());
            result.put("lifecycleStatus", item.getLifecycleStatus());
            result.put("jobId",           job.getId());
            result.put("localId",         item.getLocalId());
            return result;
        } catch (Exception e) {
            log.error("Failed to publish Amazon draft {} (job {}): {}", localId, jobId, e.getMessage());
            throw new RuntimeException("Failed to publish draft: " + e.getMessage());
        }
    }

    public void deleteDraft(String userId, String jobId, String localId) {
        AmazonProductEnhancementJob job = getJob(userId, jobId);
        AmazonProductItem item = findItem(job, localId)
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

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private String accessToken(AmazonIntegration integration) {
        return lwaTokenService.getAccessToken(
                encryptionUtils.decrypt(integration.getEncryptedRefreshToken()));
    }

    private AmazonIntegration requireIntegration(String userId) {
        AmazonIntegration active = getIntegration(userId);
        if (active == null) {
            throw new RuntimeException("No Amazon integration found. Please connect your store first.");
        }
        return active;
    }

    private String generateSku(AmazonProductItem item) {
        String base = item.getTitle() == null ? "SKU" : item.getTitle();
        String slug = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        if (slug.length() > 24) slug = slug.substring(0, 24);
        if (slug.endsWith("-")) slug = slug.substring(0, slug.length() - 1);
        if (slug.isBlank()) slug = "sku";
        return slug + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Build the attribute payload for a listings-items PUT. The attributes
     * emitted here are the subset that most product types accept — the
     * caller may need to add product-type-specific attributes (sizes,
     * materials, etc.) for strict types.
     */
    private Map<String, Object> buildListingAttributes(AmazonProductItem item, String marketplaceId) {
        Map<String, Object> attrs = new LinkedHashMap<>();

        String title = firstNonBlank(item.getEnhancedTitle(), item.getTitle());
        if (!title.isBlank()) attrs.put("item_name", singletonAttr(title, marketplaceId));

        String body = firstNonBlank(item.getEnhancedBodyHtml(), item.getBodyHtml());
        if (!body.isBlank()) attrs.put("product_description", singletonAttr(body, marketplaceId));

        String brand = firstNonBlank(item.getEnhancedBrand(), item.getBrand());
        if (!brand.isBlank()) attrs.put("brand", singletonAttr(brand, marketplaceId));

        String keywords = firstNonBlank(item.getEnhancedSearchKeywords(), item.getSearchKeywords());
        if (!keywords.isBlank()) attrs.put("generic_keyword", singletonAttr(keywords, marketplaceId));

        String condition = item.getCondition() == null || item.getCondition().isBlank()
                ? "new_new" : item.getCondition();
        attrs.put("condition_type", singletonAttr(condition, marketplaceId));

        List<String> bullets = item.getEnhancedBulletPoints() != null
                && !item.getEnhancedBulletPoints().isEmpty()
                ? item.getEnhancedBulletPoints() : item.getBulletPoints();
        if (bullets != null && !bullets.isEmpty()) {
            List<Map<String, Object>> bp = new ArrayList<>();
            for (String b : bullets) {
                if (b == null || b.isBlank()) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("value",         b);
                row.put("marketplace_id", marketplaceId);
                bp.add(row);
            }
            if (!bp.isEmpty()) attrs.put("bullet_point", bp);
        }

        if (item.getPrice() != null && !item.getPrice().isBlank()) {
            Map<String, Object> price = new LinkedHashMap<>();
            Map<String, Object> amount = new LinkedHashMap<>();
            amount.put("currency", "USD");
            amount.put("value",    item.getPrice());
            price.put("value",          amount);
            price.put("marketplace_id", marketplaceId);
            attrs.put("list_price", List.of(price));
        }

        if (item.getInventoryQuantity() != null) {
            Map<String, Object> avail = new LinkedHashMap<>();
            avail.put("fulfillment_channel_code", "DEFAULT");
            avail.put("quantity",                 item.getInventoryQuantity());
            attrs.put("fulfillment_availability", List.of(avail));
        }

        if (item.getImages() != null && !item.getImages().isEmpty()) {
            Map<String, Object> main = new LinkedHashMap<>();
            main.put("media_location",  item.getImages().get(0));
            main.put("marketplace_id",  marketplaceId);
            attrs.put("main_product_image_locator", List.of(main));

            List<Map<String, Object>> other = new ArrayList<>();
            for (int i = 1; i < item.getImages().size() && i <= 8; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("media_location",  item.getImages().get(i));
                row.put("marketplace_id",  marketplaceId);
                other.add(row);
            }
            if (!other.isEmpty()) attrs.put("other_product_image_locator", other);
        }

        return attrs;
    }

    private static List<Map<String, Object>> singletonAttr(String value, String marketplaceId) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("value",         value);
        v.put("marketplace_id", marketplaceId);
        return List.of(v);
    }

    private boolean backfillProductItems(AmazonProductEnhancementJob job) {
        boolean mutated = false;
        mutated |= backfillList(job.getRawProducts());
        mutated |= backfillList(job.getEnhancedProducts());
        return mutated;
    }

    private boolean backfillList(List<AmazonProductItem> items) {
        if (items == null) return false;
        boolean mutated = false;
        for (AmazonProductItem item : items) {
            if (item.getLocalId() == null || item.getLocalId().isBlank()) {
                item.setLocalId(UUID.randomUUID().toString());
                mutated = true;
            }
            if (item.getLifecycleStatus() == null || item.getLifecycleStatus().isBlank()) {
                boolean inStore = (item.getAsin() != null && !item.getAsin().isBlank())
                        || (item.getSellerSku() != null && !item.getSellerSku().isBlank());
                item.setLifecycleStatus(inStore ? "SYNCED" : "DRAFT");
                mutated = true;
            }
        }
        return mutated;
    }

    private Optional<AmazonProductItem> findItem(AmazonProductEnhancementJob job, String localId) {
        if (localId == null || localId.isBlank()) return Optional.empty();
        if (job.getEnhancedProducts() != null) {
            for (AmazonProductItem p : job.getEnhancedProducts()) {
                if (localId.equals(p.getLocalId())) return Optional.of(p);
            }
        }
        if (job.getRawProducts() != null) {
            for (AmazonProductItem p : job.getRawProducts()) {
                if (localId.equals(p.getLocalId())) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> draftToDetailMap(AmazonProductEnhancementJob job, AmazonProductItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId",           job.getId());
        result.put("localId",         item.getLocalId());
        result.put("lifecycleStatus", nullToEmpty(item.getLifecycleStatus()));
        result.put("asin",            nullToEmpty(item.getAsin()));
        result.put("sellerSku",       nullToEmpty(item.getSellerSku()));
        result.put("sku",             nullToEmpty(item.getSellerSku()));
        result.put("marketplaceId",   nullToEmpty(item.getMarketplaceId()));
        result.put("productType",     nullToEmpty(item.getProductType()));

        result.put("title",          firstNonBlank(item.getEnhancedTitle(),          item.getTitle()));
        result.put("bodyHtml",       firstNonBlank(item.getEnhancedBodyHtml(),       item.getBodyHtml()));
        result.put("brand",          firstNonBlank(item.getEnhancedBrand(),          item.getBrand()));
        result.put("category",       firstNonBlank(item.getEnhancedCategory(),       item.getCategory()));
        result.put("bulletPoints",   item.getEnhancedBulletPoints() != null
                && !item.getEnhancedBulletPoints().isEmpty()
                ? item.getEnhancedBulletPoints() : (item.getBulletPoints() == null
                        ? new ArrayList<>() : item.getBulletPoints()));
        result.put("searchKeywords", firstNonBlank(item.getEnhancedSearchKeywords(), item.getSearchKeywords()));
        result.put("condition",      firstNonBlank(item.getCondition(), "new_new"));

        result.put("status",         firstNonBlank(item.getAmazonStatus(), "ACTIVE"));
        result.put("createdAt",      job.getCreatedAt() == null ? "" : job.getCreatedAt().toString());
        result.put("updatedAt",      job.getUpdatedAt() == null ? "" : job.getUpdatedAt().toString());

        result.put("price",             nullToEmpty(item.getPrice()));
        result.put("listPrice",         nullToEmpty(item.getListPrice()));
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
        result.put("images", draftImages);
        return result;
    }

    // ── DTO <-> entity mapping ───────────────────────────────────────────────

    private AmazonProductItem dtoToRawItem(AmazonProductDto p) {
        return AmazonProductItem.builder()
                .localId(UUID.randomUUID().toString())
                .asin(p.getAsin())
                .sellerSku(p.getSellerSku())
                .marketplaceId(p.getMarketplaceId())
                .productType(p.getProductType())
                .title(p.getTitle())
                .bodyHtml(p.getBodyHtml())
                .brand(p.getBrand())
                .category(p.getCategory())
                .bulletPoints(p.getBulletPoints())
                .searchKeywords(p.getSearchKeywords())
                .condition(p.getCondition())
                .price(p.getPrice())
                .listPrice(p.getListPrice())
                .inventoryQuantity(p.getInventoryQuantity())
                .amazonStatus(p.getStatus())
                .images(p.getImages())
                .status("PENDING")
                .build();
    }

    /**
     * Project an Amazon item onto the Shopify DTO shape so the shared AI
     * service can operate on it. Fields are mapped conservatively:
     * brand → vendor, category → productType, searchKeywords → tags.
     * Bullet points are folded into the body HTML as a trailing list so
     * the AI has full context.
     */
    private ShopifyProductDto amazonItemToShopifyDto(AmazonProductItem item) {
        String body = item.getBodyHtml() == null ? "" : item.getBodyHtml();
        if (item.getBulletPoints() != null && !item.getBulletPoints().isEmpty()) {
            StringBuilder sb = new StringBuilder(body);
            if (!body.isBlank()) sb.append("\n\n");
            sb.append("<ul>");
            for (String b : item.getBulletPoints()) {
                if (b != null && !b.isBlank()) sb.append("<li>").append(b).append("</li>");
            }
            sb.append("</ul>");
            body = sb.toString();
        }
        return ShopifyProductDto.builder()
                .title(item.getTitle())
                .bodyHtml(body)
                .vendor(item.getBrand())
                .productType(item.getCategory())
                .tags(item.getSearchKeywords())
                .build();
    }

    private AmazonProductDto shopifyDtoToAmazonDto(ShopifyProductDto p) {
        return AmazonProductDto.builder()
                .title(p.getTitle())
                .bodyHtml(p.getBodyHtml())
                .brand(p.getVendor())
                .category(p.getProductType())
                .searchKeywords(p.getTags())
                .condition("new_new")
                .price(p.getPrice())
                .listPrice(p.getCompareAtPrice())
                .inventoryQuantity(p.getInventoryQuantity())
                .images(p.getImages())
                .build();
    }

    /**
     * Apply the Shopify-shaped enhancement back onto the Amazon item. The
     * enhanced body is used to derive bullet points heuristically when the
     * Shopify side doesn't have a first-class bullet field.
     */
    private AmazonProductItem mergeEnhancementIntoRaw(AmazonProductItem raw,
                                                      ProductEnhancementJob.ProductItem shopify) {
        raw.setEnhancedTitle(shopify.getEnhancedTitle());
        raw.setEnhancedBodyHtml(shopify.getEnhancedBodyHtml());
        raw.setEnhancedBrand(shopify.getVendor()); // brand is unchanged unless the AI suggests otherwise
        raw.setEnhancedCategory(shopify.getEnhancedProductType());
        raw.setEnhancedSearchKeywords(shopify.getEnhancedTags());
        raw.setEnhancedBulletPoints(deriveBullets(shopify.getEnhancedBodyHtml()));
        if (raw.getStatus() == null || "PENDING".equals(raw.getStatus())) {
            raw.setStatus("ENHANCED");
        }
        return raw;
    }

    /**
     * Best-effort extraction of up to 5 bullet points from enhanced body
     * HTML. Looks for {@code <li>} tags first, then falls back to the
     * first five non-empty sentences/paragraphs.
     */
    private static List<String> deriveBullets(String html) {
        if (html == null || html.isBlank()) return new ArrayList<>();
        List<String> out = new ArrayList<>();

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<li[^>]*>(.*?)</li>", java.util.regex.Pattern.DOTALL)
                .matcher(html);
        while (m.find() && out.size() < 5) {
            String text = stripHtml(m.group(1)).trim();
            if (!text.isBlank()) out.add(text);
        }
        if (!out.isEmpty()) return out;

        String plain = stripHtml(html);
        for (String p : plain.split("\\.\\s+|\\n+")) {
            String t = p.trim();
            if (t.length() < 8) continue;
            out.add(t.length() > 500 ? t.substring(0, 500) : t);
            if (out.size() >= 5) break;
        }
        return out;
    }

    private static String stripHtml(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? "" : v.toString();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String extractString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v != null) return v.toString();
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                String r = extractString((Map<String, Object>) nested, key);
                if (r != null) return r;
            } else if (value instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> nested) {
                        @SuppressWarnings("unchecked")
                        String r = extractString((Map<String, Object>) nested, key);
                        if (r != null) return r;
                    }
                }
            }
        }
        return null;
    }

    private static String normalizeRegion(String region) {
        if (region == null || region.isBlank()) return "NA";
        String r = region.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("NA", "EU", "FE").contains(r)) return "NA";
        return r;
    }
}
