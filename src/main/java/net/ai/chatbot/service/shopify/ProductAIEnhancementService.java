package net.ai.chatbot.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.shopify.ShopifyProductDto;
import net.ai.chatbot.entity.ProductEnhancementJob.ProductItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Uses OpenAI GPT to generate SEO-optimized content for all enhanceable
 * Shopify product fields.
 *
 * Fields enhanced:
 *   title, body_html, tags, product_type, handle (URL slug),
 *   seo_title (title_tag metafield), seo_description (description_tag metafield)
 */
@Service
@Slf4j
public class ProductAIEnhancementService {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parse raw file content using AI to extract product information.
     * Works with ANY format: CSV, JSON, plain text, unstructured data, etc.
     */
    public List<ShopifyProductDto> parseProductsWithAI(String content, String fileName) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            throw new RuntimeException("OpenAI API key not configured");
        }

        // Truncate very large content to avoid token limits
        int maxLength = 15000; // ~4000 tokens worth of text
        String truncatedContent = content.length() > maxLength 
            ? content.substring(0, maxLength) + "\n\n[Content truncated...]"
            : content;

        String systemPrompt = """
                You are an expert data extraction AI. Your task is to parse product information from any format (CSV, JSON, plain text, unstructured data, etc.) and extract product details.
                
                Extract as much information as possible for each product:
                - title (required): Product name
                - body_html: Product description (can be HTML or plain text)
                - vendor: Brand or manufacturer
                - product_type: Product category or type
                - tags: Comma-separated keywords/tags
                - shopify_id: Product ID or SKU if available
                
                Rules:
                1. ALWAYS extract at least the product title/name
                2. Extract as many additional fields as you can find
                3. If a field is not present, omit it or use empty string
                4. For descriptions, preserve formatting when possible
                5. Be flexible with field names (e.g., "brand" = vendor, "category" = product_type)
                6. Handle multiple products in the same file
                7. If the content is just a list of names, that's fine - extract them all as titles
                
                Return ONLY a valid JSON object with this structure:
                {
                  "products": [
                    {
                      "title": "Product Name",
                      "body_html": "Description here",
                      "vendor": "Brand Name",
                      "product_type": "Category",
                      "tags": "tag1,tag2,tag3",
                      "shopify_id": "SKU123"
                    }
                  ]
                }
                
                If you cannot extract ANY products, return: {"products": []}
                """;

        String userPrompt = String.format("""
                File name: %s
                
                Content to parse:
                %s
                
                Extract all products from this content. Be as flexible as possible and extract as much information as you can find.
                """, fileName != null ? fileName : "unknown", truncatedContent);

        try {
            // Build request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini"); // Fast and cost-effective for parsing
            requestBody.put("temperature", 0.1); // Low temperature for consistent parsing
            
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
            requestBody.put("messages", messages);
            
            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            requestBody.put("response_format", responseFormat);

            // Call OpenAI
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    OPENAI_CHAT_URL, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("OpenAI API call failed: " + response.getStatusCode());
            }

            // Parse response
            JsonNode root = OBJECT_MAPPER.readTree(response.getBody());
            String contentText = root.path("choices").get(0).path("message").path("content").asText();
            
            JsonNode parsed = OBJECT_MAPPER.readTree(contentText);
            JsonNode productsNode = parsed.path("products");
            
            if (!productsNode.isArray()) {
                log.warn("AI response doesn't contain products array");
                return Collections.emptyList();
            }

            // Convert to DTOs
            List<ShopifyProductDto> products = new ArrayList<>();
            for (JsonNode productNode : productsNode) {
                try {
                    ShopifyProductDto dto = ShopifyProductDto.builder()
                            .shopifyId(getTextOrDefault(productNode, "shopify_id", ""))
                            .title(getTextOrDefault(productNode, "title", ""))
                            .bodyHtml(getTextOrDefault(productNode, "body_html", ""))
                            .vendor(getTextOrDefault(productNode, "vendor", ""))
                            .productType(getTextOrDefault(productNode, "product_type", ""))
                            .tags(getTextOrDefault(productNode, "tags", ""))
                            .build();
                    
                    // Only add if has title
                    if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
                        products.add(dto);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse product node: {}", e.getMessage());
                }
            }

            log.info("AI parsed {} products from file: {}", products.size(), fileName);
            return products;

        } catch (Exception e) {
            log.error("AI parsing failed: {}", e.getMessage(), e);
            throw new RuntimeException("AI parsing failed: " + e.getMessage());
        }
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? defaultValue : fieldNode.asText(defaultValue);
    }

    // ── Image → Product description (GPT-4o Vision) ───────────────────────────

    /**
     * Generate a product draft from one or more images. The model is asked to
     * treat ALL supplied images as views of the SAME product (front/side/back
     * shots, packaging, detail, etc.) and return a single product entry.
     * <p>
     * Each image must be a data URL (e.g. {@code data:image/png;base64,...})
     * or a publicly reachable https URL — both are accepted by the OpenAI
     * vision API under the {@code image_url} content part.
     */
    public List<ShopifyProductDto> parseProductsFromImages(List<String> imageUrls) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            throw new RuntimeException("OpenAI API key not configured");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Collections.emptyList();
        }

        String systemPrompt = """
                You are an expert e-commerce copywriter and product photographer.
                You will be shown one or more photos of the SAME product (different
                angles, close-ups, or packaging). Identify the product and produce
                a clean, professional Shopify product draft.
                
                Return ONLY a valid JSON object with this structure:
                {
                  "products": [
                    {
                      "title":           "Clear, specific product name (include brand if visible).",
                      "body_html":       "HTML description using <p>, <ul>, <li>, <strong>. Cover: what it is, key visible features, materials/finish, likely use cases. 150–250 words. No inline styles.",
                      "vendor":          "Brand or manufacturer if visible on packaging/label, else empty string.",
                      "product_type":    "Accurate product category, 2–4 words, title case (e.g. 'Running Shoes').",
                      "tags":            "10–15 comma-separated SEO tags (keywords, materials, colors, use cases).",
                      "seo_title":       "SEO meta title, max 60 characters.",
                      "seo_description": "SEO meta description, 140–160 characters with a subtle CTA."
                    }
                  ]
                }
                
                Rules:
                - Return a single product entry — treat all supplied images as the same item.
                - Be specific, not generic. Use details you can actually see in the photos.
                - Never return null; use empty strings for unknown fields.
                - If the image is clearly NOT a product (screenshot, selfie, landscape, etc.),
                  return {"products": []}.
                """;

        try {
            // Build the multimodal user message: one text part + N image_url parts.
            List<Map<String, Object>> userContent = new ArrayList<>();
            userContent.add(Map.of("type", "text",
                    "text", "Generate a Shopify product draft from these image(s). "
                            + "They are all views of the same item."));
            for (String url : imageUrls) {
                if (url == null || url.isBlank()) continue;
                userContent.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", url, "detail", "low")
                ));
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini"); // vision-capable, cheap
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 1200);
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user",   "content", userContent)
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    OPENAI_CHAT_URL, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("OpenAI vision call failed: " + response.getStatusCode());
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.getBody());
            String contentText = root.path("choices").get(0).path("message").path("content").asText();

            JsonNode parsed = OBJECT_MAPPER.readTree(contentText);
            JsonNode productsNode = parsed.path("products");
            if (!productsNode.isArray()) {
                log.warn("Vision response does not contain a products array: {}", contentText);
                return Collections.emptyList();
            }

            List<ShopifyProductDto> products = new ArrayList<>();
            for (JsonNode n : productsNode) {
                ShopifyProductDto dto = ShopifyProductDto.builder()
                        .title(getTextOrDefault(n,           "title",           ""))
                        .bodyHtml(getTextOrDefault(n,        "body_html",       ""))
                        .vendor(getTextOrDefault(n,          "vendor",          ""))
                        .productType(getTextOrDefault(n,     "product_type",    ""))
                        .tags(getTextOrDefault(n,            "tags",            ""))
                        .seoTitle(getTextOrDefault(n,        "seo_title",       ""))
                        .seoDescription(getTextOrDefault(n,  "seo_description", ""))
                        .build();
                if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
                    products.add(dto);
                }
            }

            log.info("Vision parsed {} product(s) from {} image(s)", products.size(), imageUrls.size());
            return products;

        } catch (Exception e) {
            log.error("Image-to-product parsing failed: {}", e.getMessage(), e);
            throw new RuntimeException("Image-to-product parsing failed: " + e.getMessage());
        }
    }

    public List<ProductItem> enhanceProducts(List<ShopifyProductDto> rawProducts) {
        List<ProductItem> enhanced = new ArrayList<>();
        for (ShopifyProductDto raw : rawProducts) {
            try {
                enhanced.add(enhanceSingle(raw));
            } catch (Exception e) {
                log.error("Failed to enhance product '{}': {}", raw.getTitle(), e.getMessage());
                enhanced.add(buildFailedItem(raw));
            }
        }
        return enhanced;
    }

    public ProductItem enhanceSingle(ShopifyProductDto raw) {
        String prompt  = buildPrompt(raw);
        String aiJson  = callOpenAI(prompt);
        return parseAIResponse(raw, aiJson);
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private String buildPrompt(ShopifyProductDto p) {
        return String.format("""
                You are an expert Shopify e-commerce copywriter and SEO specialist.
                
                Analyze the product below and generate optimized content for ALL fields.
                
                RAW PRODUCT DATA:
                - Title: %s
                - Description: %s
                - Vendor: %s
                - Product Type: %s
                - Tags: %s
                - URL Handle: %s
                - Current SEO Title: %s
                - Current SEO Description: %s
                
                Generate a JSON response with EXACTLY these 7 fields:
                {
                  "enhanced_title": "Compelling, keyword-rich product title (50–70 characters). Include the main keyword naturally.",
                  "enhanced_body_html": "Persuasive HTML description using <p>, <ul>, <li>, <strong>, <em> tags. Cover: key features, benefits, use cases, materials/specs if relevant. Minimum 200 words. No inline styles.",
                  "enhanced_tags": "15–20 comma-separated SEO tags. Mix broad keywords, long-tail phrases, and purchase-intent terms.",
                  "enhanced_product_type": "Accurate, standard product category (2–4 words, title case). E.g. 'Running Shoes', 'Ceramic Cookware'.",
                  "enhanced_handle": "lowercase-hyphenated-url-slug from the enhanced title. Only letters, numbers, hyphens. No stop words.",
                  "enhanced_seo_title": "SEO meta title — max 60 characters. Include main keyword + brand if space allows.",
                  "enhanced_seo_description": "SEO meta description — 140–160 characters. Compelling, includes keyword and a subtle CTA."
                }
                
                Rules:
                - Return ONLY valid JSON — no markdown, no code blocks, no extra text.
                - All fields are required. Never return null or empty strings.
                - enhanced_seo_title MUST be ≤60 characters.
                - enhanced_seo_description MUST be 140–160 characters.
                - enhanced_handle MUST be lowercase with only hyphens as separators.
                """,
                safe(p.getTitle()), safe(p.getBodyHtml()),
                safe(p.getVendor()), safe(p.getProductType()),
                safe(p.getTags()), safe(p.getHandle()),
                safe(p.getSeoTitle()), safe(p.getSeoDescription())
        );
    }

    // ── OpenAI call ───────────────────────────────────────────────────────────

    private String callOpenAI(String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("temperature", 0.7);
        body.put("max_tokens", 1500);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

        try {
            String bodyJson = OBJECT_MAPPER.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    OPENAI_CHAT_URL, HttpMethod.POST, entity, String.class);
            return OBJECT_MAPPER.readTree(response.getBody())
                    .path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            throw new RuntimeException("AI enhancement failed: " + e.getMessage());
        }
    }

    // ── Parse response ────────────────────────────────────────────────────────

    private ProductItem parseAIResponse(ShopifyProductDto raw, String aiJson) {
        try {
            JsonNode n = OBJECT_MAPPER.readTree(aiJson);
            return ProductItem.builder()
                    // raw values preserved
                    .shopifyId(raw.getShopifyId())
                    .title(raw.getTitle())
                    .bodyHtml(raw.getBodyHtml())
                    .vendor(raw.getVendor())
                    .productType(raw.getProductType())
                    .tags(raw.getTags())
                    .handle(raw.getHandle())
                    .seoTitle(raw.getSeoTitle())
                    .seoDescription(raw.getSeoDescription())
                    // AI-enhanced values
                    .enhancedTitle(       text(n, "enhanced_title",           raw.getTitle()))
                    .enhancedBodyHtml(    text(n, "enhanced_body_html",       raw.getBodyHtml()))
                    .enhancedTags(        text(n, "enhanced_tags",            raw.getTags()))
                    .enhancedProductType( text(n, "enhanced_product_type",    raw.getProductType()))
                    .enhancedHandle(      text(n, "enhanced_handle",          raw.getHandle()))
                    .enhancedSeoTitle(    text(n, "enhanced_seo_title",       raw.getSeoTitle()))
                    .enhancedSeoDescription(text(n, "enhanced_seo_description", raw.getSeoDescription()))
                    .status("ENHANCED")
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse AI response JSON: {}", aiJson, e);
            throw new RuntimeException("Failed to parse AI response");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProductItem buildFailedItem(ShopifyProductDto raw) {
        return ProductItem.builder()
                .shopifyId(raw.getShopifyId())
                .title(raw.getTitle())
                .bodyHtml(raw.getBodyHtml())
                .vendor(raw.getVendor())
                .productType(raw.getProductType())
                .tags(raw.getTags())
                .handle(raw.getHandle())
                .seoTitle(raw.getSeoTitle())
                .seoDescription(raw.getSeoDescription())
                .status("FAILED")
                .build();
    }

    private String text(JsonNode node, String key, String fallback) {
        JsonNode v = node.path(key);
        String s = v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
        return s.isEmpty() ? (fallback != null ? fallback : "") : s;
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "(not provided)" : s.strip();
    }
}
