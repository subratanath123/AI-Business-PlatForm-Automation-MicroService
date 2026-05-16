package net.ai.chatbot.controller.aiproductstudio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.entity.AIProductStudioJob;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.service.aiproductstudio.AIProductStudioService;
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
@RequestMapping("/v1/api/ai-product-studio")
@RequiredArgsConstructor
@Slf4j
public class AIProductStudioController {

    private final AIProductStudioService productStudioService;
    private final GuardrailService guardrailService;

    @PostMapping("/generate")
    public ResponseEntity<?> generateStudioShot(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam("sceneType") String sceneType,
            @RequestParam("lighting") String lighting,
            @RequestParam("angle") String angle,
            Authentication authentication
    ) {
        try {
            String userEmail = ((Jwt) authentication.getPrincipal()).getClaim("email");
            String userId = ((Jwt) authentication.getPrincipal()).getSubject();
            log.info("Generate studio shot request from user: {}, scene: {}, lighting: {}, angle: {}", 
                     userEmail, sceneType, lighting, angle);

            // Guardrail validation
            GuardrailService.ValidationResult validationResult = guardrailService.validateProductStudio(userId);
            if (!validationResult.isAllowed()) {
                log.warn("Product studio blocked by guardrails for user {}: {}", userEmail, validationResult.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                            "error", "Feature disabled",
                            "message", validationResult.getMessage()
                        ));
            }

            if (imageFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Image file is required"));
            }

            String jobId = productStudioService.createJob(userEmail, imageFile, sceneType, lighting, angle);

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "pending");
            response.put("message", "Studio shot generation started");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Generate studio shot failed", e);
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

            AIProductStudioJob job = productStudioService.getJobStatus(jobId, userEmail);

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

            List<AIProductStudioJob> jobs = productStudioService.listJobs(userEmail);

            return ResponseEntity.ok(jobs);

        } catch (Exception e) {
            log.error("List jobs failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
