package net.ai.chatbot.service.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.amazon.AmazonDirectProductUpdateRequest;
import net.ai.chatbot.dto.amazon.AmazonProductDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Low-level client for Amazon's Selling Partner API (SP-API) v3.
 * <p>
 * Each call expects a fresh LWA access token from {@link LwaTokenService}
 * and the caller's SP-API region — SP-API itself no longer requires AWS
 * SigV4 signing (Amazon removed that requirement in 2023), so an access
 * token in the {@code x-amz-access-token} header is sufficient.
 * <p>
 * For listings the client leans on:
 * <ul>
 *   <li>Sellers API — to enumerate the seller's marketplaces.</li>
 *   <li>Reports API — GET_MERCHANT_LISTINGS_ALL_DATA, to bulk-sync the
 *       seller's existing catalog (Amazon has no "list my listings"
 *       endpoint; Reports is the sanctioned path).</li>
 *   <li>Listings Items API (2021-08-01) — CRUD for a single SKU.</li>
 *   <li>Notifications API (v1) — SQS-based push notifications for
 *       listings changes.</li>
 * </ul>
 */
@Service
@Slf4j
public class AmazonSpApiService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** How long to wait inline for a Reports API report before giving up. */
    private static final Duration REPORT_INLINE_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration REPORT_POLL_INTERVAL  = Duration.ofSeconds(3);

    private final WebClient.Builder webClientBuilder;

    public AmazonSpApiService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sellers API — marketplaces
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the list of marketplaces the seller has granted access to.
     * Each entry is a map with keys {@code id}, {@code name}, {@code countryCode},
     * {@code currencyCode}, {@code languageCode}, {@code domainName}.
     */
    public List<Map<String, Object>> getMarketplaceParticipations(String accessToken, String region) {
        String endpoint = regionEndpoint(region);
        String raw = call(endpoint, "GET", "/sellers/v1/marketplaceParticipations", null,
                null, accessToken, null);
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            JsonNode payload = node.path("payload");
            if (payload.isArray()) {
                for (JsonNode entry : payload) {
                    JsonNode m = entry.path("marketplace");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id",           m.path("id").asText(""));
                    row.put("name",         m.path("name").asText(""));
                    row.put("countryCode",  m.path("countryCode").asText(""));
                    row.put("currencyCode", m.path("defaultCurrencyCode").asText(""));
                    row.put("languageCode", m.path("defaultLanguageCode").asText(""));
                    row.put("domainName",   m.path("domainName").asText(""));
                    row.put("hasSellerSuspendedListings",
                            entry.path("participation").path("hasSellerSuspendedListings").asBoolean(false));
                    out.add(row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not parse marketplaceParticipations response", e);
        }
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Reports API — bulk seller listing sync (GET_MERCHANT_LISTINGS_ALL_DATA)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Kicks off a GET_MERCHANT_LISTINGS_ALL_DATA report and waits up to
     * {@link #REPORT_INLINE_TIMEOUT} for it to complete. Returns parsed
     * listings if the report finished in time, otherwise an empty list
     * (the caller can retry later — the report will still complete
     * asynchronously).
     */
    public List<AmazonProductDto> listSellerListings(String accessToken, String region,
                                                     String marketplaceId, int limit) {
        String endpoint = regionEndpoint(region);

        // 1. Create the report request
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("reportType", "GET_MERCHANT_LISTINGS_ALL_DATA");
        createBody.put("marketplaceIds", List.of(marketplaceId));

        String createRaw = call(endpoint, "POST", "/reports/2021-06-30/reports",
                null, createBody, accessToken, null);
        String reportId;
        try {
            reportId = OBJECT_MAPPER.readTree(createRaw).path("reportId").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create merchant-listings report: " + e.getMessage());
        }
        if (reportId.isBlank()) {
            throw new RuntimeException("Amazon did not return a reportId");
        }
        log.info("Amazon: created listings report {} for marketplace {}", reportId, marketplaceId);

        // 2. Poll until DONE / FATAL / CANCELLED or timeout
        Instant deadline = Instant.now().plus(REPORT_INLINE_TIMEOUT);
        String status = "IN_PROGRESS";
        String reportDocumentId = null;
        while (Instant.now().isBefore(deadline)) {
            try { Thread.sleep(REPORT_POLL_INTERVAL.toMillis()); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }

            String pollRaw = call(endpoint, "GET",
                    "/reports/2021-06-30/reports/" + reportId, null, null, accessToken, null);
            try {
                JsonNode node = OBJECT_MAPPER.readTree(pollRaw);
                status = node.path("processingStatus").asText("");
                reportDocumentId = node.path("reportDocumentId").asText(null);
            } catch (Exception e) {
                log.warn("Amazon: could not parse report poll response: {}", e.getMessage());
            }
            if ("DONE".equals(status) || "CANCELLED".equals(status) || "FATAL".equals(status)) break;
        }
        if (!"DONE".equals(status) || reportDocumentId == null || reportDocumentId.isBlank()) {
            log.warn("Amazon: report {} not ready in time (status={}) — returning empty list",
                    reportId, status);
            return new ArrayList<>();
        }

        // 3. Fetch the report document metadata (pre-signed URL + encryption)
        String docMetaRaw = call(endpoint, "GET",
                "/reports/2021-06-30/documents/" + reportDocumentId, null, null, accessToken, null);
        String documentUrl;
        String compressionAlgorithm;
        try {
            JsonNode node = OBJECT_MAPPER.readTree(docMetaRaw);
            documentUrl = node.path("url").asText("");
            compressionAlgorithm = node.path("compressionAlgorithm").asText("");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse report document metadata: " + e.getMessage());
        }
        if (documentUrl.isBlank()) {
            throw new RuntimeException("Report document missing download URL");
        }

        // 4. Download + parse the TSV
        byte[] raw = webClientBuilder.build()
                .get()
                .uri(documentUrl)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
        if (raw == null) return new ArrayList<>();

        String tsv;
        try {
            if ("GZIP".equalsIgnoreCase(compressionAlgorithm)) {
                try (InputStream gzipped = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                    tsv = new String(gzipped.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                tsv = new String(raw, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to decompress Amazon report document", e);
        }

        return parseMerchantListingsTsv(tsv, marketplaceId, limit);
    }

    /**
     * Minimal TSV parser for the GET_MERCHANT_LISTINGS_ALL_DATA report.
     * Only the fields we care about (item-name, asin1, seller-sku,
     * item-description, open-date, price, quantity, product-id,
     * listing-id) are extracted; other columns are ignored.
     */
    private List<AmazonProductDto> parseMerchantListingsTsv(String tsv, String marketplaceId, int limit) {
        List<AmazonProductDto> out = new ArrayList<>();
        if (tsv == null || tsv.isBlank()) return out;

        String[] lines = tsv.split("\\r?\\n");
        if (lines.length < 2) return out;

        String[] header = lines[0].split("\t", -1);
        int idxName   = indexOf(header, "item-name");
        int idxAsin   = indexOf(header, "asin1");
        int idxSku    = indexOf(header, "seller-sku");
        int idxDesc   = indexOf(header, "item-description");
        int idxPrice  = indexOf(header, "price");
        int idxQty    = indexOf(header, "quantity");
        int idxStatus = indexOf(header, "status");

        int capped = limit <= 0 ? Integer.MAX_VALUE : limit;
        for (int i = 1; i < lines.length && out.size() < capped; i++) {
            String line = lines[i];
            if (line == null || line.isEmpty()) continue;
            String[] cols = line.split("\t", -1);
            if (cols.length < header.length) continue;

            AmazonProductDto dto = AmazonProductDto.builder()
                    .marketplaceId(marketplaceId)
                    .asin(safeGet(cols, idxAsin))
                    .sellerSku(safeGet(cols, idxSku))
                    .title(safeGet(cols, idxName))
                    .bodyHtml(safeGet(cols, idxDesc))
                    .price(safeGet(cols, idxPrice))
                    .inventoryQuantity(parseIntOrNull(safeGet(cols, idxQty)))
                    .status("Active".equalsIgnoreCase(safeGet(cols, idxStatus))
                            ? "ACTIVE" : "INACTIVE")
                    .condition("new_new")
                    .build();
            out.add(dto);
        }
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Listings Items API — single-SKU CRUD
    // ──────────────────────────────────────────────────────────────────────────

    /** Fetch a single listing for the seller's SKU. Returns the raw SP-API JSON as a map. */
    public Map<String, Object> getListing(String accessToken, String region, String sellerId,
                                          String sku, String marketplaceId) {
        String endpoint = regionEndpoint(region);
        String query = "marketplaceIds=" + urlEncode(marketplaceId)
                + "&includedData=attributes,issues,offers,summaries";
        String raw = call(endpoint, "GET",
                "/listings/2021-08-01/items/" + urlEncode(sellerId) + "/" + urlEncode(sku),
                query, null, accessToken, null);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(raw, Map.class);
            return flattenListing(map, sku, marketplaceId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse listing response: " + e.getMessage());
        }
    }

    /**
     * Create or replace a listing. Caller provides the ProductType and a
     * fully-formed attributes payload. Returns the ASIN when Amazon issues
     * one, otherwise null.
     */
    public String createOrReplaceListing(String accessToken, String region, String sellerId,
                                         String sku, String marketplaceId,
                                         String productType, Map<String, Object> attributes) {
        if (productType == null || productType.isBlank()) {
            throw new RuntimeException("productType is required to publish an Amazon listing");
        }
        String endpoint = regionEndpoint(region);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productType",  productType);
        body.put("requirements", "LISTING");
        body.put("attributes",   attributes == null ? new LinkedHashMap<>() : attributes);

        String raw = call(endpoint, "PUT",
                "/listings/2021-08-01/items/" + urlEncode(sellerId) + "/" + urlEncode(sku),
                "marketplaceIds=" + urlEncode(marketplaceId),
                body, accessToken, null);

        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            return node.path("submissionId").asText(null);
        } catch (Exception e) {
            log.warn("Could not parse createOrReplaceListing response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Apply a JSON-Patch style update to a listing. Each non-null
     * field in {@code req} becomes a {@code replace} patch on the
     * corresponding SP-API attribute path.
     */
    public void patchListing(String accessToken, String region, String sellerId,
                             String sku, String marketplaceId, String productType,
                             AmazonDirectProductUpdateRequest req) {
        if (productType == null || productType.isBlank()) {
            throw new RuntimeException("productType is required to update an Amazon listing");
        }
        String endpoint = regionEndpoint(region);

        List<Map<String, Object>> patches = new ArrayList<>();
        if (req.getTitle() != null)           patches.add(patch("item_name",                  attrValue(req.getTitle(),          marketplaceId)));
        if (req.getBodyHtml() != null)        patches.add(patch("product_description",        attrValue(req.getBodyHtml(),       marketplaceId)));
        if (req.getBrand() != null)           patches.add(patch("brand",                      attrValue(req.getBrand(),          marketplaceId)));
        if (req.getCategory() != null)        patches.add(patch("product_category",           attrValue(req.getCategory(),       marketplaceId)));
        if (req.getSearchKeywords() != null)  patches.add(patch("generic_keyword",            attrValue(req.getSearchKeywords(), marketplaceId)));
        if (req.getCondition() != null)       patches.add(patch("condition_type",             attrValue(req.getCondition(),      marketplaceId)));
        if (req.getBulletPoints() != null && !req.getBulletPoints().isEmpty()) {
            patches.add(patch("bullet_point", bulletValues(req.getBulletPoints(), marketplaceId)));
        }
        if (req.getPrice() != null)           patches.add(patch("list_price", priceValue(req.getPrice(), marketplaceId)));
        if (req.getInventoryQuantity() != null) patches.add(patch("fulfillment_availability",
                fulfillmentValue(req.getInventoryQuantity())));
        // Amazon stores images across a main slot + up to 8 "other" slots.
        // When a caller sends an {@code images} array we write them back in
        // display order so the first URL becomes the main image.
        if (req.getImages() != null) {
            patches.addAll(imagePatches(req.getImages(), marketplaceId));
        }

        if (patches.isEmpty()) return;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productType", productType);
        body.put("patches",     patches);

        call(endpoint, "PATCH",
                "/listings/2021-08-01/items/" + urlEncode(sellerId) + "/" + urlEncode(sku),
                "marketplaceIds=" + urlEncode(marketplaceId),
                body, accessToken, null);
    }

    public void deleteListing(String accessToken, String region, String sellerId,
                              String sku, String marketplaceId) {
        String endpoint = regionEndpoint(region);
        call(endpoint, "DELETE",
                "/listings/2021-08-01/items/" + urlEncode(sellerId) + "/" + urlEncode(sku),
                "marketplaceIds=" + urlEncode(marketplaceId),
                null, accessToken, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notifications API — SQS-based push notifications
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Register (or look up) an SQS destination that SP-API can write
     * notifications to. The caller must grant SP-API permission to write
     * to the queue (SP-API publishes a well-known principal for this).
     */
    public String createSqsDestination(String accessToken, String region,
                                       String name, String sqsArn) {
        String endpoint = regionEndpoint(region);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        Map<String, Object> resource = new LinkedHashMap<>();
        Map<String, Object> sqs = new LinkedHashMap<>();
        sqs.put("arn", sqsArn);
        resource.put("sqs", sqs);
        body.put("resourceSpecification", resource);

        String raw = call(endpoint, "POST", "/notifications/v1/destinations",
                null, body, accessToken, null);
        try {
            return OBJECT_MAPPER.readTree(raw).path("payload").path("destinationId").asText(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse createDestination response", e);
        }
    }

    public String createSubscription(String accessToken, String region,
                                     String notificationType, String destinationId) {
        String endpoint = regionEndpoint(region);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payloadVersion", "1.0");
        body.put("destinationId",  destinationId);
        String raw = call(endpoint, "POST",
                "/notifications/v1/subscriptions/" + urlEncode(notificationType),
                null, body, accessToken, null);
        try {
            return OBJECT_MAPPER.readTree(raw).path("payload").path("subscriptionId").asText(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse createSubscription response", e);
        }
    }

    public void deleteSubscription(String accessToken, String region,
                                   String notificationType, String subscriptionId) {
        String endpoint = regionEndpoint(region);
        call(endpoint, "DELETE",
                "/notifications/v1/subscriptions/" + urlEncode(notificationType)
                        + "/" + urlEncode(subscriptionId),
                null, null, accessToken, null);
    }

    public void deleteDestination(String accessToken, String region, String destinationId) {
        String endpoint = regionEndpoint(region);
        call(endpoint, "DELETE",
                "/notifications/v1/destinations/" + urlEncode(destinationId),
                null, null, accessToken, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Region endpoint override for SP-API. */
    public static String regionEndpoint(String region) {
        if (region == null) region = "NA";
        return switch (region.toUpperCase(Locale.ROOT)) {
            case "EU" -> "https://sellingpartnerapi-eu.amazon.com";
            case "FE" -> "https://sellingpartnerapi-fe.amazon.com";
            default   -> "https://sellingpartnerapi-na.amazon.com";
        };
    }

    /** LWA authorize host for a given region (used to build the consent URL). */
    public static String lwaAuthorizeHost(String region) {
        if (region == null) region = "NA";
        return switch (region.toUpperCase(Locale.ROOT)) {
            case "EU" -> "https://sellercentral-europe.amazon.com";
            case "FE" -> "https://sellercentral.amazon.co.jp";
            default   -> "https://sellercentral.amazon.com";
        };
    }

    /**
     * Central SP-API call wrapper. Uses WebClient with the configured
     * LWA access token header and returns the raw response body. A retry
     * on 401 is left to the caller (via {@link LwaTokenService#invalidate}).
     */
    private String call(String endpoint, String method, String path, String query,
                        Object body, String accessToken, HttpHeaders extra) {
        WebClient client = webClientBuilder.baseUrl(endpoint).build();
        String fullPath = query == null || query.isBlank() ? path : path + "?" + query;
        try {
            WebClient.RequestBodySpec req = (WebClient.RequestBodySpec) client
                    .method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(fullPath)
                    .header("x-amz-access-token", accessToken)
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
            log.error("SP-API {} {} failed ({}): {}", method, fullPath,
                    wcre.getStatusCode(), wcre.getResponseBodyAsString());
            throw new RuntimeException("Amazon SP-API " + method + " " + path
                    + " failed: " + wcre.getStatusCode() + " " + wcre.getResponseBodyAsString(), wcre);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Amazon SP-API call failed: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> patch(String attribute, Object value) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("op",    "replace");
        p.put("path",  "/attributes/" + attribute);
        p.put("value", value);
        return p;
    }

    private static List<Map<String, Object>> attrValue(String value, String marketplaceId) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("value",         value == null ? "" : value);
        v.put("marketplace_id", marketplaceId);
        return List.of(v);
    }

    private static List<Map<String, Object>> bulletValues(List<String> bullets, String marketplaceId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String b : bullets) {
            if (b == null) continue;
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("value",         b);
            v.put("marketplace_id", marketplaceId);
            out.add(v);
        }
        return out;
    }

    private static List<Map<String, Object>> priceValue(String price, String marketplaceId) {
        Map<String, Object> v = new LinkedHashMap<>();
        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("currency", "USD");
        amount.put("value",    price == null ? "0" : price);
        v.put("value",          amount);
        v.put("marketplace_id", marketplaceId);
        return List.of(v);
    }

    private static List<Map<String, Object>> fulfillmentValue(Integer quantity) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("fulfillment_channel_code", "DEFAULT");
        v.put("quantity",                 quantity == null ? 0 : quantity);
        return List.of(v);
    }

    /**
     * Build a list of patches that wholesale-replace the image slots of a
     * listing. The first URL becomes {@code main_product_image_locator}, the
     * next up to eight go into {@code other_product_image_locator_1..8}.
     * Empty entries are intentionally written so removed images actually
     * disappear from the live listing.
     */
    private static List<Map<String, Object>> imagePatches(List<String> images, String marketplaceId) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> urls = images == null ? List.of() : images;

        String main = urls.isEmpty() ? "" : urls.get(0);
        out.add(patch("main_product_image_locator", imageSlotValue(main, marketplaceId)));
        for (int i = 1; i <= 8; i++) {
            String url = i < urls.size() ? urls.get(i) : "";
            out.add(patch("other_product_image_locator_" + i, imageSlotValue(url, marketplaceId)));
        }
        return out;
    }

    private static List<Map<String, Object>> imageSlotValue(String url, String marketplaceId) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("media_location",  url == null ? "" : url);
        v.put("marketplace_id",  marketplaceId);
        return List.of(v);
    }

    /**
     * Flatten the verbose SP-API listing response into a tidy map the
     * controller can return unchanged. Populates both native keys
     * ({@code asin}, {@code sellerSku}) and Shopify-ish aliases
     * ({@code title}, {@code bodyHtml}) so the frontend can render it
     * without a second translation layer.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> flattenListing(Map<String, Object> raw, String sku, String marketplaceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sellerSku",     sku);
        out.put("sku",           sku);
        out.put("marketplaceId", marketplaceId);

        Object summariesObj = raw.get("summaries");
        String title = "";
        String asin = "";
        String status = "ACTIVE";
        String productType = "";
        if (summariesObj instanceof List<?> summaries && !summaries.isEmpty()) {
            Object first = summaries.get(0);
            if (first instanceof Map<?, ?> m) {
                title       = Objects.toString(((Map<String, Object>) m).get("itemName"),   "");
                asin        = Objects.toString(((Map<String, Object>) m).get("asin"),       "");
                status      = Objects.toString(((Map<String, Object>) m).get("status"),     "ACTIVE");
                productType = Objects.toString(((Map<String, Object>) m).get("productType"),"");
            }
        }
        out.put("asin",         asin);
        out.put("title",        title);
        out.put("status",       status);
        out.put("productType",  productType);

        Object attributesObj = raw.get("attributes");
        if (attributesObj instanceof Map<?, ?> attrs) {
            out.put("bodyHtml",       firstAttrValue((Map<String, Object>) attrs, "product_description"));
            out.put("brand",          firstAttrValue((Map<String, Object>) attrs, "brand"));
            out.put("category",       firstAttrValue((Map<String, Object>) attrs, "product_category"));
            out.put("searchKeywords", firstAttrValue((Map<String, Object>) attrs, "generic_keyword"));
            out.put("condition",      firstAttrValue((Map<String, Object>) attrs, "condition_type"));
            out.put("bulletPoints",   allAttrValues((Map<String, Object>) attrs, "bullet_point"));
            out.put("price",          firstPriceValue((Map<String, Object>) attrs, "list_price"));
            out.put("images",         imageUrls((Map<String, Object>) attrs));
        }
        if (!out.containsKey("bodyHtml")) out.put("bodyHtml", "");
        if (!out.containsKey("brand"))    out.put("brand", "");
        if (!out.containsKey("bulletPoints")) out.put("bulletPoints", new ArrayList<>());
        if (!out.containsKey("images"))       out.put("images", new ArrayList<>());

        // Preserve original payload for the UI if it wants to show raw offers/issues
        out.put("raw", raw);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String firstAttrValue(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        if (v instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> m) {
                Object val = ((Map<String, Object>) m).get("value");
                if (val != null) return val.toString();
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static List<String> allAttrValues(Map<String, Object> attrs, String key) {
        List<String> out = new ArrayList<>();
        Object v = attrs.get(key);
        if (v instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object val = ((Map<String, Object>) m).get("value");
                    if (val != null) out.add(val.toString());
                }
            }
        }
        return out;
    }

    /**
     * Collect every image URL on the listing in display order. The main
     * image comes first, followed by {@code other_product_image_locator_1..8}.
     * Empty slots are skipped so the list is contiguous.
     */
    private static List<String> imageUrls(Map<String, Object> attrs) {
        List<String> out = new ArrayList<>();
        String main = imageSlotUrl(attrs, "main_product_image_locator");
        if (!main.isEmpty()) out.add(main);
        for (int i = 1; i <= 8; i++) {
            String url = imageSlotUrl(attrs, "other_product_image_locator_" + i);
            if (!url.isEmpty()) out.add(url);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String imageSlotUrl(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
            Object loc = ((Map<String, Object>) m).get("media_location");
            if (loc != null) return loc.toString();
            Object val = ((Map<String, Object>) m).get("value");
            if (val instanceof Map<?, ?> valMap) {
                Object inner = ((Map<String, Object>) valMap).get("media_location");
                if (inner != null) return inner.toString();
            } else if (val != null) {
                return val.toString();
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String firstPriceValue(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
            Object val = ((Map<String, Object>) m).get("value");
            if (val instanceof Map<?, ?> priceMap) {
                Object amount = ((Map<String, Object>) priceMap).get("value");
                if (amount != null) return amount.toString();
            } else if (val != null) {
                return val.toString();
            }
        }
        return "";
    }

    private static int indexOf(String[] arr, String key) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != null && arr[i].equalsIgnoreCase(key)) return i;
        }
        return -1;
    }

    private static String safeGet(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) return "";
        String v = cols[idx];
        return v == null ? "" : v;
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String urlEncode(String v) {
        if (v == null) return "";
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
