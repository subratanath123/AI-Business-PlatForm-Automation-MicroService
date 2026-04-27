package net.ai.chatbot.service.ebay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.ebay.EbayDirectProductUpdateRequest;
import net.ai.chatbot.dto.ebay.EbayProductDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Low-level client for eBay's Sell APIs.
 * <p>
 * The services we use:
 * <ul>
 *   <li>Sell Account API — marketplaces the seller operates in.</li>
 *   <li>Commerce Identity API — the seller's userId/storeName.</li>
 *   <li>Sell Inventory API — inventory_item (SKU-keyed) + offer CRUD.</li>
 *   <li>Commerce Notification API — webhook destinations / subscriptions.</li>
 * </ul>
 * Every call expects a fresh access token from {@link EbayOAuthService}.
 */
@Service
@Slf4j
public class EbayApiService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** eBay's inventory_item cap (per docs). */
    private static final int MAX_INVENTORY_IMAGES = 24;

    private final WebClient.Builder webClientBuilder;
    private final EbayOAuthService oauth;

    public EbayApiService(WebClient.Builder webClientBuilder, EbayOAuthService oauth) {
        this.webClientBuilder = webClientBuilder;
        this.oauth = oauth;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Identity + Account APIs
    // ──────────────────────────────────────────────────────────────────────────

    /** Fetch the seller's canonical user-id + username. */
    public Map<String, Object> getSellerIdentity(String accessToken, String environment) {
        String raw = call(oauth.apiHost(environment), "GET",
                "/commerce/identity/v1/user/", null, null, accessToken, null);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(raw, Map.class);
            return map;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse eBay identity response: " + e.getMessage());
        }
    }

    /**
     * Returns the list of eBay marketplaces the seller participates in.
     * Shape: {@code [{id: "EBAY_US", name: "United States"}, ...]}.
     */
    public List<Map<String, Object>> getMarketplaces(String accessToken, String environment) {
        String raw;
        try {
            raw = call(oauth.apiHost(environment), "GET",
                    "/sell/account/v1/privilege", null, null, accessToken, null);
        } catch (Exception e) {
            log.warn("Failed to fetch eBay seller privileges: {}", e.getMessage());
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            JsonNode mps = node.path("sellerRegistrationCompleted");
            // The privilege endpoint doesn't enumerate marketplaces directly —
            // we fall back to the commonly used global marketplaces. Callers
            // can refine this via the fulfillment policy API if needed.
            if (mps.isBoolean() && mps.asBoolean()) {
                for (String id : defaultMarketplaces()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",   id);
                    m.put("name", id);
                    out.add(m);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse eBay privilege response: {}", e.getMessage());
        }
        if (out.isEmpty()) {
            for (String id : defaultMarketplaces()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",   id);
                m.put("name", id);
                out.add(m);
            }
        }
        return out;
    }

    private static List<String> defaultMarketplaces() {
        return List.of("EBAY_US", "EBAY_GB", "EBAY_DE", "EBAY_FR", "EBAY_IT",
                "EBAY_ES", "EBAY_AU", "EBAY_CA", "EBAY_NL", "EBAY_BE");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Inventory API — SKU-keyed inventory items + per-marketplace offers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Bulk-list inventory items for the seller. eBay paginates — we walk
     * pages until we hit {@code limit} items or run out.
     */
    public List<EbayProductDto> listInventoryItems(String accessToken, String environment,
                                                   String marketplaceId, int limit) {
        List<EbayProductDto> out = new ArrayList<>();
        String offset = "0";
        int pageLimit = Math.min(limit <= 0 ? 100 : limit, 100);

        while (out.size() < (limit <= 0 ? Integer.MAX_VALUE : limit)) {
            String query = "limit=" + pageLimit + "&offset=" + offset;
            String raw;
            try {
                raw = call(oauth.apiHost(environment), "GET",
                        "/sell/inventory/v1/inventory_item", query, null, accessToken,
                        marketplaceHeader(marketplaceId));
            } catch (Exception e) {
                log.warn("eBay inventory_item list failed at offset {}: {}", offset, e.getMessage());
                break;
            }

            try {
                JsonNode node = OBJECT_MAPPER.readTree(raw);
                JsonNode items = node.path("inventoryItems");
                if (!items.isArray() || items.size() == 0) break;
                for (JsonNode item : items) {
                    out.add(inventoryItemToDto(item, marketplaceId));
                    if (out.size() >= (limit <= 0 ? Integer.MAX_VALUE : limit)) break;
                }
                String next = node.path("next").asText(null);
                if (next == null || next.isBlank()) break;
                int qIdx = next.indexOf("offset=");
                if (qIdx < 0) break;
                String remainder = next.substring(qIdx + "offset=".length());
                int ampIdx = remainder.indexOf('&');
                offset = ampIdx < 0 ? remainder : remainder.substring(0, ampIdx);
            } catch (Exception e) {
                log.warn("eBay inventory_item parse failed: {}", e.getMessage());
                break;
            }
        }

        // Enrich with pricing / quantity from the offer, if any.
        for (EbayProductDto dto : out) {
            if (dto.getSku() == null) continue;
            try {
                List<Map<String, Object>> offers = listOffersForSku(accessToken, environment, dto.getSku());
                Map<String, Object> match = offers.stream()
                        .filter(o -> marketplaceId.equals(Objects.toString(o.get("marketplaceId"), "")))
                        .findFirst()
                        .orElse(offers.isEmpty() ? null : offers.get(0));
                if (match != null) {
                    dto.setOfferId(Objects.toString(match.get("offerId"), null));
                    dto.setCategoryId(Objects.toString(match.get("categoryId"), null));
                    Object format = match.get("format");
                    if ("FIXED_PRICE".equals(Objects.toString(format, ""))) {
                        Object priceObj = ((Map<?, ?>) match.getOrDefault("pricingSummary", Map.of()))
                                .get("price");
                        if (priceObj instanceof Map<?, ?> priceMap) {
                            dto.setPrice(Objects.toString(priceMap.get("value"), null));
                            dto.setCurrency(Objects.toString(priceMap.get("currency"), null));
                        }
                    }
                    dto.setStatus(Objects.toString(match.get("status"), "INACTIVE"));
                }
            } catch (Exception ignored) { /* enrichment is best-effort */ }
        }

        return out;
    }

    /** Fetch a single inventory item (returns the eBay native shape as a map). */
    public Map<String, Object> getInventoryItem(String accessToken, String environment,
                                                String marketplaceId, String sku) {
        String raw = call(oauth.apiHost(environment), "GET",
                "/sell/inventory/v1/inventory_item/" + urlEncode(sku), null, null,
                accessToken, marketplaceHeader(marketplaceId));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(raw, Map.class);
            return flattenInventoryItem(map, sku, marketplaceId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse eBay inventory item: " + e.getMessage());
        }
    }

    /**
     * Create or replace the inventory item for a given SKU. This is
     * content-only — the offer (with price / policies / marketplace)
     * is published separately via {@link #createOrReplaceOffer}.
     */
    public void createOrReplaceInventoryItem(String accessToken, String environment,
                                             String marketplaceId, String sku,
                                             Map<String, Object> body) {
        HttpHeaders headers = marketplaceHeader(marketplaceId);
        headers.set("Content-Language", contentLanguageFor(marketplaceId));
        call(oauth.apiHost(environment), "PUT",
                "/sell/inventory/v1/inventory_item/" + urlEncode(sku), null,
                body, accessToken, headers);
    }

    /**
     * List every offer for a SKU (one per marketplace at most). Useful
     * both for sync enrichment and to know whether we need to create
     * vs update the offer at publish time.
     */
    public List<Map<String, Object>> listOffersForSku(String accessToken, String environment,
                                                      String sku) {
        String raw = call(oauth.apiHost(environment), "GET",
                "/sell/inventory/v1/offer", "sku=" + urlEncode(sku),
                null, accessToken, null);
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            JsonNode offers = node.path("offers");
            if (offers.isArray()) {
                for (JsonNode offer : offers) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = OBJECT_MAPPER.convertValue(offer, Map.class);
                    out.add(m);
                }
            }
        } catch (Exception e) {
            log.warn("eBay offer listing parse failed for SKU {}: {}", sku, e.getMessage());
        }
        return out;
    }

    /**
     * Create a new offer for a SKU, or update the existing one if it
     * already exists for the target marketplace.
     * Returns the offerId.
     */
    public String createOrReplaceOffer(String accessToken, String environment,
                                       String marketplaceId, String sku,
                                       Map<String, Object> offerBody) {
        String existingOfferId = null;
        try {
            List<Map<String, Object>> existing = listOffersForSku(accessToken, environment, sku);
            for (Map<String, Object> o : existing) {
                if (marketplaceId.equals(Objects.toString(o.get("marketplaceId"), ""))) {
                    existingOfferId = Objects.toString(o.get("offerId"), null);
                    break;
                }
            }
        } catch (Exception ignored) { /* fall through and create */ }

        HttpHeaders headers = marketplaceHeader(marketplaceId);
        headers.set("Content-Language", contentLanguageFor(marketplaceId));

        if (existingOfferId != null && !existingOfferId.isBlank()) {
            call(oauth.apiHost(environment), "PUT",
                    "/sell/inventory/v1/offer/" + urlEncode(existingOfferId), null,
                    offerBody, accessToken, headers);
            return existingOfferId;
        }

        String raw = call(oauth.apiHost(environment), "POST",
                "/sell/inventory/v1/offer", null, offerBody, accessToken, headers);
        try {
            return OBJECT_MAPPER.readTree(raw).path("offerId").asText(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse eBay createOffer response: " + e.getMessage());
        }
    }

    /**
     * Publish an offer — this is the call that actually makes the item
     * appear on eBay. Returns the listingId/itemId once issued.
     */
    public String publishOffer(String accessToken, String environment, String offerId) {
        String raw = call(oauth.apiHost(environment), "POST",
                "/sell/inventory/v1/offer/" + urlEncode(offerId) + "/publish",
                null, null, accessToken, null);
        try {
            return OBJECT_MAPPER.readTree(raw).path("listingId").asText(null);
        } catch (Exception e) {
            log.warn("Could not parse publish-offer response: {}", e.getMessage());
            return null;
        }
    }

    public void deleteInventoryItem(String accessToken, String environment, String sku) {
        call(oauth.apiHost(environment), "DELETE",
                "/sell/inventory/v1/inventory_item/" + urlEncode(sku), null, null,
                accessToken, null);
    }

    public void withdrawOffer(String accessToken, String environment, String offerId) {
        call(oauth.apiHost(environment), "POST",
                "/sell/inventory/v1/offer/" + urlEncode(offerId) + "/withdraw",
                null, null, accessToken, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Patch-style update — used by the live product editor
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Merge-style update to a live listing: fetches the existing
     * inventory item + offer, applies the non-null fields in {@code req},
     * writes both back.
     */
    public void patchListing(String accessToken, String environment,
                             String marketplaceId, String sku,
                             EbayDirectProductUpdateRequest req,
                             String defaultCategoryId,
                             String defaultMerchantLocationKey,
                             String defaultFulfillmentPolicyId,
                             String defaultPaymentPolicyId,
                             String defaultReturnPolicyId) {
        Map<String, Object> current = getInventoryItem(accessToken, environment, marketplaceId, sku);

        Map<String, Object> product = extractMap(current.get("raw"), "product");
        if (product == null) product = new LinkedHashMap<>();
        Map<String, Object> availability = extractMap(current.get("raw"), "availability");
        if (availability == null) availability = new LinkedHashMap<>();

        if (req.getTitle() != null)                 product.put("title", req.getTitle());
        if (req.getBodyHtml() != null)              product.put("description", req.getBodyHtml());
        if (req.getSubtitle() != null)              product.put("subtitle", req.getSubtitle());
        if (req.getBrand() != null)                 product.put("brand", req.getBrand());
        if (req.getMpn() != null)                   product.put("mpn", req.getMpn());
        if (req.getUpc() != null) {
            product.put("upc", List.of(req.getUpc()));
        }
        if (req.getAspects() != null)               product.put("aspects", req.getAspects());
        if (req.getImages() != null) {
            List<String> images = req.getImages();
            if (images.size() > MAX_INVENTORY_IMAGES) images = images.subList(0, MAX_INVENTORY_IMAGES);
            product.put("imageUrls", images);
        }
        if (req.getInventoryQuantity() != null) {
            Map<String, Object> ship = new LinkedHashMap<>();
            ship.put("quantity", req.getInventoryQuantity());
            availability.put("shipToLocationAvailability", ship);
        }

        Map<String, Object> itemBody = new LinkedHashMap<>();
        itemBody.put("product",      product);
        if (req.getCondition() != null)        itemBody.put("condition", req.getCondition());
        if (req.getConditionDescription() != null)
            itemBody.put("conditionDescription", req.getConditionDescription());
        if (!availability.isEmpty())          itemBody.put("availability", availability);

        createOrReplaceInventoryItem(accessToken, environment, marketplaceId, sku, itemBody);

        // Update (or create) the offer for price / quantity / policies.
        boolean priceChange = req.getPrice() != null || req.getCurrency() != null;
        boolean qtyChange   = req.getInventoryQuantity() != null;
        boolean statusChange = req.getStatus() != null;
        boolean catChange   = req.getCategoryId() != null;
        if (priceChange || qtyChange || statusChange || catChange) {
            Map<String, Object> offerBody = new LinkedHashMap<>();
            offerBody.put("sku",             sku);
            offerBody.put("marketplaceId",   marketplaceId);
            offerBody.put("format",          "FIXED_PRICE");
            String categoryId = req.getCategoryId() == null || req.getCategoryId().isBlank()
                    ? defaultCategoryId : req.getCategoryId();
            if (categoryId != null && !categoryId.isBlank()) offerBody.put("categoryId", categoryId);

            if (priceChange) {
                Map<String, Object> price = new LinkedHashMap<>();
                price.put("value",    req.getPrice() == null ? "0" : req.getPrice());
                price.put("currency", req.getCurrency() == null || req.getCurrency().isBlank()
                        ? defaultCurrencyFor(marketplaceId) : req.getCurrency());
                Map<String, Object> pricing = new LinkedHashMap<>();
                pricing.put("price", price);
                offerBody.put("pricingSummary", pricing);
            }
            if (qtyChange) offerBody.put("availableQuantity", req.getInventoryQuantity());

            String mlk = req.getMerchantLocationKey() == null || req.getMerchantLocationKey().isBlank()
                    ? defaultMerchantLocationKey : req.getMerchantLocationKey();
            if (mlk != null && !mlk.isBlank()) offerBody.put("merchantLocationKey", mlk);

            Map<String, Object> policies = new LinkedHashMap<>();
            String fpId = req.getFulfillmentPolicyId() == null || req.getFulfillmentPolicyId().isBlank()
                    ? defaultFulfillmentPolicyId : req.getFulfillmentPolicyId();
            String ppId = req.getPaymentPolicyId() == null || req.getPaymentPolicyId().isBlank()
                    ? defaultPaymentPolicyId : req.getPaymentPolicyId();
            String rpId = req.getReturnPolicyId() == null || req.getReturnPolicyId().isBlank()
                    ? defaultReturnPolicyId : req.getReturnPolicyId();
            if (fpId != null) policies.put("fulfillmentPolicyId", fpId);
            if (ppId != null) policies.put("paymentPolicyId",     ppId);
            if (rpId != null) policies.put("returnPolicyId",      rpId);
            if (!policies.isEmpty()) offerBody.put("listingPolicies", policies);

            String offerId = createOrReplaceOffer(accessToken, environment, marketplaceId, sku, offerBody);

            // If the user set ACTIVE, publish the offer.
            if ("ACTIVE".equalsIgnoreCase(req.getStatus()) && offerId != null) {
                publishOffer(accessToken, environment, offerId);
            } else if ("INACTIVE".equalsIgnoreCase(req.getStatus()) && offerId != null) {
                try { withdrawOffer(accessToken, environment, offerId); }
                catch (Exception e) { log.warn("Failed to withdraw offer {}: {}", offerId, e.getMessage()); }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Register (or look up) a webhook destination. Returns the destination id.
     */
    public String createNotificationDestination(String accessToken, String environment,
                                                String name, String endpointUrl,
                                                String verificationToken) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("endpoint", endpointUrl);
        endpoint.put("verificationToken", verificationToken);
        endpoint.put("endpointDetails", Map.of("protocol", "HTTPS"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("status", "ENABLED");
        body.put("deliveryConfig", endpoint);

        String raw = call(oauth.apiHost(environment), "POST",
                "/commerce/notification/v1/destination", null, body, accessToken, null);
        try {
            return OBJECT_MAPPER.readTree(raw).path("destinationId").asText(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse createDestination response", e);
        }
    }

    public String createNotificationSubscription(String accessToken, String environment,
                                                 String topicId, String destinationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topicId",       topicId);
        body.put("destinationId", destinationId);
        body.put("status",        "ENABLED");

        String raw = call(oauth.apiHost(environment), "POST",
                "/commerce/notification/v1/subscription", null, body, accessToken, null);
        try {
            return OBJECT_MAPPER.readTree(raw).path("subscriptionId").asText(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse createSubscription response", e);
        }
    }

    public void deleteNotificationSubscription(String accessToken, String environment,
                                               String subscriptionId) {
        call(oauth.apiHost(environment), "DELETE",
                "/commerce/notification/v1/subscription/" + urlEncode(subscriptionId),
                null, null, accessToken, null);
    }

    public void deleteNotificationDestination(String accessToken, String environment,
                                              String destinationId) {
        call(oauth.apiHost(environment), "DELETE",
                "/commerce/notification/v1/destination/" + urlEncode(destinationId),
                null, null, accessToken, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private String call(String host, String method, String path, String query,
                        Object body, String accessToken, HttpHeaders extra) {
        WebClient client = webClientBuilder.baseUrl(host).build();
        String fullPath = query == null || query.isBlank() ? path : path + "?" + query;
        try {
            WebClient.RequestBodySpec req = (WebClient.RequestBodySpec) client
                    .method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(fullPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            if (extra != null) {
                for (Map.Entry<String, List<String>> h : extra.entrySet()) {
                    for (String v : h.getValue()) req = (WebClient.RequestBodySpec) req.header(h.getKey(), v);
                }
            }
            if (body != null) {
                req = (WebClient.RequestBodySpec) req
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body);
            }
            return req.retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException wcre) {
            log.error("eBay {} {} failed ({}): {}", method, fullPath,
                    wcre.getStatusCode(), wcre.getResponseBodyAsString());
            throw new RuntimeException("eBay API " + method + " " + path
                    + " failed: " + wcre.getStatusCode() + " " + wcre.getResponseBodyAsString(), wcre);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("eBay API call failed: " + e.getMessage(), e);
        }
    }

    private static HttpHeaders marketplaceHeader(String marketplaceId) {
        HttpHeaders headers = new HttpHeaders();
        if (marketplaceId != null && !marketplaceId.isBlank()) {
            headers.set("X-EBAY-C-MARKETPLACE-ID", marketplaceId);
        }
        return headers;
    }

    private static String contentLanguageFor(String marketplaceId) {
        if (marketplaceId == null) return "en-US";
        return switch (marketplaceId.toUpperCase()) {
            case "EBAY_GB" -> "en-GB";
            case "EBAY_DE" -> "de-DE";
            case "EBAY_FR" -> "fr-FR";
            case "EBAY_IT" -> "it-IT";
            case "EBAY_ES" -> "es-ES";
            case "EBAY_NL" -> "nl-NL";
            case "EBAY_BE" -> "nl-BE";
            case "EBAY_AU" -> "en-AU";
            case "EBAY_CA" -> "en-CA";
            default         -> "en-US";
        };
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractMap(Object raw, String key) {
        if (raw instanceof Map<?, ?> m) {
            Object v = m.get(key);
            if (v instanceof Map<?, ?> inner) return (Map<String, Object>) inner;
        }
        return null;
    }

    // Map eBay's inventory_item shape onto our DTO.
    @SuppressWarnings("unchecked")
    private static EbayProductDto inventoryItemToDto(JsonNode node, String marketplaceId) {
        EbayProductDto dto = EbayProductDto.builder()
                .sku(node.path("sku").asText(""))
                .marketplaceId(marketplaceId)
                .condition(node.path("condition").asText("NEW"))
                .conditionDescription(node.path("conditionDescription").asText(""))
                .build();

        JsonNode product = node.path("product");
        if (!product.isMissingNode()) {
            dto.setTitle(product.path("title").asText(""));
            dto.setBodyHtml(product.path("description").asText(""));
            dto.setSubtitle(product.path("subtitle").asText(""));
            dto.setBrand(product.path("brand").asText(""));
            dto.setMpn(product.path("mpn").asText(""));
            JsonNode upc = product.path("upc");
            if (upc.isArray() && upc.size() > 0) dto.setUpc(upc.get(0).asText(""));

            JsonNode images = product.path("imageUrls");
            List<String> imgs = new ArrayList<>();
            if (images.isArray()) for (JsonNode i : images) imgs.add(i.asText(""));
            dto.setImages(imgs);

            JsonNode aspects = product.path("aspects");
            if (aspects.isObject()) {
                try {
                    dto.setAspects(OBJECT_MAPPER.convertValue(aspects, Map.class));
                } catch (Exception ignored) { /* keep null */ }
            }
        }

        JsonNode avail = node.path("availability").path("shipToLocationAvailability").path("quantity");
        if (avail.isNumber()) dto.setInventoryQuantity(avail.asInt());

        return dto;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flattenInventoryItem(Map<String, Object> raw,
                                                             String sku,
                                                             String marketplaceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sku",            sku);
        out.put("marketplaceId",  marketplaceId);

        Map<String, Object> product = extractMap(raw, "product");
        if (product != null) {
            out.put("title",      Objects.toString(product.get("title"),       ""));
            out.put("bodyHtml",   Objects.toString(product.get("description"), ""));
            out.put("subtitle",   Objects.toString(product.get("subtitle"),    ""));
            out.put("brand",      Objects.toString(product.get("brand"),       ""));
            out.put("mpn",        Objects.toString(product.get("mpn"),         ""));
            Object upc = product.get("upc");
            if (upc instanceof List<?> upcs && !upcs.isEmpty()) out.put("upc", Objects.toString(upcs.get(0), ""));
            Object aspects = product.get("aspects");
            if (aspects instanceof Map<?, ?> m) out.put("aspects", m);
            Object images = product.get("imageUrls");
            if (images instanceof List<?> imgs) out.put("images", imgs);
        }
        if (!out.containsKey("images")) out.put("images", new ArrayList<>());
        if (!out.containsKey("aspects")) out.put("aspects", new LinkedHashMap<>());

        Map<String, Object> availability = extractMap(raw, "availability");
        if (availability != null) {
            Map<String, Object> ship = extractMap(availability, "shipToLocationAvailability");
            if (ship != null) {
                Object qty = ship.get("quantity");
                if (qty != null) out.put("inventoryQuantity", qty);
            }
        }

        out.put("condition",            Objects.toString(raw.get("condition"),            "NEW"));
        out.put("conditionDescription", Objects.toString(raw.get("conditionDescription"), ""));

        out.put("raw", raw);
        return out;
    }

    private static String urlEncode(String v) {
        if (v == null) return "";
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
