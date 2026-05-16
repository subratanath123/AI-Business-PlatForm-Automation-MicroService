package net.ai.chatbot.controller.aivideo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.entity.AIVideoJob;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.service.aivideo.AIVideoService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/ai-videos")
@RequiredArgsConstructor
@Slf4j
public class AIVideoController {

    private final AIVideoService videoService;
    private final GuardrailService guardrailService;

    /**
     * Generate a new AI video
     * 
     * POST /v1/api/ai-videos/generate
     * 
     * Request body:
     * {
     *   "prompt": "A time-lapse of a city skyline from day to night",
     *   "duration": 5,
     *   "aspectRatio": "16:9"
     * }
     * 
     * Response:
     * {
     *   "jobId": "uuid",
     *   "status": "pending"
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody Map<String, Object> request) {
        String userEmail = AuthUtils.getUserEmail();
        String userId = AuthUtils.getUserId();
        if (userEmail == null) {
            log.warn("Unauthorized video generation attempt");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String prompt = (String) request.get("prompt");
        Integer duration = request.get("duration") != null ? 
            ((Number) request.get("duration")).intValue() : 5;
        String aspectRatio = (String) request.getOrDefault("aspectRatio", "16:9");

        if (prompt == null || prompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Prompt is required"));
        }

        // Guardrail validation
        GuardrailService.ValidationResult validationResult = guardrailService.validateVideoRequest(prompt, duration, userId);
        if (!validationResult.isAllowed()) {
            log.warn("Video prompt blocked by guardrails for user {}: {}", userEmail, validationResult.getMessage());
            return ResponseEntity.status(403).body(Map.of(
                "error", "Content policy violation",
                "message", validationResult.getMessage(),
                "violations", validationResult.getViolations()
            ));
        }

        try {
            AIVideoJob job = videoService.createJob(userEmail, prompt, duration, aspectRatio);
            return ResponseEntity.ok(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus()
            ));
        } catch (Exception e) {
            log.error("Failed to create video generation job: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to create generation job",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get job status
     * 
     * GET /v1/api/ai-videos/status/{jobId}
     * 
     * Response:
     * {
     *   "id": "uuid",
     *   "status": "completed",
     *   "videoUrl": "https://...",
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
            AIVideoJob job = videoService.getJobStatus(userEmail, jobId);
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
     * GET /v1/api/ai-videos/jobs
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
            List<AIVideoJob> jobs = videoService.listJobs(userEmail);
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
