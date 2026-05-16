package net.ai.chatbot.controller.aiproductphoto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.entity.AIProductPhotoJob;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.service.aiproductphoto.AIProductPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/ai-product-photo")
@RequiredArgsConstructor
@Slf4j
public class AIProductPhotoController {

    private final AIProductPhotoService productPhotoService;
    private final GuardrailService guardrailService;

    @PostMapping("/generate")
    public ResponseEntity<?> generateProductPhoto(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam("backgroundType") String backgroundType,
            @RequestParam(value = "customPrompt", required = false, defaultValue = "") String customPrompt,
            Authentication authentication
    ) {
        try {
            String userEmail = ((Jwt) authentication.getPrincipal()).getClaim("email");
            String userId = ((Jwt) authentication.getPrincipal()).getSubject();
            log.info("Generate product photo request from user: {}, backgroundType: {}", userEmail, backgroundType);

            // Guardrail validation for custom prompts
            if (customPrompt != null && !customPrompt.trim().isEmpty()) {
                GuardrailService.ValidationResult validationResult = guardrailService.validateImagePrompt(customPrompt, userId);
                if (!validationResult.isAllowed()) {
                    log.warn("Product photo prompt blocked by guardrails for user {}: {}", userEmail, validationResult.getMessage());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of(
                                "error", "Content policy violation",
                                "message", validationResult.getMessage(),
                                "violations", validationResult.getViolations()
                            ));
                }
            }

            if (imageFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Image file is required"));
            }

            String jobId = productPhotoService.createJob(userEmail, imageFile, backgroundType, customPrompt);

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "pending");
            response.put("message", "Product photo generation started");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Generate product photo failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getJobStatus(
            @PathVariable String jobId,
            Authentication authentication
    ) {
        try {
            String userEmail = ((Jwt) authentication.getPrincipal()).getClaim("email");

            AIProductPhotoJob job = productPhotoService.getJobStatus(jobId, userEmail);

            if (job == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("id", job.getId());
            response.put("status", job.getStatus());
            response.put("resultUrl", job.getResultUrl());
            response.put("assetId", job.getAssetId());
            response.put("error", job.getError());
            response.put("createdAt", job.getCreatedAt());
            response.put("completedAt", job.getCompletedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Get job status failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<?> listJobs(Authentication authentication) {
        try {
            String userEmail = ((Jwt) authentication.getPrincipal()).getClaim("email");

            List<AIProductPhotoJob> jobs = productPhotoService.listJobs(userEmail);

            return ResponseEntity.ok(jobs);

        } catch (Exception e) {
            log.error("List jobs failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
