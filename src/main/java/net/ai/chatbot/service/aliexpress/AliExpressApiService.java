package net.ai.chatbot.service.aliexpress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * TOP / AliExpress REST gateway client ({@code gw.api.taobao.com}) with MD5 signing.
 */
@Service
@Slf4j
public class AliExpressApiService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TOP_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    @Value("${aliexpress.api.gateway-url:https://gw.api.taobao.com/router/rest}")
    private String gatewayUrl;

    @Value("${aliexpress.oauth.app-key:${ALIEXPRESS_APP_KEY:}}")
    private String appKey;

    @Value("${aliexpress.oauth.app-secret:${ALIEXPRESS_APP_SECRET:}}")
    private String appSecret;

    private final WebClient.Builder webClientBuilder;

    public AliExpressApiService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public JsonNode execute(String method, String sessionKey, Map<String, String> appParams) {
        TreeMap<String, String> all = new TreeMap<>();
        all.put("method", method);
        all.put("app_key", appKey);
        all.put("timestamp", TOP_TS.format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))));
        all.put("format", "json");
        all.put("v", "2.0");
        all.put("sign_method", "md5");
        if (sessionKey != null && !sessionKey.isBlank()) {
            all.put("session", sessionKey);
        }
        if (appParams != null) {
            for (Map.Entry<String, String> e : appParams.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isBlank()) {
                    all.put(e.getKey(), e.getValue());
                }
            }
        }
        String sign = signTopRequest(all, appSecret);
        all.put("sign", sign);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (Map.Entry<String, String> e : all.entrySet()) {
            form.add(e.getKey(), e.getValue());
        }

        String raw = webClientBuilder.build()
                .post()
                .uri(gatewayUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(raw == null ? "{}" : raw);
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON from AliExpress gateway", e);
        }
        if (root.has("error_response")) {
            JsonNode err = root.get("error_response");
            throw new RuntimeException("AliExpress API error: "
                    + err.path("code").asText() + " — " + err.path("msg").asText());
        }
        return root;
    }

    /**
     * {@code aliexpress.solution.product.list.get} — paginated seller catalogue snapshot.
     */
    public List<Map<String, Object>> listProductsPage(String sessionKey, String statusType,
                                                      int page, int pageSize) {
        String queryJson;
        try {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("product_status_type", statusType == null || statusType.isBlank() ? "onSelling" : statusType);
            q.put("current_page", page);
            q.put("page_size", pageSize);
            queryJson = OBJECT_MAPPER.writeValueAsString(q);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        JsonNode root = execute("aliexpress.solution.product.list.get", sessionKey,
                Map.of("product_list_query", queryJson));
        JsonNode body = firstResponseObject(root);
        JsonNode result = body == null ? null : body.get("result");
        if (result == null || result.isNull()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        collectProductLikeObjects(result, rows);
        return rows;
    }

    /** {@code aliexpress.solution.product.info.get} */
    public JsonNode getProductInfo(String sessionKey, String productId) {
        JsonNode root = execute("aliexpress.solution.product.info.get", sessionKey,
                Map.of("product_id", productId));
        JsonNode body = firstResponseObject(root);
        return body == null ? OBJECT_MAPPER.createObjectNode() : body;
    }

    /**
     * Partial update via {@code aliexpress.solution.product.edit} — title + HTML description only.
     */
    public void editProductTitleAndDetail(String sessionKey, String productId, String locale,
                                         String title, String htmlBody) {
        if (productId == null || productId.isBlank()) {
            throw new RuntimeException("product_id is required");
        }
        String loc = (locale == null || locale.isBlank()) ? "en" : locale.trim();
        try {
            boolean hasTitle = title != null && !title.isBlank();
            boolean hasBody = htmlBody != null && !htmlBody.isBlank();
            if (!hasTitle && !hasBody) {
                return;
            }
            Map<String, Object> edit = new LinkedHashMap<>();
            edit.put("product_id", Long.parseLong(productId));

            if (hasTitle) {
                List<Map<String, Object>> subjects = new ArrayList<>();
                Map<String, Object> st = new LinkedHashMap<>();
                st.put("locale", loc);
                st.put("subject", title);
                subjects.add(st);
                edit.put("multi_language_subject_list", subjects);
            }

            if (hasBody) {
                String webDetail = buildWebDetailJson(htmlBody);
                List<Map<String, Object>> desc = new ArrayList<>();
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("locale", loc);
                d.put("web_detail", webDetail);
                desc.add(d);
                edit.put("multi_language_description_list", desc);
            }

            String json = OBJECT_MAPPER.writeValueAsString(edit);
            execute("aliexpress.solution.product.edit", sessionKey, Map.of("edit_product_request", json));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid AliExpress product id: " + productId);
        } catch (Exception e) {
            throw new RuntimeException("AliExpress product.edit failed: " + e.getMessage(), e);
        }
    }

    private static String buildWebDetailJson(String html) throws Exception {
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("type", "html");
        Map<String, Object> inner = Map.of("content", html);
        module.put("html", inner);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "2.0.0");
        root.put("moduleList", List.of(module));
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    private static JsonNode firstResponseObject(JsonNode root) {
        if (root == null || !root.isObject()) return null;
        var it = root.fields();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey().endsWith("_response")) {
                return e.getValue();
            }
        }
        return null;
    }

    private static void collectProductLikeObjects(JsonNode node, List<Map<String, Object>> out) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            for (JsonNode el : node) {
                if (el != null && el.isObject() && (el.has("product_id") || el.has("productId"))
                        && (el.has("subject") || el.has("product_subject"))) {
                    out.add(jsonObjectToMap(el));
                } else {
                    collectProductLikeObjects(el, out);
                }
            }
        } else if (node.isObject()) {
            node.fields().forEachRemaining(e -> collectProductLikeObjects(e.getValue(), out));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonObjectToMap(JsonNode n) {
        return OBJECT_MAPPER.convertValue(n, Map.class);
    }

    /** Taobao TOP MD5 signature. */
    static String signTopRequest(TreeMap<String, String> params, String secret) {
        StringBuilder sb = new StringBuilder(secret);
        for (Map.Entry<String, String> e : params.entrySet()) {
            if ("sign".equalsIgnoreCase(e.getKey())) continue;
            String v = e.getValue();
            if (v == null || v.isEmpty()) continue;
            sb.append(e.getKey()).append(v);
        }
        sb.append(secret);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString().toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw new RuntimeException("MD5 signing failed", e);
        }
    }

    /** Flatten {@code product.info.get} into the shape our UI / AI layer expect. */
    public Map<String, Object> flattenProductInfo(JsonNode responseBody, String productId) {
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("id", productId);
        flat.put("productId", productId);

        JsonNode result = responseBody.get("result");
        if (result == null) result = responseBody;

        putIfPresent(flat, "title", textAt(result, "subject", "aeop_subject", "product_subject"));
        putIfPresent(flat, "bodyHtml", textAt(result, "detail", "aeop_detail", "mobile_detail", "web_detail"));

        String imgs = textAt(result, "image_urls", "aeop_image_urls", "image_u_r_ls");
        if (imgs != null && !imgs.isBlank()) {
            List<String> list = new ArrayList<>();
            for (String p : imgs.split(";")) {
                if (p != null && !p.isBlank()) list.add(p.trim());
            }
            flat.put("images", list);
        }

        flat.put("currency", Objects.toString(firstString(result, "currency_code", "currencyCode"), "USD"));
        flat.put("price", Objects.toString(firstString(result, "product_price", "aeop_product_price"), ""));
        flat.put("categoryId", Objects.toString(firstString(result, "category_id", "categoryId"), ""));
        flat.put("brandName", Objects.toString(firstString(result, "brand_name", "brandName"), ""));
        flat.put("aeStatus", Objects.toString(firstString(result, "product_status_type", "ws_display"), ""));
        return flat;
    }

    private static void putIfPresent(Map<String, Object> m, String key, String val) {
        if (val != null && !val.isBlank()) m.put(key, val);
    }

    private static String textAt(JsonNode n, String... names) {
        if (n == null) return null;
        for (String name : names) {
            JsonNode v = n.get(name);
            if (v != null && !v.isNull()) {
                if (v.isTextual()) return v.asText();
                if (v.isNumber()) return v.asText();
                if (v.isObject() || v.isArray()) {
                    return v.toString();
                }
            }
        }
        return deepFindString(n, names);
    }

    private static String deepFindString(JsonNode node, String[] preferredKeys) {
        if (node == null) return null;
        if (node.isObject()) {
            for (String k : preferredKeys) {
                if (node.has(k)) {
                    JsonNode v = node.get(k);
                    if (v != null && v.isTextual()) return v.asText();
                }
            }
            var it = node.fields();
            while (it.hasNext()) {
                var e = it.next();
                String s = deepFindString(e.getValue(), preferredKeys);
                if (s != null && !s.isBlank()) return s;
            }
        } else if (node.isArray()) {
            for (JsonNode el : node) {
                String s = deepFindString(el, preferredKeys);
                if (s != null && !s.isBlank()) return s;
            }
        }
        return null;
    }

    private static String firstString(JsonNode n, String... keys) {
        if (n == null) return null;
        for (String k : keys) {
            if (n.has(k) && n.get(k).isTextual()) return n.get(k).asText();
        }
        return null;
    }
}
