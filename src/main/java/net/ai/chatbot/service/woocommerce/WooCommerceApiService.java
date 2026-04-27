package net.ai.chatbot.service.woocommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.shopify.DirectProductUpdateRequest;
import net.ai.chatbot.dto.shopify.ShopifyProductDto;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WooCommerce REST API v3 client.
 * <p>
 * Mirrors {@link net.ai.chatbot.service.shopify.ShopifyApiService} so the
 * frontend can target either platform with the same {@link ShopifyProductDto}
 * shape. Authentication is HTTP Basic with the user's {@code consumer_key}
 * and {@code consumer_secret}.
 * <p>
 * Images in WooCommerce live on the product resource itself (not a separate
 * REST endpoint), so add/delete image are implemented as read-modify-write
 * PUTs against {@code /products/{id}}.
 */
@Service
@Slf4j
public class WooCommerceApiService {

    private static final String API_BASE = "/wp-json/wc/v3";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient.Builder webClientBuilder;

    public WooCommerceApiService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // ── Connection test ───────────────────────────────────────────────────────

    /**
     * Probe /products with ?per_page=1 and return the site's system-status
     * title — good enough to verify the credentials work and to populate a
     * display name for the integration row.
     */
    public String testConnection(String storeUrl, String consumerKey, String consumerSecret) {
        String url = base(storeUrl) + "/system_status";
        try {
            String response = get(url, consumerKey, consumerSecret);
            JsonNode root = OBJECT_MAPPER.readTree(response);
            String name = root.path("settings").path("title").asText("");
            if (name.isBlank()) name = root.path("environment").path("site_url").asText(storeUrl);
            return name.isBlank() ? "WooCommerce Store" : name;
        } catch (Exception e) {
            // Fall back to a cheaper endpoint if system_status is restricted.
            try {
                get(base(storeUrl) + "/products?per_page=1", consumerKey, consumerSecret);
                return "WooCommerce Store";
            } catch (Exception ex) {
                throw new RuntimeException("WooCommerce connection test failed: " + ex.getMessage());
            }
        }
    }

    // ── List products ─────────────────────────────────────────────────────────

    public List<ShopifyProductDto> listProducts(String storeUrl, String consumerKey,
                                                String consumerSecret, int limit) {
        int perPage = Math.min(Math.max(limit, 1), 100);
        String url = base(storeUrl) + "/products?per_page=" + perPage
                + "&_fields=id,name,description,short_description,status,slug,"
                + "categories,tags,meta_data,sku,price,regular_price,sale_price,"
                + "stock_quantity";

        String response = get(url, consumerKey, consumerSecret);
        List<ShopifyProductDto> products = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            for (JsonNode p : root) {
                products.add(jsonToDto(p));
            }
        } catch (Exception e) {
            log.error("Failed to parse WooCommerce products", e);
            throw new RuntimeException("Failed to parse WooCommerce products");
        }
        return products;
    }

    // ── Full product detail ──────────────────────────────────────────────────

    public Map<String, Object> getFullProduct(String storeUrl, String consumerKey,
                                              String consumerSecret, String productId) {
        String url = base(storeUrl) + "/products/" + productId;
        String response = get(url, consumerKey, consumerSecret);
        try {
            JsonNode p = OBJECT_MAPPER.readTree(response);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id",             p.path("id").asText());
            result.put("wooId",          p.path("id").asText());
            result.put("title",          p.path("name").asText());
            result.put("bodyHtml",       p.path("description").asText());
            // WC has no direct "vendor" field — many stores use a "brand" taxonomy
            // or the store title; leave empty so the editor treats it as unset.
            result.put("vendor",         "");
            result.put("productType",    firstCategoryName(p));
            result.put("tags",           joinTags(p));
            result.put("handle",         p.path("slug").asText());
            // Map WC status → the Shopify-style vocabulary the UI expects.
            result.put("status",         mapWooStatusToUi(p.path("status").asText()));
            result.put("publishedScope", "web");
            result.put("templateSuffix", "");
            result.put("createdAt",      p.path("date_created").asText());
            result.put("updatedAt",      p.path("date_modified").asText());
            result.put("publishedAt",    p.path("date_created").asText());

            // Default-variant-like fields on the product object
            String price          = p.path("regular_price").asText();
            String salePrice      = p.path("sale_price").asText();
            List<Map<String, Object>> variants = new ArrayList<>();
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("id",                p.path("id").asText());
            variant.put("title",             "Default");
            variant.put("price",             salePrice.isBlank() ? price : salePrice);
            variant.put("compareAtPrice",    salePrice.isBlank() ? "" : price);
            variant.put("sku",               p.path("sku").asText());
            variant.put("barcode",           "");
            variant.put("weight",            p.path("weight").asText().isBlank()
                    ? 0.0 : Double.parseDouble(p.path("weight").asText("0")));
            variant.put("weightUnit",        "");
            variant.put("inventoryQuantity", p.path("stock_quantity").isNull()
                    ? 0 : p.path("stock_quantity").asInt());
            variant.put("taxable",           p.path("tax_status").asText("taxable").equals("taxable"));
            variant.put("requiresShipping",  !p.path("virtual").asBoolean(false));
            variant.put("position",          1);
            variant.put("option1",           "");
            variant.put("option2",           "");
            variant.put("option3",           "");
            variants.add(variant);
            result.put("variants", variants);

            result.put("options",  new ArrayList<>());

            // Images
            List<Map<String, Object>> images = new ArrayList<>();
            for (JsonNode img : p.path("images")) {
                Map<String, Object> image = new LinkedHashMap<>();
                image.put("id",       img.path("id").asText());
                image.put("src",      img.path("src").asText());
                image.put("alt",      img.path("alt").asText(""));
                image.put("position", img.path("position").asInt());
                images.add(image);
            }
            result.put("images", images);

            // SEO from Yoast / Rank Math meta_data
            String[] seo = readSeoFromMeta(p);
            result.put("seoTitle",       seo[0]);
            result.put("seoDescription", seo[1]);

            return result;
        } catch (Exception e) {
            log.error("Failed to parse full WooCommerce product {}", productId, e);
            throw new RuntimeException("Failed to fetch product details: " + e.getMessage());
        }
    }

    // ── Update (enhanced fields) ─────────────────────────────────────────────

    public void updateProduct(String storeUrl, String consumerKey, String consumerSecret,
                              ShopifyProductDto enhanced) {
        String wooId = enhanced.getWooId() != null ? enhanced.getWooId() : enhanced.getShopifyId();
        String url = base(storeUrl) + "/products/" + wooId;

        Map<String, Object> product = new LinkedHashMap<>();
        if (notBlank(enhanced.getEnhancedTitle()))       product.put("name",              enhanced.getEnhancedTitle());
        if (notBlank(enhanced.getEnhancedBodyHtml()))    product.put("description",       enhanced.getEnhancedBodyHtml());
        if (notBlank(enhanced.getEnhancedTags()))        product.put("tags",              tagsFromCsv(enhanced.getEnhancedTags()));
        if (notBlank(enhanced.getEnhancedProductType())) product.put("categories",        categoriesFromCsv(enhanced.getEnhancedProductType()));
        if (notBlank(enhanced.getEnhancedHandle()))      product.put("slug",              enhanced.getEnhancedHandle());

        List<Map<String, Object>> meta = buildSeoMeta(
                enhanced.getEnhancedSeoTitle(), enhanced.getEnhancedSeoDescription());
        if (!meta.isEmpty()) product.put("meta_data", meta);

        try {
            String body = OBJECT_MAPPER.writeValueAsString(product);
            put(url, consumerKey, consumerSecret, body);
            log.info("Updated WooCommerce product {}", wooId);
        } catch (Exception e) {
            log.error("Failed to update WooCommerce product {}", wooId, e);
            throw new RuntimeException("Failed to update product " + wooId);
        }
    }

    // ── Direct update (user edits) ───────────────────────────────────────────

    public void updateProductDirect(String storeUrl, String consumerKey, String consumerSecret,
                                    String wooId, DirectProductUpdateRequest req) {
        String url = base(storeUrl) + "/products/" + wooId;
        Map<String, Object> product = new LinkedHashMap<>();

        if (notBlank(req.getTitle()))       product.put("name",        req.getTitle());
        if (notBlank(req.getBodyHtml()))    product.put("description", req.getBodyHtml());
        if (notBlank(req.getTags()))        product.put("tags",        tagsFromCsv(req.getTags()));
        if (notBlank(req.getProductType())) product.put("categories",  categoriesFromCsv(req.getProductType()));
        if (notBlank(req.getHandle()))      product.put("slug",        req.getHandle());
        if (notBlank(req.getStatus()))      product.put("status",      mapUiStatusToWoo(req.getStatus()));

        // Pricing / SKU / inventory live on the product itself (simple products)
        if (notBlank(req.getPrice()))          product.put("regular_price", req.getPrice());
        if (notBlank(req.getCompareAtPrice())) {
            // Compare-at in Shopify = original price; in Woo, sale_price is the
            // discounted price. Convention here: price is the sale, compareAt
            // is the "was" price. So if both are present, regular = compareAt
            // and sale = price.
            product.put("regular_price", req.getCompareAtPrice());
            if (notBlank(req.getPrice())) product.put("sale_price", req.getPrice());
        }
        if (notBlank(req.getSku()))            product.put("sku",            req.getSku());
        if (req.getInventoryQuantity() != null) {
            product.put("manage_stock",   true);
            product.put("stock_quantity", req.getInventoryQuantity());
        }

        List<Map<String, Object>> meta = buildSeoMeta(req.getSeoTitle(), req.getSeoDescription());
        if (!meta.isEmpty()) product.put("meta_data", meta);

        try {
            String body = OBJECT_MAPPER.writeValueAsString(product);
            put(url, consumerKey, consumerSecret, body);
            log.info("Directly updated WooCommerce product {}", wooId);
        } catch (Exception e) {
            log.error("Failed to directly update WooCommerce product {}", wooId, e);
            throw new RuntimeException("Failed to update product: " + e.getMessage());
        }
    }

    // ── Create product (publish drafts) ──────────────────────────────────────

    public String createProduct(String storeUrl, String consumerKey, String consumerSecret,
                                ShopifyProductDto draft) {
        String url = base(storeUrl) + "/products";

        Map<String, Object> product = new LinkedHashMap<>();
        if (notBlank(draft.getTitle()))       product.put("name",        draft.getTitle());
        if (notBlank(draft.getBodyHtml()))    product.put("description", draft.getBodyHtml());
        if (notBlank(draft.getHandle()))      product.put("slug",        draft.getHandle());
        product.put("type",   "simple");
        product.put("status", mapUiStatusToWoo(firstNonBlank(draft.getStatus(), "active")));

        if (notBlank(draft.getTags()))        product.put("tags",       tagsFromCsv(draft.getTags()));
        if (notBlank(draft.getProductType())) product.put("categories", categoriesFromCsv(draft.getProductType()));

        if (notBlank(draft.getCompareAtPrice())) {
            product.put("regular_price", draft.getCompareAtPrice());
            if (notBlank(draft.getPrice())) product.put("sale_price", draft.getPrice());
        } else if (notBlank(draft.getPrice())) {
            product.put("regular_price", draft.getPrice());
        }
        if (notBlank(draft.getSku())) product.put("sku", draft.getSku());
        if (draft.getInventoryQuantity() != null) {
            product.put("manage_stock",   true);
            product.put("stock_quantity", draft.getInventoryQuantity());
        }

        // Images: WC fetches the src itself, same as Shopify.
        if (draft.getImages() != null && !draft.getImages().isEmpty()) {
            List<Map<String, Object>> images = new ArrayList<>();
            int position = 0;
            for (String src : draft.getImages()) {
                if (src == null || src.isBlank()) continue;
                Map<String, Object> img = new LinkedHashMap<>();
                img.put("src",      src);
                img.put("position", position++);
                if (notBlank(draft.getTitle())) img.put("alt", draft.getTitle());
                images.add(img);
            }
            if (!images.isEmpty()) product.put("images", images);
        }

        List<Map<String, Object>> meta = buildSeoMeta(draft.getSeoTitle(), draft.getSeoDescription());
        if (!meta.isEmpty()) product.put("meta_data", meta);

        if (!product.containsKey("name")) {
            throw new RuntimeException("Cannot publish draft: title is required");
        }

        try {
            String body = OBJECT_MAPPER.writeValueAsString(product);
            String response = post(url, consumerKey, consumerSecret, body);
            String newId = OBJECT_MAPPER.readTree(response).path("id").asText();
            if (newId == null || newId.isBlank()) {
                throw new RuntimeException("WooCommerce returned no product id");
            }
            log.info("Created WooCommerce product {} ({})", newId, draft.getTitle());
            return newId;
        } catch (Exception e) {
            log.error("Failed to create WooCommerce product '{}': {}", draft.getTitle(), e.getMessage());
            throw new RuntimeException("Failed to create WooCommerce product: " + e.getMessage());
        }
    }

    // ── Product images (read-modify-write) ───────────────────────────────────

    public Map<String, Object> addProductImage(String storeUrl, String consumerKey,
                                               String consumerSecret, String wooId,
                                               String src, String alt, Integer position) {
        if (wooId == null || wooId.isBlank())
            throw new IllegalArgumentException("wooId is required");
        if (src == null || src.isBlank())
            throw new IllegalArgumentException("Image src URL is required");

        String url = base(storeUrl) + "/products/" + wooId;
        try {
            String current = get(url, consumerKey, consumerSecret);
            JsonNode p = OBJECT_MAPPER.readTree(current);

            List<Map<String, Object>> images = new ArrayList<>();
            for (JsonNode img : p.path("images")) {
                Map<String, Object> existing = new LinkedHashMap<>();
                existing.put("id",       img.path("id").asInt());
                existing.put("position", img.path("position").asInt());
                images.add(existing);
            }

            Map<String, Object> newImage = new LinkedHashMap<>();
            newImage.put("src",      src);
            newImage.put("position", position != null && position > 0 ? position : images.size());
            if (notBlank(alt)) newImage.put("alt", alt);
            images.add(newImage);

            String body = OBJECT_MAPPER.writeValueAsString(Map.of("images", images));
            String response = put(url, consumerKey, consumerSecret, body);

            JsonNode updated = OBJECT_MAPPER.readTree(response);
            JsonNode last = updated.path("images").get(updated.path("images").size() - 1);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id",       last.path("id").asText());
            result.put("src",      last.path("src").asText());
            result.put("alt",      last.path("alt").asText(""));
            result.put("position", last.path("position").asInt());
            log.info("Attached image to WooCommerce product {}", wooId);
            return result;
        } catch (Exception e) {
            log.error("Failed to attach image to WooCommerce product {}: {}", wooId, e.getMessage());
            throw new RuntimeException("Failed to attach image: " + e.getMessage());
        }
    }

    public void deleteProductImage(String storeUrl, String consumerKey, String consumerSecret,
                                   String wooId, String imageId) {
        if (wooId == null || wooId.isBlank() || imageId == null || imageId.isBlank())
            throw new IllegalArgumentException("wooId and imageId are required");

        String url = base(storeUrl) + "/products/" + wooId;
        try {
            String current = get(url, consumerKey, consumerSecret);
            JsonNode p = OBJECT_MAPPER.readTree(current);

            List<Map<String, Object>> remaining = new ArrayList<>();
            for (JsonNode img : p.path("images")) {
                if (!imageId.equals(img.path("id").asText())) {
                    Map<String, Object> keep = new LinkedHashMap<>();
                    keep.put("id",       img.path("id").asInt());
                    keep.put("position", img.path("position").asInt());
                    remaining.add(keep);
                }
            }

            String body = OBJECT_MAPPER.writeValueAsString(Map.of("images", remaining));
            put(url, consumerKey, consumerSecret, body);
            log.info("Deleted image {} from WooCommerce product {}", imageId, wooId);
        } catch (Exception e) {
            log.error("Failed to delete image {} from WooCommerce product {}: {}",
                    imageId, wooId, e.getMessage());
            throw new RuntimeException("Failed to delete image: " + e.getMessage());
        }
    }

    // ── Webhooks ─────────────────────────────────────────────────────────────

    public String registerProductCreatedWebhook(String storeUrl, String consumerKey,
                                                String consumerSecret, String callbackUrl) {
        String url = base(storeUrl) + "/webhooks";
        try {
            Map<String, Object> webhook = new HashMap<>();
            webhook.put("name",          "Product created — AI auto-enhance");
            webhook.put("topic",         "product.created");
            webhook.put("delivery_url",  callbackUrl);
            webhook.put("status",        "active");

            String body = OBJECT_MAPPER.writeValueAsString(webhook);
            String response = post(url, consumerKey, consumerSecret, body);
            String webhookId = OBJECT_MAPPER.readTree(response).path("id").asText();
            log.info("Registered WooCommerce webhook {} for {}", webhookId, storeUrl);
            return webhookId;
        } catch (Exception e) {
            log.error("Failed to register WooCommerce webhook", e);
            throw new RuntimeException("Failed to register webhook");
        }
    }

    public void deleteWebhook(String storeUrl, String consumerKey, String consumerSecret,
                              String webhookId) {
        String url = base(storeUrl) + "/webhooks/" + webhookId + "?force=true";
        try {
            webClientBuilder.build()
                    .delete().uri(url)
                    .header("Authorization", basicAuth(consumerKey, consumerSecret))
                    .retrieve().bodyToMono(String.class).block();
            log.info("Deleted WooCommerce webhook {}", webhookId);
        } catch (Exception e) {
            log.warn("Failed to delete webhook {} (may already be deleted): {}",
                    webhookId, e.getMessage());
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private ShopifyProductDto jsonToDto(JsonNode p) {
        return ShopifyProductDto.builder()
                .wooId(p.path("id").asText())
                .title(p.path("name").asText())
                .bodyHtml(p.path("description").asText())
                .vendor("")
                .productType(firstCategoryName(p))
                .tags(joinTags(p))
                .handle(p.path("slug").asText())
                .status(mapWooStatusToUi(p.path("status").asText()))
                .price(p.path("sale_price").asText().isBlank()
                        ? p.path("regular_price").asText()
                        : p.path("sale_price").asText())
                .compareAtPrice(p.path("sale_price").asText().isBlank()
                        ? "" : p.path("regular_price").asText())
                .sku(p.path("sku").asText())
                .inventoryQuantity(p.path("stock_quantity").isNull()
                        ? null : p.path("stock_quantity").asInt())
                .seoTitle(readSeoFromMeta(p)[0])
                .seoDescription(readSeoFromMeta(p)[1])
                .build();
    }

    private static String firstCategoryName(JsonNode p) {
        JsonNode cats = p.path("categories");
        if (cats.isArray() && cats.size() > 0) {
            return cats.get(0).path("name").asText("");
        }
        return "";
    }

    private static String joinTags(JsonNode p) {
        JsonNode tags = p.path("tags");
        if (!tags.isArray()) return "";
        List<String> out = new ArrayList<>();
        for (JsonNode t : tags) {
            String n = t.path("name").asText();
            if (!n.isBlank()) out.add(n);
        }
        return String.join(", ", out);
    }

    /** Read Yoast or Rank Math SEO fields from meta_data. Returns [title, description]. */
    private static String[] readSeoFromMeta(JsonNode p) {
        String title = "", desc = "";
        for (JsonNode m : p.path("meta_data")) {
            String key = m.path("key").asText();
            String val = m.path("value").asText("");
            switch (key) {
                case "_yoast_wpseo_title":
                case "rank_math_title":
                    if (title.isBlank()) title = val;
                    break;
                case "_yoast_wpseo_metadesc":
                case "rank_math_description":
                    if (desc.isBlank()) desc = val;
                    break;
            }
        }
        return new String[]{ title, desc };
    }

    /** Yoast-first meta_data builder (Rank Math keys are added too for compatibility). */
    private static List<Map<String, Object>> buildSeoMeta(String seoTitle, String seoDescription) {
        List<Map<String, Object>> meta = new ArrayList<>();
        if (notBlank(seoTitle)) {
            meta.add(Map.of("key", "_yoast_wpseo_title", "value", seoTitle));
            meta.add(Map.of("key", "rank_math_title",    "value", seoTitle));
        }
        if (notBlank(seoDescription)) {
            meta.add(Map.of("key", "_yoast_wpseo_metadesc", "value", seoDescription));
            meta.add(Map.of("key", "rank_math_description", "value", seoDescription));
        }
        return meta;
    }

    private static List<Map<String, Object>> tagsFromCsv(String csv) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (csv == null) return out;
        for (String raw : csv.split(",")) {
            String t = raw.trim();
            if (!t.isEmpty()) out.add(Map.of("name", t));
        }
        return out;
    }

    private static List<Map<String, Object>> categoriesFromCsv(String csv) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (csv == null) return out;
        for (String raw : csv.split(",")) {
            String c = raw.trim();
            if (!c.isEmpty()) out.add(Map.of("name", c));
        }
        return out;
    }

    /** Map our UI status vocabulary (active/draft/archived) onto WooCommerce's. */
    private static String mapUiStatusToWoo(String uiStatus) {
        if (uiStatus == null) return "publish";
        return switch (uiStatus.toLowerCase()) {
            case "active", "publish"  -> "publish";
            case "draft"              -> "draft";
            case "archived", "private"-> "private";
            case "pending"            -> "pending";
            default                    -> "publish";
        };
    }

    private static String mapWooStatusToUi(String wooStatus) {
        if (wooStatus == null) return "active";
        return switch (wooStatus.toLowerCase()) {
            case "publish" -> "active";
            case "draft"   -> "draft";
            case "private" -> "archived";
            default         -> wooStatus.toLowerCase();
        };
    }

    private static String firstNonBlank(String a, String b) {
        return notBlank(a) ? a : (b == null ? "" : b);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String base(String storeUrl) {
        String u = storeUrl.trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        return u + API_BASE;
    }

    private static String basicAuth(String consumerKey, String consumerSecret) {
        String raw = consumerKey + ":" + consumerSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String get(String url, String consumerKey, String consumerSecret) {
        return webClientBuilder.build()
                .get().uri(url)
                .header("Authorization", basicAuth(consumerKey, consumerSecret))
                .retrieve().bodyToMono(String.class).block();
    }

    private String post(String url, String consumerKey, String consumerSecret, String body) {
        return webClientBuilder.build()
                .post().uri(url)
                .header("Authorization", basicAuth(consumerKey, consumerSecret))
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve().bodyToMono(String.class).block();
    }

    private String put(String url, String consumerKey, String consumerSecret, String body) {
        return webClientBuilder.build()
                .put().uri(url)
                .header("Authorization", basicAuth(consumerKey, consumerSecret))
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve().bodyToMono(String.class).block();
    }
}
