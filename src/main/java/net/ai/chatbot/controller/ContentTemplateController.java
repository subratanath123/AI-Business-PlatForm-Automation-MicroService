package net.ai.chatbot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.PromptTemplateDao;
import net.ai.chatbot.dto.admin.PromptTemplate;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public endpoints for content creation templates.
 * These templates are displayed in the Content Creation section for users.
 */
@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/content-templates")
@RequiredArgsConstructor
public class ContentTemplateController {

    private final PromptTemplateDao promptTemplateDao;

    /**
     * Get all active templates - public endpoint for the templates listing page.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getActiveTemplates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        try {
            List<PromptTemplate> templates;
            
            if (search != null && !search.isBlank()) {
                templates = promptTemplateDao.search(search);
            } else if (category != null && !category.isBlank() && !category.equals("All")) {
                templates = promptTemplateDao.findByCategory(category);
            } else {
                templates = promptTemplateDao.findAllActive();
            }

            List<String> categories = promptTemplateDao.findAllCategories();
            categories.add(0, "All");

            Map<String, Object> response = new HashMap<>();
            response.put("templates", templates);
            response.put("categories", categories);
            response.put("totalCount", templates.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching templates", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get a specific template by ID or code.
     */
    @GetMapping("/{idOrCode}")
    public ResponseEntity<PromptTemplate> getTemplate(@PathVariable String idOrCode) {
        try {
            // Try by code first
            PromptTemplate template = promptTemplateDao.findByTemplateCode(idOrCode.toUpperCase())
                    .orElse(null);
            
            // If not found, try by ID
            if (template == null) {
                template = promptTemplateDao.findById(idOrCode).orElse(null);
            }

            if (template == null || !template.isActive()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(template);
        } catch (Exception e) {
            log.error("Error fetching template {}", idOrCode, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get popular templates.
     */
    @GetMapping("/popular")
    public ResponseEntity<List<PromptTemplate>> getPopularTemplates(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<PromptTemplate> templates = promptTemplateDao.findPopular(limit);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            log.error("Error fetching popular templates", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Increment usage count when a template is used.
     * Requires authentication.
     */
    @PostMapping("/{templateId}/use")
    public ResponseEntity<Void> useTemplate(@PathVariable String templateId) {
        try {
            String userId = AuthUtils.getUserId();
            if (userId == null) {
                return ResponseEntity.status(401).build();
            }

            promptTemplateDao.incrementUsageCount(templateId);
            log.info("Template {} used by user {}", templateId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error recording template usage {}", templateId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
