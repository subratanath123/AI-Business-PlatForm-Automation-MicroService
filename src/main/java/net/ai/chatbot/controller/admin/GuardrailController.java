package net.ai.chatbot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.ContentViolation;
import net.ai.chatbot.dto.admin.GuardrailSettings;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for managing content moderation guardrails.
 */
@Slf4j
@RestController
@RequestMapping("/v1/api/admin/guardrails")
@RequiredArgsConstructor
public class GuardrailController {

    private final GuardrailService guardrailService;

    // ═══════════════════════════════════════════════════════════════════════════
    // SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get current guardrail settings.
     */
    @GetMapping("/settings")
    public ResponseEntity<GuardrailSettings> getSettings() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            GuardrailSettings settings = guardrailService.getSettings();
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.error("Error fetching guardrail settings", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update guardrail settings.
     */
    @PutMapping("/settings")
    public ResponseEntity<GuardrailSettings> updateSettings(@RequestBody GuardrailSettings settings) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            GuardrailSettings updated = guardrailService.updateSettings(settings);
            log.info("Guardrail settings updated by {}", AuthUtils.getEmail());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error updating guardrail settings", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VIOLATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get violation statistics.
     */
    @GetMapping("/violations/stats")
    public ResponseEntity<Map<String, Object>> getViolationStats() {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Map<String, Object> stats = guardrailService.getViolationStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching violation stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get recent violations.
     */
    @GetMapping("/violations")
    public ResponseEntity<Map<String, Object>> getViolations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean unreviewed) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<ContentViolation> violations;
            if (Boolean.TRUE.equals(unreviewed)) {
                violations = guardrailService.getUnreviewedViolations(page, size);
            } else {
                violations = guardrailService.getRecentViolations(page, size);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("violations", violations);
            response.put("page", page);
            response.put("size", size);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching violations", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get violations for a specific user.
     */
    @GetMapping("/violations/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserViolations(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<ContentViolation> violations = guardrailService.getUserViolations(userId, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("violations", violations);
            response.put("userId", userId);
            response.put("page", page);
            response.put("size", size);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching user violations", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Review a violation (mark as reviewed, optionally mark as false positive).
     */
    @PostMapping("/violations/{violationId}/review")
    public ResponseEntity<Void> reviewViolation(
            @PathVariable String violationId,
            @RequestBody ReviewRequest request) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            guardrailService.reviewViolation(violationId, request.notes, request.falsePositive);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error reviewing violation", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST ENDPOINT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Test content against guardrails without logging (for admin testing).
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testContent(@RequestBody TestRequest request) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            GuardrailService.ValidationResult result = guardrailService.validateContent(
                    request.content,
                    request.featureType != null ? request.featureType : "CONTENT",
                    "test-user"
            );

            Map<String, Object> response = new HashMap<>();
            response.put("allowed", result.isAllowed());
            response.put("warned", result.isWarned());
            response.put("message", result.getMessage());
            response.put("violations", result.getViolations());
            response.put("triggeredKeywords", result.getTriggeredKeywords());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error testing content", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REQUEST CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    public static class ReviewRequest {
        public String notes;
        public boolean falsePositive;
    }

    public static class TestRequest {
        public String content;
        public String featureType;
    }
}
