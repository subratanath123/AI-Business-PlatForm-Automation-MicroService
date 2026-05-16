package net.ai.chatbot.controller.aiimage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.entity.AIImageJob;
import net.ai.chatbot.service.aiimage.AIImageService;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/ai-images")
@RequiredArgsConstructor
@Slf4j
public class AIImageController {

    private final AIImageService imageService;
    private final GuardrailService guardrailService;

    /**
     * Generate a new AI image
     * 
     * POST /v1/api/ai-images/generate
     * 
     * Request body:
     * {
     *   "prompt": "A beautiful sunset over mountains",
     *   "size": "1024x1024",
     *   "quality": "standard",
     *   "style": "vivid"
     * }
     * 
     * Response:
     * {
     *   "jobId": "uuid",
     *   "status": "pending"
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody Map<String, String> request) {
        String userEmail = AuthUtils.getUserEmail();
        String userId = AuthUtils.getUserId();
        if (userEmail == null) {
            log.warn("Unauthorized image generation attempt");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String prompt = request.get("prompt");
        String size = request.getOrDefault("size", "1024x1024");
        String quality = request.getOrDefault("quality", "standard");
        String style = request.getOrDefault("style", "vivid");

        if (prompt == null || prompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Prompt is required"));
        }

        // Guardrail validation
        GuardrailService.ValidationResult validationResult = guardrailService.validateImagePrompt(prompt, userId);
        if (!validationResult.isAllowed()) {
            log.warn("Image prompt blocked by guardrails for user {}: {}", userEmail, validationResult.getMessage());
            return ResponseEntity.status(403).body(Map.of(
                "error", "Content policy violation",
                "message", validationResult.getMessage(),
                "violations", validationResult.getViolations()
            ));
        }

        if (validationResult.isWarned()) {
            log.info("Image prompt warning for user {}: {}", userEmail, validationResult.getMessage());
        }

        try {
            AIImageJob job = imageService.createJob(userEmail, prompt, size, quality, style);
            return ResponseEntity.ok(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus()
            ));
        } catch (Exception e) {
            log.error("Failed to create image generation job: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to create generation job",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get job status
     * 
     * GET /v1/api/ai-images/status/{jobId}
     * 
     * Response:
     * {
     *   "id": "uuid",
     *   "status": "completed",
     *   "imageUrl": "https://...",
     *   "assetId": "uuid"
     * }
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getStatus(@PathVariable String jobId) {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized status check attempt");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            AIImageJob job = imageService.getJobStatus(userEmail, jobId);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", "Job not found"));
        } catch (Exception e) {
            log.error("Failed to get job status: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get status",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * List all jobs for user
     * 
     * GET /v1/api/ai-images/jobs
     * 
     * Response:
     * [
     *   { "id": "uuid", "prompt": "...", "status": "completed", ... },
     *   ...
     * ]
     */
    @GetMapping("/jobs")
    public ResponseEntity<?> listJobs() {
        String userEmail = AuthUtils.getUserEmail();
        if (userEmail == null) {
            log.warn("Unauthorized jobs list attempt");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            List<AIImageJob> jobs = imageService.listJobs(userEmail);
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            log.error("Failed to list jobs: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to list jobs",
                "message", e.getMessage()
            ));
        }
    }
}
