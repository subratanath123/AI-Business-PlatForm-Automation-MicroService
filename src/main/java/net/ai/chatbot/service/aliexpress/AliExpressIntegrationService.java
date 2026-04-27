package net.ai.chatbot.service.aliexpress;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.AliExpressIntegrationDao;
import net.ai.chatbot.dao.AliExpressPendingConnectionDao;
import net.ai.chatbot.dao.AliExpressProductEnhancementJobDao;
import net.ai.chatbot.dto.aliexpress.AliExpressDirectProductUpdateRequest;
import net.ai.chatbot.dto.aliexpress.AliExpressProductDto;
import net.ai.chatbot.dto.shopify.ShopifyProductDto;
import net.ai.chatbot.entity.AliExpressIntegration;
import net.ai.chatbot.entity.AliExpressPendingConnection;
import net.ai.chatbot.entity.AliExpressProductEnhancementJob;
import net.ai.chatbot.entity.AliExpressProductEnhancementJob.AliExpressProductItem;
import net.ai.chatbot.entity.ProductEnhancementJob;
import net.ai.chatbot.entity.ProductEnhancementJob.ProductItem;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AliExpressIntegrationService {

    private final AliExpressIntegrationDao integrationDao;
    private final AliExpressPendingConnectionDao pendingDao;
    private final AliExpressProductEnhancementJobDao jobDao;
    private final AliExpressApiService apiService;
    private final AliExpressOAuthService oauth;
    private final ProductAIEnhancementService aiEnhancementService;
    private final EncryptionUtils encryptionUtils;

    // ── OAuth ────────────────────────────────────────────────────────────────

    public AliExpressPendingConnection initAuth(String userId) {
        AliExpressPendingConnection pending = AliExpressPendingConnection.builder()
                .id(UUID.randomUUID().toString())
                .state(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return pendingDao.save(pending);
    }

    public AliExpressIntegration finalizeAuth(String userId, String state, String code, String storeName) {
        AliExpressPendingConnection pending = pendingDao.findByState(state)
                .orElseThrow(() -> new RuntimeException("Unknown connection state"));
        if (!userId.equals(pending.getUserId())) {
            throw new RuntimeException("State does not belong to the current user");
        }

        Map<String, Object> tokens = oauth.exchangeAuthorizationCode(code);
        String refreshToken = Objects.toString(tokens.get("refresh_token"), null);
        if (refreshToken == null || refreshToken.isBlank()) {
            pendingDao.deleteByState(state);
            throw new RuntimeException("AliExpress did not return refresh_token — check app key/secret and redirect URI.");
        }

        String sellerId = firstNonBlank(
                tokens.get("seller_id"),
                tokens.get("user_id"),
                tokens.get("account_id"),
                tokens.get("account"));
        if (sellerId == null || sellerId.isBlank()) {
            sellerId = "ae-" + UUID.randomUUID().toString().substring(0, 10);
        }
        String nick = Objects.toString(tokens.get("user_nick"), "");
        String resolvedName = (storeName != null && !storeName.isBlank()) ? storeName
                : (!nick.isBlank() ? nick : sellerId);

        AliExpressIntegration saved = saveCredentials(userId, sellerId, nick, resolvedName, refreshToken);
        pendingDao.deleteByState(state);
        return saved;
    }

    private AliExpressIntegration saveCredentials(String userId, String sellerId, String sellerLoginId,
                                                  String storeName, String refreshToken) {
        String enc = encryptionUtils.encrypt(refreshToken);
        AliExpressIntegration existing = integrationDao.findByUserIdAndSellerId(userId, sellerId).orElse(null);

        if (existing != null) {
            existing.setSellerLoginId(sellerLoginId);
            existing.setStoreName(storeName);
            existing.setEncryptedRefreshToken(enc);
            existing.setConnected(true);
            existing.setUpdatedAt(Instant.now());
            integrationDao.save(existing);
            activateStore(userId, sellerId);
            return integrationDao.findByUserIdAndSellerId(userId, sellerId).orElse(existing);
        }

        AliExpressIntegration integration = AliExpressIntegration.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .sellerId(sellerId)
                .sellerLoginId(sellerLoginId)
                .storeName(storeName)
                .encryptedRefreshToken(enc)
                .defaultContentLocale("en")
                .connected(true)
                .notificationsEnabled(false)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        integrationDao.save(integration);
        activateStore(userId, sellerId);
        return integrationDao.findByUserIdAndSellerId(userId, sellerId).orElse(integration);
    }

    private static String firstNonBlank(Object... vals) {
        for (Object v : vals) {
            if (v == null) continue;
            String s = v.toString().trim();
            if (!s.isBlank()) return s;
        }
        return "";
    }

    private void activateStore(String userId, String sellerId) {
        List<AliExpressIntegration> all = integrationDao.findAllByUserId(userId);
        for (AliExpressIntegration s : all) {
            boolean should = s.getSellerId().equals(sellerId);
            if (s.isActive() != should) {
                s.setActive(should);
                s.setUpdatedAt(Instant.now());
                integrationDao.save(s);
            }
        }
    }

    public List<AliExpressIntegration> listStores(String userId) {
        List<AliExpressIntegration> stores = integrationDao.findAllByUserId(userId);
        boolean any = stores.stream().anyMatch(AliExpressIntegration::isActive);
        if (!any && !stores.isEmpty()) {
            activateStore(userId, stores.get(0).getSellerId());
            return integrationDao.findAllByUserId(userId);
        }
        return stores;
    }

    public AliExpressIntegration switchStore(String userId, String sellerId) {
        AliExpressIntegration t = integrationDao.findByUserIdAndSellerId(userId, sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not connected: " + sellerId));
        activateStore(userId, sellerId);
        return integrationDao.findByUserIdAndSellerId(userId, t.getSellerId()).orElse(t);
    }

    public void disconnectStore(String userId, String sellerId) {
        oauth.invalidate(decryptRefreshSafe(userId, sellerId));
        integrationDao.deleteByUserIdAndSellerId(userId, sellerId);
    }

    public void disconnectAllStores(String userId) {
        for (AliExpressIntegration s : integrationDao.findAllByUserId(userId)) {
            try {
                oauth.invalidate(encryptionUtils.decrypt(s.getEncryptedRefreshToken()));
            } catch (Exception ignored) {
            }
        }
        integrationDao.deleteByUserId(userId);
    }

    private String decryptRefreshSafe(String userId, String sellerId) {
        try {
            return integrationDao.findByUserIdAndSellerId(userId, sellerId)
                    .map(i -> encryptionUtils.decrypt(i.getEncryptedRefreshToken()))
                    .orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    public AliExpressIntegration getIntegration(String userId) {
        return integrationDao.findByUserIdAndActiveTrue(userId).orElse(null);
    }

    public AliExpressIntegration requireIntegration(String userId) {
        AliExpressIntegration i = getIntegration(userId);
        if (i == null) throw new RuntimeException("AliExpress is not connected");
        return i;
    }

    private String sessionKey(AliExpressIntegration integration) {
        String rt = encryptionUtils.decrypt(integration.getEncryptedRefreshToken());
        return oauth.getAccessToken(rt);
    }

    // ── Sync / parse / upload ────────────────────────────────────────────────

    public AliExpressProductEnhancementJob syncProductsFromStore(String userId, int limit) {
        AliExpressIntegration integration = requireIntegration(userId);
        String session = sessionKey(integration);

        List<AliExpressProductItem> rawItems = new ArrayList<>();
        int page = 1;
        int pageSize = Math.min(50, Math.max(1, limit));
        while (rawItems.size() < limit) {
            List<Map<String, Object>> rows = apiService.listProductsPage(session, "onSelling", page, pageSize);
            if (rows.isEmpty()) break;
            for (Map<String, Object> row : rows) {
                if (rawItems.size() >= limit) break;
                rawItems.add(mapSyncRowToItem(row));
            }
            if (rows.size() < pageSize) break;
            page++;
            if (page > 100) break;
        }

        AliExpressProductEnhancementJob job = AliExpressProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("ALIEXPRESS")
                .status("PENDING")
                .rawProducts(rawItems)
                .source("SYNC")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return jobDao.save(job);
    }

    private AliExpressProductItem mapSyncRowToItem(Map<String, Object> row) {
        String pid = str(row, "product_id", "productId");
        String subject = str(row, "subject", "product_subject");
        String imgs = str(row, "image_u_r_ls", "image_urls", "src");
        List<String> images = new ArrayList<>();
        if (imgs != null && !imgs.isBlank()) {
            for (String p : imgs.split(";")) {
                if (p != null && !p.isBlank()) images.add(p.trim());
            }
        }
        return AliExpressProductItem.builder()
                .localId(UUID.randomUUID().toString())
                .productId(pid)
                .shopifyId(pid)
                .title(subject)
                .bodyHtml("")
                .price(str(row, "product_min_price", "product_max_price"))
                .currency(str(row, "currency_code", "currencyCode"))
                .aeStatus(str(row, "product_status_type", "ws_display"))
                .images(images)
                .lifecycleStatus("SYNCED")
                .build();
    }

    private static String str(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return "";
    }

    public List<AliExpressProductDto> parseProductsWithAI(String content, String fileName) {
        List<ShopifyProductDto> parsed = aiEnhancementService.parseProductsWithAI(content, fileName);
        return parsed.stream().map(this::shopifyToAeDto).collect(Collectors.toList());
    }

    public List<AliExpressProductDto> parseProductsFromImages(List<String> imageUrls) {
        List<ShopifyProductDto> parsed = aiEnhancementService.parseProductsFromImages(imageUrls);
        return parsed.stream().map(this::shopifyToAeDto).collect(Collectors.toList());
    }

    public AliExpressProductEnhancementJob createUploadJob(String userId, List<AliExpressProductDto> products) {
        List<AliExpressProductItem> rawItems = products.stream()
                .map(this::dtoToRawItem)
                .peek(p -> {
                    p.setLifecycleStatus("DRAFT");
                    if (p.getLocalId() == null || p.getLocalId().isBlank()) {
                        p.setLocalId(UUID.randomUUID().toString());
                    }
                })
                .collect(Collectors.toList());

        AliExpressProductEnhancementJob job = AliExpressProductEnhancementJob.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .platform("ALIEXPRESS")
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
        AliExpressProductEnhancementJob job = jobDao.findById(jobId)
                .filter(j -> userId.equals(j.getUserId()))
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        job.setStatus("PROCESSING");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        try {
            List<ShopifyProductDto> shopifyDtos = job.getRawProducts().stream()
                    .map(this::itemToShopifyDto).collect(Collectors.toList());
            List<ProductItem> enhanced = aiEnhancementService.enhanceProducts(shopifyDtos);
            List<AliExpressProductItem> mapped = new ArrayList<>();
            for (int i = 0; i < enhanced.size() && i < job.getRawProducts().size(); i++) {
                mapped.add(mergeEnhancement(job.getRawProducts().get(i), enhanced.get(i)));
            }
            job.setEnhancedProducts(mapped);
            job.setStatus("ENHANCED");
        } catch (Exception e) {
            log.error("AliExpress enhance failed job {}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
    }

    public void pushProductsToAliExpress(String userId, String jobId, List<String> productIds) {
        AliExpressIntegration integration = requireIntegration(userId);
        String session = sessionKey(integration);
        String locale = integration.getDefaultContentLocale() == null ? "en" : integration.getDefaultContentLocale();

        AliExpressProductEnhancementJob job = jobDao.findById(jobId)
                .filter(j -> userId.equals(j.getUserId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));
        if (!"ENHANCED".equals(job.getStatus())) {
            throw new RuntimeException("Job must be in ENHANCED status before publishing");
        }
        List<AliExpressProductItem> list = job.getEnhancedProducts().stream()
                .filter(p -> productIds == null || productIds.isEmpty()
                        || productIds.contains(p.getProductId())
                        || productIds.contains(p.getLocalId()))
                .collect(Collectors.toList());

        for (AliExpressProductItem item : list) {
            if (item.getProductId() == null || item.getProductId().isBlank()) {
                log.warn("Skipping AliExpress push — no product_id on draft item {}", item.getLocalId());
                continue;
            }
            String title = firstNonBlank(item.getEnhancedTitle(), item.getTitle());
            String body = firstNonBlank(item.getEnhancedBodyHtml(), item.getBodyHtml());
            apiService.editProductTitleAndDetail(session, item.getProductId(), locale, title, body);
            item.setStatus("PUBLISHED");
            item.setLifecycleStatus("PUBLISHED");
        }
        job.setStatus("PUBLISHED");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
    }

    // ── Single product (live) ─────────────────────────────────────────────────

    public Map<String, Object> getProductDetails(String userId, String productId) {
        AliExpressIntegration integration = requireIntegration(userId);
        JsonNode body = apiService.getProductInfo(sessionKey(integration), productId);
        return apiService.flattenProductInfo(body, productId);
    }

    public Map<String, Object> enhanceSingleProductDirect(String userId, String productId) {
        requireIntegration(userId);
        Map<String, Object> productMap = getProductDetails(userId, productId);
        ShopifyProductDto dto = ShopifyProductDto.builder()
                .title(Objects.toString(productMap.get("title"), ""))
                .bodyHtml(Objects.toString(productMap.get("bodyHtml"), ""))
                .vendor(Objects.toString(productMap.get("brandName"), ""))
                .productType(Objects.toString(productMap.get("categoryId"), ""))
                .build();
        ProductItem enhanced = aiEnhancementService.enhanceSingle(dto);
        Map<String, Object> suggestions = new LinkedHashMap<>();
        suggestions.put("title", enhanced.getEnhancedTitle());
        suggestions.put("bodyHtml", enhanced.getEnhancedBodyHtml());
        suggestions.put("brandName", enhanced.getVendor());
        productMap.put("aiSuggestions", suggestions);
        return productMap;
    }

    public void updateProductDirect(String userId, String productId, AliExpressDirectProductUpdateRequest req) {
        AliExpressIntegration integration = requireIntegration(userId);
        String session = sessionKey(integration);
        String locale = integration.getDefaultContentLocale() == null ? "en" : integration.getDefaultContentLocale();
        String title = req.getTitle();
        String body = req.getBodyHtml();
        boolean hasText = (title != null && !title.isBlank()) || (body != null && !body.isBlank());
        boolean hasImages = req.getImages() != null && !req.getImages().isEmpty();
        if (!hasText && !hasImages) {
            throw new RuntimeException("Nothing to update — provide title, bodyHtml, and/or images");
        }
        if (hasText) {
            String t = title != null && !title.isBlank() ? title : null;
            String b = body != null && !body.isBlank() ? body : null;
            apiService.editProductTitleAndDetail(session, productId, locale, t, b);
        }
        if (hasImages) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> edit = new LinkedHashMap<>();
                edit.put("product_id", Long.parseLong(productId));
                edit.put("main_image_urls_list", req.getImages());
                String json = om.writeValueAsString(edit);
                apiService.execute("aliexpress.solution.product.edit", session, Map.of("edit_product_request", json));
            } catch (Exception e) {
                throw new RuntimeException("Image update failed: " + e.getMessage(), e);
            }
        }
    }

    public List<String> addProductImage(String userId, String productId, String src) {
        if (src == null || src.isBlank()) throw new RuntimeException("Image URL is required");
        Map<String, Object> cur = getProductDetails(userId, productId);
        List<String> imgs = new ArrayList<>();
        Object ex = cur.get("images");
        if (ex instanceof List<?> l) {
            for (Object o : l) if (o != null) imgs.add(o.toString());
        }
        if (imgs.contains(src)) return imgs;
        if (imgs.size() >= 6) throw new RuntimeException("AliExpress supports at most 6 main images per listing");
        imgs.add(src);
        AliExpressDirectProductUpdateRequest req = new AliExpressDirectProductUpdateRequest();
        req.setImages(imgs);
        updateProductDirect(userId, productId, req);
        return imgs;
    }

    public List<String> removeProductImage(String userId, String productId, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) throw new RuntimeException("Image URL is required");
        Map<String, Object> cur = getProductDetails(userId, productId);
        List<String> imgs = new ArrayList<>();
        Object ex = cur.get("images");
        if (ex instanceof List<?> l) {
            for (Object o : l) if (o != null) imgs.add(o.toString());
        }
        imgs.removeIf(imageUrl::equals);
        AliExpressDirectProductUpdateRequest req = new AliExpressDirectProductUpdateRequest();
        req.setImages(imgs);
        updateProductDirect(userId, productId, req);
        return imgs;
    }

    public AliExpressIntegration toggleNotifications(String userId, boolean enable) {
        throw new RuntimeException(
                "AliExpress message push is configured in the Open Platform console — automatic webhook auto-enhance "
                        + "is not implemented for this channel yet.");
    }

    // ── Jobs ─────────────────────────────────────────────────────────────────

    public List<AliExpressProductEnhancementJob> getJobs(String userId) {
        return jobDao.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public AliExpressProductEnhancementJob getJob(String userId, String jobId) {
        return jobDao.findById(jobId)
                .filter(j -> userId.equals(j.getUserId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));
    }

    // ── Drafts ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getDraft(String userId, String jobId, String localId) {
        AliExpressProductEnhancementJob job = getJob(userId, jobId);
        AliExpressProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        return draftToMap(job, item);
    }

    public Map<String, Object> updateDraft(String userId, String jobId, String localId,
                                           AliExpressDirectProductUpdateRequest req) {
        AliExpressProductEnhancementJob job = getJob(userId, jobId);
        AliExpressProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        if (req.getTitle() != null) {
            item.setTitle(req.getTitle());
            item.setEnhancedTitle(null);
        }
        if (req.getBodyHtml() != null) {
            item.setBodyHtml(req.getBodyHtml());
            item.setEnhancedBodyHtml(null);
        }
        if (req.getBrandName() != null) {
            item.setBrandName(req.getBrandName());
            item.setEnhancedBrandName(null);
        }
        if (req.getCategoryId() != null) item.setCategoryId(req.getCategoryId());
        if (req.getPrice() != null) item.setPrice(req.getPrice());
        if (req.getCurrency() != null) item.setCurrency(req.getCurrency());
        if (req.getSkuCode() != null) item.setSkuCode(req.getSkuCode());
        if (req.getInventoryQuantity() != null) item.setInventoryQuantity(req.getInventoryQuantity());
        if (req.getImages() != null) item.setImages(req.getImages());
        if (req.getAeStatus() != null) item.setAeStatus(req.getAeStatus());
        bumpLifecycle(item);
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        return draftToMap(job, item);
    }

    public Map<String, Object> enhanceDraft(String userId, String jobId, String localId) {
        AliExpressProductEnhancementJob job = getJob(userId, jobId);
        AliExpressProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new RuntimeException("Title is required before running AI enhancement");
        }
        ShopifyProductDto dto = itemToShopifyDto(item);
        ProductItem en = aiEnhancementService.enhanceSingle(dto);
        item.setEnhancedTitle(en.getEnhancedTitle());
        item.setEnhancedBodyHtml(en.getEnhancedBodyHtml());
        item.setEnhancedBrandName(en.getVendor());
        item.setStatus("ENHANCED");
        bumpLifecycle(item);
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        return draftToMap(job, item);
    }

    public Map<String, Object> publishDraft(String userId, String jobId, String localId) {
        AliExpressIntegration integration = requireIntegration(userId);
        String session = sessionKey(integration);
        String locale = integration.getDefaultContentLocale() == null ? "en" : integration.getDefaultContentLocale();

        AliExpressProductEnhancementJob job = getJob(userId, jobId);
        AliExpressProductItem item = findItem(job, localId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + localId));
        if (item.getProductId() == null || item.getProductId().isBlank()) {
            throw new RuntimeException(
                    "This draft has no AliExpress product_id. Create the listing in Seller Center, sync it here, "
                            + "then enhance and publish — posting brand-new products requires full category schema.");
        }
        String title = firstNonBlank(item.getEnhancedTitle(), item.getTitle());
        String body = firstNonBlank(item.getEnhancedBodyHtml(), item.getBodyHtml());
        if ((title == null || title.isBlank()) && (body == null || body.isBlank())) {
            throw new RuntimeException("Title or description is required before publishing");
        }
        apiService.editProductTitleAndDetail(session, item.getProductId(), locale, title, body);
        item.setLifecycleStatus("PUBLISHED");
        item.setStatus("PUBLISHED");
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("productId", item.getProductId());
        res.put("lifecycleStatus", item.getLifecycleStatus());
        res.put("jobId", job.getId());
        res.put("localId", item.getLocalId());
        return res;
    }

    public void deleteDraft(String userId, String jobId, String localId) {
        AliExpressProductEnhancementJob job = getJob(userId, jobId);
        List<AliExpressProductItem> raw = job.getRawProducts() == null ? new ArrayList<>() : new ArrayList<>(job.getRawProducts());
        List<AliExpressProductItem> en = job.getEnhancedProducts() == null ? new ArrayList<>() : new ArrayList<>(job.getEnhancedProducts());
        boolean removed = raw.removeIf(p -> localId.equals(p.getLocalId()));
        removed |= en.removeIf(p -> localId.equals(p.getLocalId()));
        if (!removed) {
            throw new RuntimeException("Draft not found: " + localId);
        }
        job.setRawProducts(raw);
        job.setEnhancedProducts(en);
        job.setUpdatedAt(Instant.now());
        jobDao.save(job);
    }

    private void bumpLifecycle(AliExpressProductItem item) {
        String c = item.getLifecycleStatus();
        if ("SYNCED".equals(c) || "PUBLISHED".equals(c)) {
            item.setLifecycleStatus("PENDING_SYNC");
        }
    }

    private Optional<AliExpressProductItem> findItem(AliExpressProductEnhancementJob job, String localId) {
        if (job.getRawProducts() != null) {
            for (AliExpressProductItem p : job.getRawProducts()) {
                if (localId.equals(p.getLocalId())) return Optional.of(p);
            }
        }
        if (job.getEnhancedProducts() != null) {
            for (AliExpressProductItem p : job.getEnhancedProducts()) {
                if (localId.equals(p.getLocalId())) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> draftToMap(AliExpressProductEnhancementJob job, AliExpressProductItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobId", job.getId());
        m.put("localId", item.getLocalId());
        m.put("lifecycleStatus", item.getLifecycleStatus());
        m.put("productId", item.getProductId());
        m.put("skuCode", item.getSkuCode());
        m.put("title", firstNonBlank(item.getEnhancedTitle(), item.getTitle()));
        m.put("bodyHtml", firstNonBlank(item.getEnhancedBodyHtml(), item.getBodyHtml()));
        m.put("brandName", firstNonBlank(item.getEnhancedBrandName(), item.getBrandName()));
        m.put("categoryId", item.getCategoryId());
        m.put("price", item.getPrice());
        m.put("currency", item.getCurrency());
        m.put("inventoryQuantity", item.getInventoryQuantity() == null ? 0 : item.getInventoryQuantity());
        m.put("aeStatus", item.getAeStatus());
        m.put("images", toImageMaps(item.getImages(), item.getTitle()));
        m.put("createdAt", job.getCreatedAt());
        m.put("updatedAt", job.getUpdatedAt());
        return m;
    }

    private List<Map<String, Object>> toImageMaps(List<String> urls, String alt) {
        if (urls == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 1;
        for (String u : urls) {
            if (u == null || u.isBlank()) continue;
            out.add(Map.of("id", "img-" + i, "src", u, "position", i, "alt", alt == null ? "" : alt));
            i++;
        }
        return out;
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private AliExpressProductDto shopifyToAeDto(ShopifyProductDto s) {
        return AliExpressProductDto.builder()
                .title(s.getTitle())
                .bodyHtml(s.getBodyHtml())
                .brandName(s.getVendor())
                .categoryId(s.getProductType())
                .build();
    }

    private AliExpressProductItem dtoToRawItem(AliExpressProductDto d) {
        String pid = d.getProductId();
        return AliExpressProductItem.builder()
                .localId(UUID.randomUUID().toString())
                .productId(pid)
                .shopifyId(pid)
                .skuCode(d.getSkuCode())
                .title(d.getTitle())
                .bodyHtml(d.getBodyHtml())
                .brandName(d.getBrandName())
                .categoryId(d.getCategoryId())
                .price(d.getPrice())
                .currency(d.getCurrency())
                .inventoryQuantity(d.getInventoryQuantity())
                .aeStatus(d.getAeStatus())
                .images(d.getImages())
                .build();
    }

    private ShopifyProductDto itemToShopifyDto(AliExpressProductItem p) {
        return ShopifyProductDto.builder()
                .title(firstNonBlank(p.getEnhancedTitle(), p.getTitle()))
                .bodyHtml(firstNonBlank(p.getEnhancedBodyHtml(), p.getBodyHtml()))
                .vendor(firstNonBlank(p.getEnhancedBrandName(), p.getBrandName()))
                .productType(p.getCategoryId())
                .build();
    }

    private AliExpressProductItem mergeEnhancement(AliExpressProductItem raw, ProductItem en) {
        String pid = raw.getProductId();
        return AliExpressProductItem.builder()
                .localId(raw.getLocalId())
                .productId(pid)
                .shopifyId(pid != null ? pid : raw.getShopifyId())
                .skuCode(raw.getSkuCode())
                .title(raw.getTitle())
                .bodyHtml(raw.getBodyHtml())
                .brandName(raw.getBrandName())
                .categoryId(raw.getCategoryId())
                .price(raw.getPrice())
                .currency(raw.getCurrency())
                .inventoryQuantity(raw.getInventoryQuantity())
                .aeStatus(raw.getAeStatus())
                .images(raw.getImages())
                .lifecycleStatus(raw.getLifecycleStatus())
                .enhancedTitle(en.getEnhancedTitle())
                .enhancedBodyHtml(en.getEnhancedBodyHtml())
                .enhancedBrandName(en.getVendor())
                .status("ENHANCED")
                .build();
    }
}
