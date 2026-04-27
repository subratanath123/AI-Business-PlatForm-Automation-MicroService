package net.ai.chatbot.service.ebay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.EbayIntegrationDao;
import net.ai.chatbot.dao.EbayPendingConnectionDao;
import net.ai.chatbot.dao.EbayProductEnhancementJobDao;
import net.ai.chatbot.dto.ebay.EbayDirectProductUpdateRequest;
import net.ai.chatbot.dto.ebay.EbayProductDto;
import net.ai.chatbot.dto.shopify.ShopifyProductDto;
import net.ai.chatbot.entity.EbayIntegration;
import net.ai.chatbot.entity.EbayPendingConnection;
import net.ai.chatbot.entity.EbayProductEnhancementJob;
import net.ai.chatbot.entity.EbayProductEnhancementJob.EbayProductItem;
import net.ai.chatbot.entity.ProductEnhancementJob;
import net.ai.chatbot.service.shopify.ProductAIEnhancementService;
import net.ai.chatbot.utils.EncryptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * eBay counterpart to the Shopify / Woo / Amazon integration services.
 * Uses a dedicated {@link EbayProductEnhancementJob} collection because
 * eBay listings carry concepts (offer/inventory split, per-marketplace
 * offers, item specifics / aspects, category id) that don't map cleanly
 * onto the existing Shopify schema. AI enhancement is shared via
 * {@link ProductAIEnhancementService} by projecting eBay products
 * through {@link ShopifyProductDto}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EbayIntegrationService {

    private static final String DEFAULT_MARKETPLACE_ID = "EBAY_US";
    /** eBay subscription topics we register when notifications are enabled. */
    private static final List<String> NOTIFICATION_TOPICS = List.of(
            "INVENTORY_ITEM.CREATED",
            "INVENTORY_ITEM.UPDATED",
            "OFFER.PUBLISHED"
    );
    /** Scopes we request at consent time. */
    public static final String OAUTH_SCOPES = String.join(" ",
            "https://api.ebay.com/oauth/api_scope",
            "https://api.ebay.com/oauth/api_scope/sell.inventory",
            "https://api.ebay.com/oauth/api_scope/sell.account",
            "https://api.ebay.com/oauth/api_scope/sell.fulfillment",
            "https://api.ebay.com/oauth/api_scope/sell.marketing",
            "https://api.ebay.com/oauth/api_scope/commerce.notification.subscription",
            "https://api.ebay.com/oauth/api_scope/commerce.identity.readonly"
    );

    private final EbayIntegrationDao ebayDao;
    private final EbayPendingConnectionDao pendingDao;
    private final EbayProductEnhancementJobDao jobDao;
    private final EbayApiService apiService;
    private final EbayOAuthService oauth;
    private final ProductAIEnhancementService aiEnhancementService;
    private final EncryptionUtils encryptionUtils;

    // ──────────────────────────────────────────────────────────────────────────
    // OAuth handshake
    // ──────────────────────────────────────────────────────────────────────────

    public EbayPendingConnection initAuth(String userId, String environment) {
        EbayPendingConnection pending = EbayPendingConnection.builder()
                .id(UUID.randomUUID().toString())
                .state(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .environment(oauth.normalizeEnvironment(environment))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return pendingDao.save(pending);
    }

    public EbayIntegration finalizeAuth(String userId, String state, String code, String storeName) {
        EbayPendingConnection pending = pendingDao.findByState(state)
                .orElseThrow(() -> new RuntimeException("Unknown connection state"));
        if (!userId.equals(pending.getUserId())) {
            throw new RuntimeException("State does not belong to the current user");
        }

        Map<String, Object> tokens = oauth.exchangeAuthorizationCode(code, pending.getEnvironment());
        String refreshToken = Objects.toString(tokens.get("refresh_token"), null);
        String accessToken  = Objects.toString(tokens.get("access_token"), null);
        if (refreshToken == null || refreshToken.isBlank()) {
            pendingDao.deleteByState(state);
            throw new RuntimeException("eBay did not return a refresh_token — is your RuName correctly configured?");
        }

        // Fetch seller identity + marketplaces
        String sellerId = null;
        String resolvedStoreName = storeName;
        List<String> marketplaces = new ArrayList<>();
        try {
            if (accessToken != null) {
                Map<String, Object> identity = apiService.getSellerIdentity(accessToken, pending.getEnvironment());
                sellerId = Objects.toString(identity.get("userId"), null);
                if (resolvedStoreName == null || resolvedStoreName.isBlank()) {
                    resolvedStoreName = Objects.toString(identity.get("username"), sellerId);
                }
                for (Map<String, Object> m : apiService.getMarketplaces(accessToken, pending.getEnvironment())) {
                    String id = Objects.toString(m.get("id"), "");
                    if (!id.isBlank()) marketplaces.add(id);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch eBay identity/marketplaces: {}", e.getMessage());
        }
        if (sellerId == null || sellerId.isBlank()) {
            sellerId = "ebay-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (resolvedStoreName == null || resolvedStoreName.isBlank()) resolvedStoreName = sellerId;

        String activeMarketplace = marketplaces.contains(DEFAULT_MARKETPLACE_ID)
                ? DEFAULT_MARKETPLACE_ID
                : (marketplaces.isEmpty() ? DEFAULT_MARKETPLACE_ID : marketplaces.get(0));

        EbayIntegration saved = saveCredentials(userId, sellerId, pending.getEnvironment(),
                refreshToken, resolvedStoreName, marketplaces, activeMarketplace);
        pendingDao.deleteByState(state);
        return saved;
    }

    private EbayIntegration saveCredentials(String userId, String sellerId, String environment,
                                            String refreshToken, String storeName,
                                            List<String> availableMarketplaceIds,
                                            String activeMarketplaceId) {
        EbayIntegration existing = ebayDao.findByUserIdAndSellerId(userId, sellerId).orElse(null);
        String enc = encryptionUtils.encrypt(refreshToken);

        if (existing != null) {
            existing.setEnvironment(environment);
            existing.setStoreName(storeName);
            existing.setEncryptedRefreshToken(enc);
            existing.setScopes(OAUTH_SCOPES);
            existing.setAvailableMarketplaceIds(availableMarketplaceIds);
            existing.setActiveMarketplaceId(activeMarketplaceId);
            existing.setConnected(true);
            existing.setUpdatedAt(Instant.now());
            ebayDao.save(existing);
            activateStore(userId, sellerId);
            return ebayDao.findByUserIdAndSellerId(userId, sellerId).orElse(existing);
        }

        EbayIntegration integration = EbayIntegration.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .sellerId(sellerId)
                .storeName(storeName)
                .environment(environment)
                .encryptedRefreshToken(enc)
                .scopes(OAUTH_SCOPES)
                .availableMarketplaceIds(availableMarketplaceIds)
                .activeMarketplaceId(activeMarketplaceId)
                .connected(true)
                .notificationsEnabled(false)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        ebayDao.save(integration);
        activateStore(userId, sellerId);
        return ebayDao.findByUserIdAndSellerId(userId, sellerId).orElse(integration);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Store / marketplace management
    // ──────────────────────────────────────────────────────────────────────────

    public List<EbayIntegration> listStores(String userId) {
        List<EbayIntegration> stores = ebayDao.findAllByUserId(userId);
        boolean anyActive = stores.stream().anyMatch(EbayIntegration::isActive);
        if (!anyActive && !stores.isEmpty()) {
            activateStore(userId, stores.get(0).getSellerId());
            return ebayDao.findAllByUserId(userId);
        }
        return stores;
    }

    public EbayIntegration switchStore(String userId, String sellerId) {
        EbayIntegration target = ebayDao.findByUserIdAndSellerId(userId, sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not connected: " + sellerId));
        activateStore(userId, sellerId);
        return ebayDao.findByUserIdAndSellerId(userId, target.getSellerId()).orElse(target);
    }

    public EbayIntegration switchMarketplace(String userId, String marketplaceId) {
        EbayIntegration active = requireIntegration(userId);
        if (active.getAvailableMarketplaceIds() != null
                && !active.getAvailableMarketplaceIds().isEmpty()
                && !active.getAvailableMarketplaceIds().contains(marketplaceId)) {
            throw new RuntimeException("Seller has not authorized marketplace: " + marketplaceId);
        }
        active.setActiveMarketplaceId(marketplaceId);
        active.setUpdatedAt(Instant.now());
        return ebayDao.save(active);
    }

    public EbayIntegration setStoreDefaults(String userId, String categoryId, String locationKey,
                                            String fulfillmentPolicyId, String paymentPolicyId,
                                            String returnPolicyId) {
        EbayIntegration active = requireIntegration(userId);
        if (categoryId           != null) active.setDefaultCategoryId(categoryId);
        if (locationKey          != null) active.setDefaultMerchantLocationKey(locationKey);
        if (fulfillmentPolicyId  != null) active.setDefaultFulfillmentPolicyId(fulfillmentPolicyId);
        if (paymentPolicyId      != null) active.setDefaultPaymentPolicyId(paymentPolicyId);
        if (returnPolicyId       != null) active.setDefaultReturnPolicyId(returnPolicyId);
        active.setUpdatedAt(Instant.now());
        return ebayDao.save(active);
    }

    public void disconnectStore(String userId, String sellerId) {
        EbayIntegration integration = ebayDao.findByUserIdAndSellerId(userId, sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not connected: " + sellerId));
        teardownNotifications(integration);
        ebayDao.deleteByUserIdAndSellerId(userId, sellerId);
        if (integration.isActive()) {
            ebayDao.findAllByUserId(userId).stream().findFirst()
                    .ifPresent(next -> activateStore(userId, next.getSellerId()));
        }
    }

    public void disconnectAllStores(String userId) {
        for (EbayIntegration integration : ebayDao.findAllByUserId(userId)) {
            teardownNotifications(integration);
        }
        ebayDao.deleteByUserId(userId);
    }

    public EbayIntegration getIntegration(String userId) {
        EbayIntegration active = ebayDao.findByUserIdAndActiveTrue(userId).orElse(null);
        if (active != null) return active;
        EbayIntegration any = ebayDao.findByUserId(userId).orElse(null);
        if (any != null) {
            activateStore(userId, any.getSellerId());
            return ebayDao.findByUserIdAndSellerId(userId, any.getSellerId()).orElse(any);
        }
        return null;
    }

    private void activateStore(String userId, String sellerId) {
        List<EbayIntegration> all = ebayDao.findAllByUserId(userId);
        for (EbayIntegration s : all) {
            boolean shouldBeActive = s.getSellerId().equals(sellerId);
            if (s.isActive() != shouldBeActive) {
                s.setActive(shouldBeActive);
                s.setUpdatedAt(Instant.now());
                ebayDao.save(s);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sync / parse / upload
    // ──────────────────────────────────────────────────────────────────────────

    public EbayProductEnhancementJob syncProductsFromStore(String userId, int limit) {
        EbayIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String marketplaceId = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();

        List<EbayProductDto> products = apiService.listInventoryItems(
                accessToken, integration.getEnvironment(), marketplaceId, limit);

        List<EbayProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .peek(item -> {
                    item.setLifecycleStatus("SYNCED");
                    item.setMarketplaceId(marketplaceId);
                })
                .collect(Collectors.toList());

        EbayProductEnhancementJob job = EbayProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("EBAY")
                .status("PENDING")
                .marketplaceId(marketplaceId)
                .rawProducts(rawItems)
                .source("SYNC")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return jobDao.save(job);
    }

    public List<EbayProductDto> parseProductsWithAI(String content, String fileName) {
        log.info("eBay: parsing products with AI from file {}", fileName);
        List<ShopifyProductDto> parsed = aiEnhancementService.parseProductsWithAI(content, fileName);
        return parsed.stream().map(this::shopifyDtoToEbayDto).collect(Collectors.toList());
    }

    public List<EbayProductDto> parseProductsFromImages(List<String> imageUrls) {
        log.info("eBay: parsing product from {} image(s)", imageUrls == null ? 0 : imageUrls.size());
        List<ShopifyProductDto> parsed = aiEnhancementService.parseProductsFromImages(imageUrls);
        return parsed.stream().map(this::shopifyDtoToEbayDto).collect(Collectors.toList());
    }

    public EbayProductEnhancementJob createUploadJob(String userId, List<EbayProductDto> products) {
        EbayIntegration integration = getIntegration(userId);
        String marketplaceId = integration != null && integration.getActiveMarketplaceId() != null
                ? integration.getActiveMarketplaceId() : DEFAULT_MARKETPLACE_ID;

        List<EbayProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .peek(item -> {
                    item.setLifecycleStatus("DRAFT");
                    if (item.getMarketplaceId() == null || item.getMarketplaceId().isBlank()) {
                        item.setMarketplaceId(marketplaceId);
                    }
                    if (item.getCondition() == null || item.getCondition().isBlank()) {
                        item.setCondition("NEW");
                    }
                    if (item.getCurrency() == null || item.getCurrency().isBlank()) {
                        item.setCurrency(defaultCurrencyFor(marketplaceId));
                    }
                })
                .collect(Collectors.toList());

        EbayProductEnhancementJob job = EbayProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("EBAY")
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
        EbayProductEnhancementJob job = jobDao.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus("PROCESSING");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);

        try {
            List<ShopifyProductDto> shopifyDtos = job.getRawProducts().stream()
                    .map(this::ebayItemToShopifyDto).collect(Collectors.toList());
            List<ProductEnhancementJob.ProductItem> enhanced =
                    aiEnhancementService.enhanceProducts(shopifyDtos);

            List<EbayProductItem> mapped = new ArrayList<>();
            for (int i = 0; i < enhanced.size() && i < job.getRawProducts().size(); i++) {
                mapped.add(mergeEnhancementIntoRaw(job.getRawProducts().get(i), enhanced.get(i)));
            }

            job.setEnhancedProducts(mapped);
            job.setStatus("ENHANCED");
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
            log.info("eBay enhancement complete for job {}: {} products", jobId, mapped.size());
        } catch (Exception e) {
            log.error("eBay enhancement failed for job {}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
        }
    }

    public void pushProductsToEbay(String userId, String jobId, List<String> targetIds) {
        EbayIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);

        EbayProductEnhancementJob job = jobDao.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        if (!"ENHANCED".equals(job.getStatus())) {
            throw new RuntimeException("Job must be in ENHANCED status before publishing");
        }

        List<EbayProductItem> toPublish = job.getEnhancedProducts().stream()
                .filter(p -> targetIds == null || targetIds.isEmpty()
                        || targetIds.contains(p.getSku())
                        || targetIds.contains(p.getLocalId()))
                .collect(Collectors.toList());

        int successCount = 0;
        for (EbayProductItem item : toPublish) {
            try {
                publishItem(integration, accessToken, item);
                item.setStatus("PUBLISHED");
                successCount++;
            } catch (Exception e) {
                log.error("Failed to push eBay product {} / {}: {}",
                        item.getSku(), item.getItemId(), e.getMessage());
                item.setStatus("FAILED");
            }
        }

        job.setStatus("PUBLISHED");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        log.info("Pushed {}/{} products to eBay for job {}", successCount, toPublish.size(), jobId);
    }

    /** Build and upload the inventory_item + offer for a single item, then publish. */
    private void publishItem(EbayIntegration integration, String accessToken, EbayProductItem item) {
        String marketplaceId = item.getMarketplaceId() == null || item.getMarketplaceId().isBlank()
                ? integration.getActiveMarketplaceId() : item.getMarketplaceId();
        if (marketplaceId == null || marketplaceId.isBlank()) marketplaceId = DEFAULT_MARKETPLACE_ID;

        if (item.getSku() == null || item.getSku().isBlank()) {
            item.setSku(generateSku(item));
        }

        // 1. inventory_item
        Map<String, Object> product = new LinkedHashMap<>();
        String title = firstNonBlank(item.getEnhancedTitle(), item.getTitle());
        if (!title.isBlank())    product.put("title", title);
        String body = firstNonBlank(item.getEnhancedBodyHtml(), item.getBodyHtml());
        if (!body.isBlank())     product.put("description", body);
        String subtitle = firstNonBlank(item.getEnhancedSubtitle(), item.getSubtitle());
        if (!subtitle.isBlank()) product.put("subtitle", subtitle);
        String brand = firstNonBlank(item.getEnhancedBrand(), item.getBrand());
        if (!brand.isBlank())    product.put("brand", brand);
        if (item.getMpn() != null && !item.getMpn().isBlank()) product.put("mpn", item.getMpn());
        if (item.getUpc() != null && !item.getUpc().isBlank()) product.put("upc", List.of(item.getUpc()));
        Map<String, List<String>> aspects = item.getEnhancedAspects() != null
                && !item.getEnhancedAspects().isEmpty() ? item.getEnhancedAspects() : item.getAspects();
        if (aspects != null && !aspects.isEmpty()) product.put("aspects", aspects);
        if (item.getImages() != null && !item.getImages().isEmpty()) {
            product.put("imageUrls", item.getImages());
        }

        Map<String, Object> itemBody = new LinkedHashMap<>();
        itemBody.put("product",   product);
        itemBody.put("condition", item.getCondition() == null || item.getCondition().isBlank()
                ? "NEW" : item.getCondition());
        if (item.getConditionDescription() != null && !item.getConditionDescription().isBlank()) {
            itemBody.put("conditionDescription", item.getConditionDescription());
        }
        Map<String, Object> ship = new LinkedHashMap<>();
        ship.put("quantity", item.getInventoryQuantity() == null ? 1 : item.getInventoryQuantity());
        itemBody.put("availability", Map.of("shipToLocationAvailability", ship));

        apiService.createOrReplaceInventoryItem(accessToken, integration.getEnvironment(),
                marketplaceId, item.getSku(), itemBody);

        // 2. offer
        Map<String, Object> offerBody = new LinkedHashMap<>();
        offerBody.put("sku",           item.getSku());
        offerBody.put("marketplaceId", marketplaceId);
        offerBody.put("format",        "FIXED_PRICE");
        String categoryId = item.getCategoryId() == null || item.getCategoryId().isBlank()
                ? integration.getDefaultCategoryId() : item.getCategoryId();
        if (categoryId == null || categoryId.isBlank()) {
            throw new RuntimeException("No eBay categoryId configured. Set a default category on the store or on the product.");
        }
        offerBody.put("categoryId", categoryId);

        Map<String, Object> price = new LinkedHashMap<>();
        price.put("value",    item.getPrice() == null ? "0" : item.getPrice());
        price.put("currency", item.getCurrency() == null || item.getCurrency().isBlank()
                ? defaultCurrencyFor(marketplaceId) : item.getCurrency());
        offerBody.put("pricingSummary", Map.of("price", price));
        offerBody.put("availableQuantity", item.getInventoryQuantity() == null ? 1 : item.getInventoryQuantity());

        if (integration.getDefaultMerchantLocationKey() != null
                && !integration.getDefaultMerchantLocationKey().isBlank()) {
            offerBody.put("merchantLocationKey", integration.getDefaultMerchantLocationKey());
        }
        Map<String, Object> policies = new LinkedHashMap<>();
        if (integration.getDefaultFulfillmentPolicyId() != null) policies.put("fulfillmentPolicyId", integration.getDefaultFulfillmentPolicyId());
        if (integration.getDefaultPaymentPolicyId()     != null) policies.put("paymentPolicyId",     integration.getDefaultPaymentPolicyId());
        if (integration.getDefaultReturnPolicyId()      != null) policies.put("returnPolicyId",      integration.getDefaultReturnPolicyId());
        if (!policies.isEmpty()) offerBody.put("listingPolicies", policies);

        String offerId = apiService.createOrReplaceOffer(accessToken, integration.getEnvironment(),
                marketplaceId, item.getSku(), offerBody);
        item.setOfferId(offerId);
        item.setCategoryId(categoryId);

        // 3. publish
        if (offerId != null) {
            String listingId = apiService.publishOffer(accessToken, integration.getEnvironment(), offerId);
            if (listingId != null) item.setItemId(listingId);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notifications (webhook-equivalent)
    // ──────────────────────────────────────────────────────────────────────────

    public EbayIntegration toggleNotifications(String userId, boolean enable,
                                                String endpointUrl, String verificationToken) {
        EbayIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);

        if (enable && !integration.isNotificationsEnabled()) {
            if (endpointUrl == null || endpointUrl.isBlank()
                    || verificationToken == null || verificationToken.isBlank()) {
                throw new RuntimeException("endpointUrl and verificationToken are required to enable notifications");
            }
            String destinationId = apiService.createNotificationDestination(
                    accessToken, integration.getEnvironment(),
                    "jade-" + integration.getSellerId(), endpointUrl, verificationToken);
            integration.setNotificationDestinationId(destinationId);

            List<String> subs = new ArrayList<>();
            for (String topic : NOTIFICATION_TOPICS) {
                try {
                    String subId = apiService.createNotificationSubscription(
                            accessToken, integration.getEnvironment(), topic, destinationId);
                    if (subId != null) subs.add(topic + ":" + subId);
                } catch (Exception e) {
                    log.warn("Failed to subscribe to eBay topic {}: {}", topic, e.getMessage());
                }
            }
            integration.setNotificationSubscriptionIds(subs);
            integration.setNotificationsEnabled(true);
        } else if (!enable && integration.isNotificationsEnabled()) {
            teardownNotifications(integration);
        }

        integration.setUpdatedAt(Instant.now());
        return ebayDao.save(integration);
    }

    private void teardownNotifications(EbayIntegration integration) {
        try {
            String accessToken = accessToken(integration);
            if (integration.getNotificationSubscriptionIds() != null) {
                for (String combined : integration.getNotificationSubscriptionIds()) {
                    String[] parts = combined.split(":", 2);
                    if (parts.length == 2) {
                        try {
                            apiService.deleteNotificationSubscription(accessToken,
                                    integration.getEnvironment(), parts[1]);
                        } catch (Exception e) {
                            log.warn("Failed to delete eBay subscription {}: {}", combined, e.getMessage());
                        }
                    }
                }
            }
            if (integration.getNotificationDestinationId() != null) {
                try {
                    apiService.deleteNotificationDestination(accessToken,
                            integration.getEnvironment(), integration.getNotificationDestinationId());
                } catch (Exception e) {
                    log.warn("Failed to delete eBay destination {}: {}",
                            integration.getNotificationDestinationId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error tearing down eBay notifications for seller {}: {}",
                    integration.getSellerId(), e.getMessage());
        }
        integration.setNotificationDestinationId(null);
        integration.setNotificationSubscriptionIds(new ArrayList<>());
        integration.setNotificationsEnabled(false);
    }

    /**
     * Called from the public notification ingest endpoint. Extracts the
     * SKU from the payload and kicks off an auto-enhance pass against
     * the live eBay listing.
     */
    @Async
    public void handleNotification(String userId, Map<String, Object> payload) {
        if (payload == null) return;
        log.info("eBay notification received for user {}: {}", userId, payload.get("topic"));

        String sku = extractString(payload, "sku");
        String marketplaceId = extractString(payload, "marketplaceId");
        if (sku == null || sku.isBlank()) {
            log.info("eBay notification had no SKU — skipping auto-enhance");
            return;
        }

        EbayIntegration integration = getIntegration(userId);
        if (integration == null || !integration.isNotificationsEnabled()) {
            log.warn("Ignoring eBay notification for user {} — integration missing or notifications disabled", userId);
            return;
        }

        EbayProductItem raw = EbayProductItem.builder()
                .localId(UUID.randomUUID().toString())
                .sku(sku)
                .marketplaceId(marketplaceId == null
                        ? integration.getActiveMarketplaceId() : marketplaceId)
                .status("PENDING")
                .lifecycleStatus("SYNCED")
                .condition("NEW")
                .build();

        EbayProductEnhancementJob job = EbayProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("EBAY")
                .status("PROCESSING")
                .marketplaceId(raw.getMarketplaceId())
                .rawProducts(List.of(raw))
                .source("NOTIFICATION")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        jobDao.save(job);

        try {
            String accessToken = accessToken(integration);
            Map<String, Object> listing = apiService.getInventoryItem(accessToken,
                    integration.getEnvironment(), raw.getMarketplaceId(), sku);

            raw.setTitle(str(listing, "title"));
            raw.setBodyHtml(str(listing, "bodyHtml"));
            raw.setBrand(str(listing, "brand"));

            ShopifyProductDto shopifyDto = ebayItemToShopifyDto(raw);
            ProductEnhancementJob.ProductItem enhancedShopify = aiEnhancementService.enhanceSingle(shopifyDto);
            EbayProductItem enhanced = mergeEnhancementIntoRaw(raw, enhancedShopify);

            EbayDirectProductUpdateRequest patch = new EbayDirectProductUpdateRequest();
            patch.setTitle(enhanced.getEnhancedTitle());
            patch.setBodyHtml(enhanced.getEnhancedBodyHtml());
            patch.setBrand(enhanced.getEnhancedBrand());
            patch.setAspects(enhanced.getEnhancedAspects());

            apiService.patchListing(accessToken, integration.getEnvironment(),
                    raw.getMarketplaceId(), sku, patch,
                    integration.getDefaultCategoryId(),
                    integration.getDefaultMerchantLocationKey(),
                    integration.getDefaultFulfillmentPolicyId(),
                    integration.getDefaultPaymentPolicyId(),
                    integration.getDefaultReturnPolicyId());

            enhanced.setStatus("PUBLISHED");
            job.setEnhancedProducts(List.of(enhanced));
            job.setStatus("PUBLISHED");
        } catch (Exception e) {
            log.error("eBay notification auto-enhance failed for SKU {}", sku, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Single-product direct edit (live eBay listing)
    // ──────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getProductDetails(String userId, String sku) {
        EbayIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String mp = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();
        Map<String, Object> listing = apiService.getInventoryItem(accessToken,
                integration.getEnvironment(), mp, sku);

        // Enrich with offer-level price + currency + itemId
        try {
            List<Map<String, Object>> offers = apiService.listOffersForSku(accessToken,
                    integration.getEnvironment(), sku);
            Map<String, Object> match = offers.stream()
                    .filter(o -> mp.equals(Objects.toString(o.get("marketplaceId"), "")))
                    .findFirst()
                    .orElse(offers.isEmpty() ? null : offers.get(0));
            if (match != null) {
                listing.put("offerId",    Objects.toString(match.get("offerId"),  ""));
                listing.put("itemId",     Objects.toString(match.get("listingId"), ""));
                listing.put("categoryId", Objects.toString(match.get("categoryId"), ""));
                listing.put("status",     Objects.toString(match.get("status"),  "INACTIVE"));
                Object ps = match.get("pricingSummary");
                if (ps instanceof Map<?, ?> pricing && pricing.get("price") instanceof Map<?, ?> pm) {
                    listing.put("price",    Objects.toString(pm.get("value"),    ""));
                    listing.put("currency", Objects.toString(pm.get("currency"), ""));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich eBay listing {} with offer data: {}", sku, e.getMessage());
        }
        return listing;
    }

    public Map<String, Object> enhanceSingleProductDirect(String userId, String sku) {
        EbayIntegration integration = requireIntegration(userId);
        Map<String, Object> productMap = getProductDetails(userId, sku);

        ShopifyProductDto dto = ShopifyProductDto.builder()
                .title(str(productMap, "title"))
                .bodyHtml(str(productMap, "bodyHtml"))
                .vendor(str(productMap, "brand"))
                .productType(str(productMap, "categoryId"))
                .build();

        ProductEnhancementJob.ProductItem enhanced = aiEnhancementService.enhanceSingle(dto);

        Map<String, Object> suggestions = new LinkedHashMap<>();
        suggestions.put("title",    enhanced.getEnhancedTitle());
        suggestions.put("bodyHtml", enhanced.getEnhancedBodyHtml());
        suggestions.put("brand",    enhanced.getVendor());
        suggestions.put("subtitle", enhanced.getEnhancedProductType());
        productMap.put("aiSuggestions", suggestions);
        // Suppress unused-parameter warning (integration kept for future scope refinement).
        if (integration == null) return productMap;
        return productMap;
    }

    public void updateProductDirect(String userId, String sku, EbayDirectProductUpdateRequest req) {
        EbayIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String mp = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();

        apiService.patchListing(accessToken, integration.getEnvironment(), mp, sku, req,
                integration.getDefaultCategoryId(),
                integration.getDefaultMerchantLocationKey(),
                integration.getDefaultFulfillmentPolicyId(),
                integration.getDefaultPaymentPolicyId(),
                integration.getDefaultReturnPolicyId());
        log.info("Direct eBay listing patch applied for user {} sku {}", userId, sku);
    }

    public List<String> addProductImage(String userId, String sku, String src) {
        if (src == null || src.isBlank()) throw new RuntimeException("Image URL is required");
        EbayIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String mp = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();

        Map<String, Object> listing = apiService.getInventoryItem(accessToken,
                integration.getEnvironment(), mp, sku);

        List<String> images = new ArrayList<>();
        Object existing = listing.get("images");
        if (existing instanceof List<?> l) for (Object o : l) if (o != null) images.add(o.toString());
        if (images.contains(src)) return images;
        if (images.size() >= 24) {
            throw new RuntimeException("eBay supports at most 24 images per listing");
        }
        images.add(src);

        EbayDirectProductUpdateRequest req = new EbayDirectProductUpdateRequest();
        req.setImages(images);
        apiService.patchListing(accessToken, integration.getEnvironment(), mp, sku, req,
                integration.getDefaultCategoryId(),
                integration.getDefaultMerchantLocationKey(),
                integration.getDefaultFulfillmentPolicyId(),
                integration.getDefaultPaymentPolicyId(),
                integration.getDefaultReturnPolicyId());
        return images;
    }

    public List<String> removeProductImage(String userId, String sku, String imageId) {
        if (imageId == null || imageId.isBlank()) throw new RuntimeException("Image id is required");
        EbayIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);
        String mp = integration.getActiveMarketplaceId() == null
                ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId();

        Map<String, Object> listing = apiService.getInventoryItem(accessToken,
                integration.getEnvironment(), mp, sku);

        List<String> images = new ArrayList<>();
        Object existing = listing.get("images");
        if (existing instanceof List<?> l) for (Object o : l) if (o != null) images.add(o.toString());
        images.removeIf(url -> url.equals(imageId));

        EbayDirectProductUpdateRequest req = new EbayDirectProductUpdateRequest();
        req.setImages(images);
        apiService.patchListing(accessToken, integration.getEnvironment(), mp, sku, req,
                integration.getDefaultCategoryId(),
                integration.getDefaultMerchantLocationKey(),
                integration.getDefaultFulfillmentPolicyId(),
                integration.getDefaultPaymentPolicyId(),
                integration.getDefaultReturnPolicyId());
        return images;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Jobs
    // ──────────────────────────────────────────────────────────────────────────

    public List<EbayProductEnhancementJob> getJobs(String userId) {
        List<EbayProductEnhancementJob> all = jobDao.findByUserIdOrderByCreatedAtDesc(userId);
        List<EbayProductEnhancementJob> out = new ArrayList<>();
        for (EbayProductEnhancementJob job : all) {
            if (backfillProductItems(job)) jobDao.save(job);
            out.add(job);
        }
        return out;
    }

    public EbayProductEnhancementJob getJob(String userId, String jobId) {
        EbayProductEnhancementJob job = jobDao.findById(jobId)
                .filter(j -> userId.equals(j.getUserId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));
        if (backfillProductItems(job)) jobDao.save(job);
        return job;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Drafts
    // ──────────────────────────────────────────────────────────────────────────

    public Map<String, Object> enhanceDraft(String userId, String jobId, String localId) {
        EbayProductEnhancementJob job = getJob(userId, jobId);
        EbayProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new RuntimeException("Title is required before running AI enhancement");
        }
        try {
            ShopifyProductDto dto = ebayItemToShopifyDto(item);
            ProductEnhancementJob.ProductItem enhancedShopify = aiEnhancementService.enhanceSingle(dto);
            EbayProductItem enhanced = mergeEnhancementIntoRaw(item, enhancedShopify);

            item.setEnhancedTitle(enhanced.getEnhancedTitle());
            item.setEnhancedBodyHtml(enhanced.getEnhancedBodyHtml());
            item.setEnhancedSubtitle(enhanced.getEnhancedSubtitle());
            item.setEnhancedBrand(enhanced.getEnhancedBrand());
            item.setEnhancedAspects(enhanced.getEnhancedAspects());
            item.setStatus("ENHANCED");

            String current = item.getLifecycleStatus();
            if ("SYNCED".equals(current) || "PUBLISHED".equals(current)) {
                item.setLifecycleStatus("PENDING_SYNC");
            }

            job.setUpdatedAt(Instant.now());
            jobDao.save(job);
            return draftToDetailMap(job, item);
        } catch (Exception e) {
            log.error("eBay AI enhancement failed for draft {} (job {}): {}",
                    localId, jobId, e.getMessage());
            throw new RuntimeException("AI enhancement failed: " + e.getMessage());
        }
    }

    public Map<String, Object> getDraft(String userId, String jobId, String localId) {
        EbayProductEnhancementJob job = getJob(userId, jobId);
        EbayProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        return draftToDetailMap(job, item);
    }

    public Map<String, Object> updateDraft(String userId, String jobId, String localId,
                                           EbayDirectProductUpdateRequest req) {
        EbayProductEnhancementJob job = getJob(userId, jobId);
        EbayProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        if (req.getTitle() != null)    { item.setTitle(req.getTitle());       item.setEnhancedTitle(null); }
        if (req.getBodyHtml() != null) { item.setBodyHtml(req.getBodyHtml()); item.setEnhancedBodyHtml(null); }
        if (req.getSubtitle() != null) { item.setSubtitle(req.getSubtitle()); item.setEnhancedSubtitle(null); }
        if (req.getBrand() != null)    { item.setBrand(req.getBrand());       item.setEnhancedBrand(null); }
        if (req.getAspects() != null)  { item.setAspects(req.getAspects());   item.setEnhancedAspects(null); }
        if (req.getMpn() != null)            item.setMpn(req.getMpn());
        if (req.getUpc() != null)            item.setUpc(req.getUpc());
        if (req.getCondition() != null)      item.setCondition(req.getCondition());
        if (req.getConditionDescription() != null) item.setConditionDescription(req.getConditionDescription());
        if (req.getCategoryId() != null)     item.setCategoryId(req.getCategoryId());
        if (req.getStatus() != null)         item.setEbayStatus(req.getStatus());
        if (req.getPrice() != null)          item.setPrice(req.getPrice());
        if (req.getCurrency() != null)       item.setCurrency(req.getCurrency());
        if (req.getSku() != null)            item.setSku(req.getSku());
        if (req.getInventoryQuantity() != null) item.setInventoryQuantity(req.getInventoryQuantity());
        if (req.getImages() != null)         item.setImages(req.getImages());

        String current = item.getLifecycleStatus();
        if ("SYNCED".equals(current) || "PUBLISHED".equals(current)) {
            item.setLifecycleStatus("PENDING_SYNC");
        }

        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        return draftToDetailMap(job, item);
    }

    public Map<String, Object> publishDraft(String userId, String jobId, String localId) {
        EbayIntegration integration = requireIntegration(userId);
        String accessToken = accessToken(integration);

        EbayProductEnhancementJob job = getJob(userId, jobId);
        EbayProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));

        try {
            if (item.getMarketplaceId() == null || item.getMarketplaceId().isBlank()) {
                item.setMarketplaceId(integration.getActiveMarketplaceId() == null
                        ? DEFAULT_MARKETPLACE_ID : integration.getActiveMarketplaceId());
            }
            if (item.getSku() == null || item.getSku().isBlank()) {
                item.setSku(generateSku(item));
            }

            publishItem(integration, accessToken, item);
            item.setLifecycleStatus("PUBLISHED");
            job.setUpdatedAt(Instant.now());
            jobDao.save(job);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sku",             item.getSku());
            result.put("itemId",          item.getItemId() == null ? "" : item.getItemId());
            result.put("offerId",         item.getOfferId() == null ? "" : item.getOfferId());
            result.put("lifecycleStatus", item.getLifecycleStatus());
            result.put("jobId",           job.getId());
            result.put("localId",         item.getLocalId());
            return result;
        } catch (Exception e) {
            log.error("Failed to publish eBay draft {} (job {}): {}", localId, jobId, e.getMessage());
            throw new RuntimeException("Failed to publish draft: " + e.getMessage());
        }
    }

    public void deleteDraft(String userId, String jobId, String localId) {
        EbayProductEnhancementJob job = getJob(userId, jobId);
        EbayProductItem item = findItem(job, localId)
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

    private String accessToken(EbayIntegration integration) {
        return oauth.getAccessToken(
                encryptionUtils.decrypt(integration.getEncryptedRefreshToken()),
                integration.getScopes(),
                integration.getEnvironment());
    }

    private EbayIntegration requireIntegration(String userId) {
        EbayIntegration active = getIntegration(userId);
        if (active == null) {
            throw new RuntimeException("No eBay integration found. Please connect your store first.");
        }
        return active;
    }

    private String generateSku(EbayProductItem item) {
        String base = item.getTitle() == null ? "SKU" : item.getTitle();
        String slug = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        if (slug.length() > 24) slug = slug.substring(0, 24);
        if (slug.endsWith("-")) slug = slug.substring(0, slug.length() - 1);
        if (slug.isBlank()) slug = "sku";
        return slug + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private boolean backfillProductItems(EbayProductEnhancementJob job) {
        boolean mutated = false;
        mutated |= backfillList(job.getRawProducts());
        mutated |= backfillList(job.getEnhancedProducts());
        return mutated;
    }

    private boolean backfillList(List<EbayProductItem> items) {
        if (items == null) return false;
        boolean mutated = false;
        for (EbayProductItem item : items) {
            if (item.getLocalId() == null || item.getLocalId().isBlank()) {
                item.setLocalId(UUID.randomUUID().toString());
                mutated = true;
            }
            if (item.getLifecycleStatus() == null || item.getLifecycleStatus().isBlank()) {
                boolean inStore = (item.getSku() != null && !item.getSku().isBlank())
                        || (item.getItemId() != null && !item.getItemId().isBlank());
                item.setLifecycleStatus(inStore ? "SYNCED" : "DRAFT");
                mutated = true;
            }
        }
        return mutated;
    }

    private Optional<EbayProductItem> findItem(EbayProductEnhancementJob job, String localId) {
        if (localId == null || localId.isBlank()) return Optional.empty();
        if (job.getEnhancedProducts() != null) {
            for (EbayProductItem p : job.getEnhancedProducts()) {
                if (localId.equals(p.getLocalId())) return Optional.of(p);
            }
        }
        if (job.getRawProducts() != null) {
            for (EbayProductItem p : job.getRawProducts()) {
                if (localId.equals(p.getLocalId())) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> draftToDetailMap(EbayProductEnhancementJob job, EbayProductItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId",           job.getId());
        result.put("localId",         item.getLocalId());
        result.put("lifecycleStatus", nullToEmpty(item.getLifecycleStatus()));
        result.put("sku",             nullToEmpty(item.getSku()));
        result.put("itemId",          nullToEmpty(item.getItemId()));
        result.put("offerId",         nullToEmpty(item.getOfferId()));
        result.put("marketplaceId",   nullToEmpty(item.getMarketplaceId()));
        result.put("categoryId",      nullToEmpty(item.getCategoryId()));

        result.put("title",                 firstNonBlank(item.getEnhancedTitle(),    item.getTitle()));
        result.put("bodyHtml",              firstNonBlank(item.getEnhancedBodyHtml(), item.getBodyHtml()));
        result.put("subtitle",              firstNonBlank(item.getEnhancedSubtitle(), item.getSubtitle()));
        result.put("brand",                 firstNonBlank(item.getEnhancedBrand(),    item.getBrand()));
        result.put("mpn",                   nullToEmpty(item.getMpn()));
        result.put("upc",                   nullToEmpty(item.getUpc()));
        result.put("condition",             firstNonBlank(item.getCondition(), "NEW"));
        result.put("conditionDescription",  nullToEmpty(item.getConditionDescription()));
        result.put("aspects",               item.getEnhancedAspects() != null
                && !item.getEnhancedAspects().isEmpty()
                ? item.getEnhancedAspects()
                : (item.getAspects() == null ? new LinkedHashMap<String, Object>() : item.getAspects()));

        result.put("status",            firstNonBlank(item.getEbayStatus(), "INACTIVE"));
        result.put("createdAt",         job.getCreatedAt() == null ? "" : job.getCreatedAt().toString());
        result.put("updatedAt",         job.getUpdatedAt() == null ? "" : job.getUpdatedAt().toString());

        result.put("price",             nullToEmpty(item.getPrice()));
        result.put("currency",          firstNonBlank(item.getCurrency(), defaultCurrencyFor(item.getMarketplaceId())));
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

    private EbayProductItem dtoToRawItem(EbayProductDto p) {
        return EbayProductItem.builder()
                .localId(UUID.randomUUID().toString())
                .sku(p.getSku())
                .itemId(p.getItemId())
                .offerId(p.getOfferId())
                .marketplaceId(p.getMarketplaceId())
                .categoryId(p.getCategoryId())
                .title(p.getTitle())
                .bodyHtml(p.getBodyHtml())
                .subtitle(p.getSubtitle())
                .brand(p.getBrand())
                .mpn(p.getMpn())
                .upc(p.getUpc())
                .condition(p.getCondition())
                .conditionDescription(p.getConditionDescription())
                .aspects(p.getAspects())
                .price(p.getPrice())
                .currency(p.getCurrency())
                .inventoryQuantity(p.getInventoryQuantity())
                .ebayStatus(p.getStatus())
                .images(p.getImages())
                .status("PENDING")
                .build();
    }

    /**
     * Project an eBay item onto the Shopify DTO shape so the shared AI
     * service can operate on it. Fields are mapped conservatively:
     * brand → vendor, categoryId → productType, aspect values joined
     * into tags.
     */
    private ShopifyProductDto ebayItemToShopifyDto(EbayProductItem item) {
        String tags = "";
        if (item.getAspects() != null && !item.getAspects().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (List<String> values : item.getAspects().values()) {
                if (values == null) continue;
                for (String v : values) {
                    if (v == null || v.isBlank()) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(v);
                }
            }
            tags = sb.toString();
        }
        return ShopifyProductDto.builder()
                .title(item.getTitle())
                .bodyHtml(item.getBodyHtml())
                .vendor(item.getBrand())
                .productType(item.getCategoryId())
                .tags(tags)
                .price(item.getPrice())
                .inventoryQuantity(item.getInventoryQuantity())
                .images(item.getImages())
                .build();
    }

    private EbayProductDto shopifyDtoToEbayDto(ShopifyProductDto p) {
        return EbayProductDto.builder()
                .title(p.getTitle())
                .bodyHtml(p.getBodyHtml())
                .brand(p.getVendor())
                .categoryId(p.getProductType())
                .condition("NEW")
                .price(p.getPrice())
                .currency("USD")
                .inventoryQuantity(p.getInventoryQuantity() == null ? 1 : p.getInventoryQuantity())
                .images(p.getImages())
                .build();
    }

    /**
     * Apply the Shopify-shaped enhancement back onto the eBay item.
     * enhanced tags (comma-separated) are folded into {@code aspects}
     * under a single "Tags" key so the AI suggestions aren't lost, and
     * the first bullet-list item derived from the body becomes the
     * subtitle suggestion.
     */
    private EbayProductItem mergeEnhancementIntoRaw(EbayProductItem raw,
                                                    ProductEnhancementJob.ProductItem shopify) {
        raw.setEnhancedTitle(shopify.getEnhancedTitle());
        raw.setEnhancedBodyHtml(shopify.getEnhancedBodyHtml());
        raw.setEnhancedBrand(shopify.getVendor());
        if (shopify.getEnhancedTags() != null && !shopify.getEnhancedTags().isBlank()) {
            Map<String, List<String>> enhancedAspects = raw.getAspects() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(raw.getAspects());
            List<String> tagValues = new ArrayList<>();
            for (String t : shopify.getEnhancedTags().split(",")) {
                String clean = t == null ? "" : t.trim();
                if (!clean.isBlank()) tagValues.add(clean);
            }
            if (!tagValues.isEmpty()) enhancedAspects.put("Tags", tagValues);
            raw.setEnhancedAspects(enhancedAspects);
        }
        // eBay subtitle is limited to 55 chars — use the first sentence of the enhanced body.
        if (shopify.getEnhancedBodyHtml() != null) {
            String plain = shopify.getEnhancedBodyHtml().replaceAll("<[^>]+>", " ").trim();
            if (!plain.isBlank()) {
                int cut = plain.indexOf('.');
                String subtitle = cut > 0 ? plain.substring(0, Math.min(cut, 55)) : plain;
                raw.setEnhancedSubtitle(subtitle.length() > 55 ? subtitle.substring(0, 55) : subtitle);
            }
        }
        if (raw.getStatus() == null || "PENDING".equals(raw.getStatus())) {
            raw.setStatus("ENHANCED");
        }
        return raw;
    }

    private static String defaultCurrencyFor(String marketplaceId) {
        if (marketplaceId == null) return "USD";
        return switch (marketplaceId.toUpperCase()) {
            case "EBAY_GB" -> "GBP";
            case "EBAY_DE", "EBAY_FR", "EBAY_IT", "EBAY_ES", "EBAY_NL", "EBAY_BE" -> "EUR";
            case "EBAY_AU" -> "AUD";
            case "EBAY_CA" -> "CAD";
            default         -> "USD";
        };
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
}
