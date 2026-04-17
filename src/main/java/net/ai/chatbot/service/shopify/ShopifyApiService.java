package net.ai.chatbot.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.shopify.DirectProductUpdateRequest;
import net.ai.chatbot.dto.shopify.ShopifyProductDto;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shopify Admin REST API client (API version 2024-01).
 */
@Service
@Slf4j
public class ShopifyApiService {

    private static final String API_VERSION = "2024-01";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient.Builder webClientBuilder;

    public ShopifyApiService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // ── Connection test ───────────────────────────────────────────────────────

    public String testConnection(String shopDomain, String accessToken) {
        String url = buildBaseUrl(shopDomain) + "/shop.json";
        String response = get(url, accessToken);
        try {
            return OBJECT_MAPPER.readTree(response).path("shop").path("name").asText("Unknown Shop");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Shopify shop info");
        }
    }

    // ── List products (with metafields fetched per product) ───────────────────

    public List<ShopifyProductDto> listProducts(String shopDomain, String accessToken, int limit) {
        String url = buildBaseUrl(shopDomain)
                + "/products.json?limit=" + Math.min(limit, 250)
                + "&fields=id,title,body_html,vendor,product_type,tags,handle,status";

        String response = get(url, accessToken);
        List<ShopifyProductDto> products = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            for (JsonNode p : root.path("products")) {
                String productId = p.path("id").asText();
                ShopifyProductDto dto = ShopifyProductDto.builder()
                        .shopifyId(productId)
                        .title(p.path("title").asText())
                        .bodyHtml(p.path("body_html").asText())
                        .vendor(p.path("vendor").asText())
                        .productType(p.path("product_type").asText())
                        .tags(p.path("tags").asText())
                        .handle(p.path("handle").asText())
                        .status(p.path("status").asText())
                        .build();

                // Fetch SEO metafields for this product
                fetchSeoMetafields(shopDomain, accessToken, productId, dto);
                products.add(dto);
            }
        } catch (Exception e) {
            log.error("Failed to parse Shopify products", e);
            throw new RuntimeException("Failed to parse Shopify products");
        }
        return products;
    }

    // ── Fetch SEO metafields (title_tag + description_tag) ────────────────────

    private void fetchSeoMetafields(String shopDomain, String accessToken,
                                     String productId, ShopifyProductDto dto) {
        try {
            String url = buildBaseUrl(shopDomain)
                    + "/products/" + productId
                    + "/metafields.json?namespace=global&fields=key,value";
            String response = get(url, accessToken);
            JsonNode root = OBJECT_MAPPER.readTree(response);
            for (JsonNode mf : root.path("metafields")) {
                String key = mf.path("key").asText();
                String value = mf.path("value").asText();
                if ("title_tag".equals(key)) dto.setSeoTitle(value);
                if ("description_tag".equals(key)) dto.setSeoDescription(value);
            }
        } catch (Exception e) {
            log.warn("Could not fetch metafields for product {}: {}", productId, e.getMessage());
        }
    }

    // ── Update product (all fields + SEO metafields) ──────────────────────────

    public void updateProduct(String shopDomain, String accessToken, ShopifyProductDto enhanced) {
        String url = buildBaseUrl(shopDomain) + "/products/" + enhanced.getShopifyId() + ".json";

        Map<String, Object> product = new HashMap<>();
        product.put("id", enhanced.getShopifyId());

        if (notBlank(enhanced.getEnhancedTitle()))       product.put("title",        enhanced.getEnhancedTitle());
        if (notBlank(enhanced.getEnhancedBodyHtml()))    product.put("body_html",     enhanced.getEnhancedBodyHtml());
        if (notBlank(enhanced.getEnhancedTags()))        product.put("tags",          enhanced.getEnhancedTags());
        if (notBlank(enhanced.getEnhancedProductType())) product.put("product_type",  enhanced.getEnhancedProductType());
        if (notBlank(enhanced.getEnhancedHandle()))      product.put("handle",        enhanced.getEnhancedHandle());

        // SEO metafields (title_tag + description_tag)
        List<Map<String, Object>> metafields = new ArrayList<>();
        if (notBlank(enhanced.getEnhancedSeoTitle())) {
            metafields.add(buildMetafield("global", "title_tag",
                    enhanced.getEnhancedSeoTitle(), "single_line_text_field"));
        }
        if (notBlank(enhanced.getEnhancedSeoDescription())) {
            metafields.add(buildMetafield("global", "description_tag",
                    enhanced.getEnhancedSeoDescription(), "single_line_text_field"));
        }
        if (!metafields.isEmpty()) product.put("metafields", metafields);

        try {
            String body = OBJECT_MAPPER.writeValueAsString(Map.of("product", product));
            put(url, accessToken, body);
            log.info("Updated Shopify product {}", enhanced.getShopifyId());
        } catch (Exception e) {
            log.error("Failed to update Shopify product {}", enhanced.getShopifyId(), e);
            throw new RuntimeException("Failed to update product " + enhanced.getShopifyId());
        }
    }

    // ── Full product detail (all fields + metafields + variants + images) ────

    public Map<String, Object> getFullProduct(String shopDomain, String accessToken, String productId) {
        String url = buildBaseUrl(shopDomain) + "/products/" + productId + ".json";
        String response = get(url, accessToken);
        try {
            JsonNode p = OBJECT_MAPPER.readTree(response).path("product");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id",             p.path("id").asText());
            result.put("title",          p.path("title").asText());
            result.put("bodyHtml",       p.path("body_html").asText());
            result.put("vendor",         p.path("vendor").asText());
            result.put("productType",    p.path("product_type").asText());
            result.put("tags",           p.path("tags").asText());
            result.put("handle",         p.path("handle").asText());
            result.put("status",         p.path("status").asText());
            result.put("publishedScope", p.path("published_scope").asText());
            result.put("templateSuffix", p.path("template_suffix").isNull() ? "" : p.path("template_suffix").asText());
            result.put("createdAt",      p.path("created_at").asText());
            result.put("updatedAt",      p.path("updated_at").asText());
            result.put("publishedAt",    p.path("published_at").isNull() ? "" : p.path("published_at").asText());

            // Variants
            List<Map<String, Object>> variants = new ArrayList<>();
            for (JsonNode v : p.path("variants")) {
                Map<String, Object> variant = new LinkedHashMap<>();
                variant.put("id",                v.path("id").asText());
                variant.put("title",             v.path("title").asText());
                variant.put("price",             v.path("price").asText());
                variant.put("compareAtPrice",    v.path("compare_at_price").isNull() ? "" : v.path("compare_at_price").asText());
                variant.put("sku",               v.path("sku").asText());
                variant.put("barcode",           v.path("barcode").asText());
                variant.put("weight",            v.path("weight").asDouble());
                variant.put("weightUnit",        v.path("weight_unit").asText());
                variant.put("inventoryQuantity", v.path("inventory_quantity").asInt());
                variant.put("taxable",           v.path("taxable").asBoolean());
                variant.put("requiresShipping",  v.path("requires_shipping").asBoolean());
                variant.put("position",          v.path("position").asInt());
                variant.put("option1",           v.path("option1").isNull() ? "" : v.path("option1").asText());
                variant.put("option2",           v.path("option2").isNull() ? "" : v.path("option2").asText());
                variant.put("option3",           v.path("option3").isNull() ? "" : v.path("option3").asText());
                variants.add(variant);
            }
            result.put("variants", variants);

            // Options
            List<Map<String, Object>> options = new ArrayList<>();
            for (JsonNode o : p.path("options")) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("id",   o.path("id").asText());
                option.put("name", o.path("name").asText());
                List<String> values = new ArrayList<>();
                for (JsonNode val : o.path("values")) values.add(val.asText());
                option.put("values", values);
                options.add(option);
            }
            result.put("options", options);

            // Images
            List<Map<String, Object>> images = new ArrayList<>();
            for (JsonNode img : p.path("images")) {
                Map<String, Object> image = new LinkedHashMap<>();
                image.put("id",       img.path("id").asText());
                image.put("src",      img.path("src").asText());
                image.put("alt",      img.path("alt").isNull() ? "" : img.path("alt").asText());
                image.put("position", img.path("position").asInt());
                images.add(image);
            }
            result.put("images", images);

            // SEO metafields
            fetchSeoToMap(shopDomain, accessToken, productId, result);

            return result;
        } catch (Exception e) {
            log.error("Failed to parse full Shopify product {}", productId, e);
            throw new RuntimeException("Failed to fetch product details: " + e.getMessage());
        }
    }

    private void fetchSeoToMap(String shopDomain, String accessToken,
                                String productId, Map<String, Object> target) {
        target.put("seoTitle", "");
        target.put("seoDescription", "");
        try {
            String url = buildBaseUrl(shopDomain)
                    + "/products/" + productId + "/metafields.json?namespace=global&fields=key,value";
            String response = get(url, accessToken);
            JsonNode root = OBJECT_MAPPER.readTree(response);
            for (JsonNode mf : root.path("metafields")) {
                String key   = mf.path("key").asText();
                String value = mf.path("value").asText();
                if ("title_tag".equals(key))       target.put("seoTitle",       value);
                if ("description_tag".equals(key)) target.put("seoDescription", value);
            }
        } catch (Exception e) {
            log.warn("Could not fetch SEO metafields for product {}: {}", productId, e.getMessage());
        }
    }

    // ── Direct update (all mutable fields at once) ────────────────────────────

    public void updateProductDirect(String shopDomain, String accessToken,
                                     String shopifyId, DirectProductUpdateRequest req) {
        String url = buildBaseUrl(shopDomain) + "/products/" + shopifyId + ".json";

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("id", shopifyId);

        if (notBlank(req.getTitle()))       product.put("title",        req.getTitle());
        if (notBlank(req.getBodyHtml()))    product.put("body_html",     req.getBodyHtml());
        if (notBlank(req.getVendor()))      product.put("vendor",        req.getVendor());
        if (notBlank(req.getProductType())) product.put("product_type",  req.getProductType());
        if (notBlank(req.getTags()))        product.put("tags",          req.getTags());
        if (notBlank(req.getHandle()))      product.put("handle",        req.getHandle());
        if (notBlank(req.getStatus()))      product.put("status",        req.getStatus());

        List<Map<String, Object>> metafields = new ArrayList<>();
        if (notBlank(req.getSeoTitle())) {
            metafields.add(buildMetafield("global", "title_tag",
                    req.getSeoTitle(), "single_line_text_field"));
        }
        if (notBlank(req.getSeoDescription())) {
            metafields.add(buildMetafield("global", "description_tag",
                    req.getSeoDescription(), "single_line_text_field"));
        }
        if (!metafields.isEmpty()) product.put("metafields", metafields);

        try {
            String body = OBJECT_MAPPER.writeValueAsString(Map.of("product", product));
            put(url, accessToken, body);
            log.info("Directly updated Shopify product {}", shopifyId);
        } catch (Exception e) {
            log.error("Failed to directly update Shopify product {}", shopifyId, e);
            throw new RuntimeException("Failed to update product: " + e.getMessage());
        }
    }

    // ── Webhook management ────────────────────────────────────────────────────

    public String registerProductCreatedWebhook(String shopDomain, String accessToken, String callbackUrl) {
        String url = buildBaseUrl(shopDomain) + "/webhooks.json";
        try {
            Map<String, Object> webhook = new HashMap<>();
            webhook.put("topic",   "products/create");
            webhook.put("address", callbackUrl);
            webhook.put("format",  "json");

            String body = OBJECT_MAPPER.writeValueAsString(Map.of("webhook", webhook));
            String response = post(url, accessToken, body);
            String webhookId = OBJECT_MAPPER.readTree(response).path("webhook").path("id").asText();
            log.info("Registered Shopify webhook {} for domain {}", webhookId, shopDomain);
            return webhookId;
        } catch (Exception e) {
            log.error("Failed to register Shopify webhook", e);
            throw new RuntimeException("Failed to register webhook");
        }
    }

    public void deleteWebhook(String shopDomain, String accessToken, String webhookId) {
        String url = buildBaseUrl(shopDomain) + "/webhooks/" + webhookId + ".json";
        try {
            webClientBuilder.build()
                    .delete().uri(url)
                    .header("X-Shopify-Access-Token", accessToken)
                    .retrieve().bodyToMono(String.class).block();
            log.info("Deleted Shopify webhook {}", webhookId);
        } catch (Exception e) {
            log.warn("Failed to delete webhook {} (may already be deleted): {}", webhookId, e.getMessage());
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String get(String url, String accessToken) {
        return webClientBuilder.build()
                .get().uri(url)
                .header("X-Shopify-Access-Token", accessToken)
                .retrieve().bodyToMono(String.class).block();
    }

    private String post(String url, String accessToken, String body) {
        return webClientBuilder.build()
                .post().uri(url)
                .header("X-Shopify-Access-Token", accessToken)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve().bodyToMono(String.class).block();
    }

    private void put(String url, String accessToken, String body) {
        webClientBuilder.build()
                .put().uri(url)
                .header("X-Shopify-Access-Token", accessToken)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve().bodyToMono(String.class).block();
    }

    private Map<String, Object> buildMetafield(String namespace, String key,
                                                String value, String type) {
        Map<String, Object> mf = new HashMap<>();
        mf.put("namespace", namespace);
        mf.put("key",       key);
        mf.put("value",     value);
        mf.put("type",      type);
        return mf;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String buildBaseUrl(String shopDomain) {
        String domain = shopDomain.trim().toLowerCase();
        if (!domain.endsWith(".myshopify.com")) domain += ".myshopify.com";
        return "https://" + domain + "/admin/api/" + API_VERSION;
    }
}
