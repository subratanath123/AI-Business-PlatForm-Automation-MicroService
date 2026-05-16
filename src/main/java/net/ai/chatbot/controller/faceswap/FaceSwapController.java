package net.ai.chatbot.controller.faceswap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.entity.FaceSwapJob;
import net.ai.chatbot.service.admin.GuardrailService;
import net.ai.chatbot.service.faceswap.FaceSwapService;
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
@RequestMapping("/v1/api/face-swap")
@RequiredArgsConstructor
@Slf4j
public class FaceSwapController {

    private final FaceSwapService faceSwapService;
    private final GuardrailService guardrailService;

    @PostMapping("/swap")
    public ResponseEntity<?> swapFaces(
            @RequestParam("sourceFace") MultipartFile sourceFace,
            @RequestParam("targetImage") MultipartFile targetImage,
            Authentication authentication
    ) {
        try {
            String userEmail = ((Jwt) authentication.getPrincipal()).getClaim("email");
            String userId = ((Jwt) authentication.getPrincipal()).getSubject();
            log.info("Face swap request from user: {}", userEmail);

            // Guardrail validation
            GuardrailService.ValidationResult validationResult = guardrailService.validateFaceSwap(userId);
            if (!validationResult.isAllowed()) {
                log.warn("Face swap blocked by guardrails for user {}: {}", userEmail, validationResult.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                            "error", "Feature disabled",
                            "message", validationResult.getMessage()
                        ));
            }

            if (sourceFace.isEmpty() || targetImage.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Both source and target images are required"));
            }

            String jobId = faceSwapService.createJob(userEmail, sourceFace, targetImage);

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("status", "pending");
            response.put("message", "Face swap started");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Face swap failed", e);
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

            FaceSwapJob job = faceSwapService.getJobStatus(jobId, userEmail);

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

            List<FaceSwapJob> jobs = faceSwapService.listJobs(userEmail);

            return ResponseEntity.ok(jobs);

        } catch (Exception e) {
            log.error("List jobs failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
