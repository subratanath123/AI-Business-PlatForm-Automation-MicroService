package net.ai.chatbot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.PromptTemplate;
import net.ai.chatbot.dto.admin.request.CreatePromptTemplateRequest;
import net.ai.chatbot.service.admin.PromptTemplateService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @GetMapping("/prompts/templates")
    public ResponseEntity<Map<String, Object>> getActiveTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String category) {
        try {
            List<PromptTemplate> templates = promptTemplateService.getAllTemplates(page, size, category, true);
            long total = promptTemplateService.countTemplates(category, true);

            Map<String, Object> response = new HashMap<>();
            response.put("templates", templates);
            response.put("total", total);
            response.put("page", page);
            response.put("pageSize", size);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching templates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/prompts/templates/categories")
    public ResponseEntity<List<String>> getCategories() {
        try {
            List<String> categories = promptTemplateService.getAllCategories();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            log.error("Error fetching categories", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/prompts/templates/popular")
    public ResponseEntity<List<PromptTemplate>> getPopularTemplates(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<PromptTemplate> templates = promptTemplateService.getPopularTemplates(limit);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            log.error("Error fetching popular templates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/prompts/templates/search")
    public ResponseEntity<List<PromptTemplate>> searchTemplates(
            @RequestParam String q) {
        try {
            List<PromptTemplate> templates = promptTemplateService.searchTemplates(q);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            log.error("Error searching templates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/prompts/templates/category/{category}")
    public ResponseEntity<List<PromptTemplate>> getTemplatesByCategory(@PathVariable String category) {
        try {
            List<PromptTemplate> templates = promptTemplateService.getTemplatesByCategory(category);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            log.error("Error fetching templates by category {}", category, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/prompts/templates/{templateId}")
    public ResponseEntity<PromptTemplate> getTemplate(@PathVariable String templateId) {
        try {
            PromptTemplate template = promptTemplateService.getTemplateById(templateId);
            return ResponseEntity.ok(template);
        } catch (Exception e) {
            log.error("Error fetching template {}", templateId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/prompts/templates/{templateId}/use")
    public ResponseEntity<Void> useTemplate(@PathVariable String templateId) {
        try {
            promptTemplateService.useTemplate(templateId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error recording template usage {}", templateId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/admin/prompts/templates")
    public ResponseEntity<PromptTemplate> createTemplate(
            @Valid @RequestBody CreatePromptTemplateRequest request) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            PromptTemplate template = promptTemplateService.createTemplate(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(template);
        } catch (Exception e) {
            log.error("Error creating template", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/admin/prompts/templates")
    public ResponseEntity<Map<String, Object>> getAllTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<PromptTemplate> templates = promptTemplateService.getAllTemplates(page, size, category, isActive);
            long total = promptTemplateService.countTemplates(category, isActive);

            Map<String, Object> response = new HashMap<>();
            response.put("templates", templates);
            response.put("total", total);
            response.put("page", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) total / size));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching all templates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/admin/prompts/templates/{templateId}")
    public ResponseEntity<PromptTemplate> updateTemplate(
            @PathVariable String templateId,
            @Valid @RequestBody CreatePromptTemplateRequest request) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            PromptTemplate template = promptTemplateService.updateTemplate(templateId, request);
            return ResponseEntity.ok(template);
        } catch (Exception e) {
            log.error("Error updating template {}", templateId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/admin/prompts/templates/{templateId}/active")
    public ResponseEntity<Void> setActive(
            @PathVariable String templateId,
            @RequestBody Map<String, Boolean> body) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            promptTemplateService.setActive(templateId, body.get("isActive"));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error updating template active status {}", templateId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/admin/prompts/templates/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            promptTemplateService.deleteTemplate(templateId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting template {}", templateId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
