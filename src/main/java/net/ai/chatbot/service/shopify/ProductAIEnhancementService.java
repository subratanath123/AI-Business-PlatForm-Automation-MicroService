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
