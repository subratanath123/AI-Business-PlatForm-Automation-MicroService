package net.ai.chatbot.service.aiimage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.AIImageJobDao;
import net.ai.chatbot.dao.MediaAssetDao;
import net.ai.chatbot.entity.AIImageJob;
import net.ai.chatbot.entity.MediaAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import net.ai.chatbot.service.storage.LocalUserStorageService;
import net.ai.chatbot.service.storage.SupabaseStorageService;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIImageService {

    private final AIImageJobDao jobDao;
    private final MediaAssetDao assetDao;
    private final SupabaseStorageService supabaseStorage;
    private final LocalUserStorageService localUserStorage;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/images/generations";

    /**
     * Create a new image generation job
     */
    public AIImageJob createJob(String userEmail, String userId, String prompt, String size, String quality, String style) {
        AIImageJob job = AIImageJob.builder()
                .id(UUID.randomUUID().toString())
                .userEmail(userEmail)
                .userId(userId)
                .prompt(prompt)
                .size(size)
                .quality(quality)
                .style(style)
                .status("pending")
                .createdAt(Instant.now())
                .build();

        jobDao.save(job);
        log.info("Created image generation job {} for user {}", job.getId(), userEmail);

        // Start async generation
        generateImageAsync(job.getId());

        return job;
    }

    /**
     * Generate image asynchronously
     */
    @Async
    public void generateImageAsync(String jobId) {
        AIImageJob job = jobDao.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Job {} not found", jobId);
            return;
        }

        try {
            // Update status to processing
            job.setStatus("processing");
            jobDao.save(job);
            log.info("Starting image generation for job {}", jobId);

            // Call OpenAI API
            GeneratedImage image = callOpenAI(job.getUserEmail(), job.getUserId(), job.getPrompt(), job.getSize(), job.getQuality(), job.getStyle());

            // Save image URL to assets
            String assetId = saveToAssets(job.getUserEmail(), job.getUserId(), image);

            // Update job as completed
            job.setStatus("completed");
            job.setImageUrl(image.displayUrl());
            job.setAssetId(assetId);
            job.setCompletedAt(Instant.now());
            jobDao.save(job);

            log.info("Completed image generation for job {}", jobId);

        } catch (Exception e) {
            log.error("Failed to generate image for job {}: {}", jobId, e.getMessage(), e);
            job.setStatus("failed");
            job.setError(e.getMessage());
            jobDao.save(job);
        }
    }

    /**
     * Call the OpenAI image generation API.
     * Current GPT image models reject the legacy {@code style} field and do not accept {@code dall-e-3}.
     */
    private GeneratedImage callOpenAI(String userEmail, String userId, String prompt, String size, String quality, String style) throws Exception {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        String mappedSize = mapSize(size);
        String mappedQuality = mapQuality(quality);
        String requestPrompt = promptWithStyleHint(prompt, style);

        log.info("Calling OpenAI API - prompt: '{}', size: {}, quality: {}",
                 requestPrompt.substring(0, Math.min(50, requestPrompt.length())), mappedSize, mappedQuality);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openaiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-image-1");
        requestBody.put("prompt", requestPrompt);
        requestBody.put("n", 1);
        requestBody.put("size", mappedSize);
        requestBody.put("quality", mappedQuality);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                OPENAI_API_URL,
                HttpMethod.POST,
                request,
                Map.class
            );

            log.info("OpenAI response status: {}", response.getStatusCode());

            if (response.getBody() == null) {
                throw new Exception("Empty response from OpenAI");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            if (data == null || data.isEmpty()) {
                throw new Exception("No image returned from OpenAI");
            }

            String imageUrl = (String) data.get(0).get("url");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                log.info("Successfully generated image URL");
                String filename = filenameFor(prompt);
                return new GeneratedImage(imageUrl, "ai-generated/" + userEmail + "/" + filename, filename, 0L);
            }

            String b64 = (String) data.get(0).get("b64_json");
            if (b64 == null || b64.isEmpty()) {
                throw new Exception("Invalid image data from OpenAI");
            }

            byte[] pngBytes = Base64.getDecoder().decode(b64);
            log.info("Successfully generated image ({} bytes)", pngBytes.length);
            return storeGeneratedPng(userEmail, userId, prompt, pngBytes);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Generated image could not be stored")) {
                throw e;
            }
            log.error("OpenAI API call failed: {} - {}", e.getClass().getName(), e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("421")) {
                throw new Exception("OpenAI API endpoint error. Please verify API key and endpoint configuration.");
            }
            throw new Exception("OpenAI generation failed: " + e.getMessage());
        }
    }

    /**
     * GPT image models do not accept {@code style}. Keep the UI choice by describing it in the prompt.
     */
    private static String promptWithStyleHint(String prompt, String style) {
        if (style == null || style.isBlank()) {
            return prompt;
        }
        if ("natural".equalsIgnoreCase(style)) {
            return prompt + ", natural realistic style";
        }
        if ("vivid".equalsIgnoreCase(style)) {
            return prompt + ", vivid dramatic colors";
        }
        return prompt;
    }

    private static String mapQuality(String quality) {
        if (quality == null || quality.isBlank()) {
            return "medium";
        }
        return switch (quality.toLowerCase()) {
            case "hd", "high" -> "high";
            case "low" -> "low";
            case "auto" -> "auto";
            default -> "medium";
        };
    }

    private static String mapSize(String size) {
        if (size == null || size.isBlank()) {
            return "1024x1024";
        }
        return switch (size) {
            case "1024x1024", "1024x1536", "1536x1024", "auto" -> size;
            case "1792x1024" -> "1536x1024";
            case "1024x1792" -> "1024x1536";
            default -> "1024x1024";
        };
    }

    /**
     * Always keep a per-user local copy. Supabase upload is best-effort: a DNS or
     * storage failure does not discard the OpenAI image.
     */
    private GeneratedImage storeGeneratedPng(String userEmail, String userId, String prompt, byte[] pngBytes) throws Exception {
        String filename = filenameFor(prompt);
        LocalUserStorageService.StoredFile local;
        try {
            local = localUserStorage.saveImage(userId, userEmail, pngBytes, "png");
        } catch (Exception e) {
            throw new Exception("Generated image could not be stored", e);
        }

        String remoteFolder = local.userFolder();
        String objectPath = "ai-generated/" + remoteFolder + "/" + filename;
        try {
            supabaseStorage.upload(objectPath, pngBytes, "image/png");
            log.info("Uploaded generated image to Supabase and kept local copy in folder {}", remoteFolder);
        } catch (Exception storageFailure) {
            log.warn(
                    "Remote Supabase storage was skipped ({}); local copy kept in folder {}",
                    storageFailure.getMessage(),
                    remoteFolder);
        }
        return new GeneratedImage(local.publicUrl(), local.relativePath(), filename, pngBytes.length);
    }

    private static String filenameFor(String prompt) {
        String safe = prompt == null ? "image" : prompt;
        return safe.substring(0, Math.min(50, safe.length()))
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", "-")
                .toLowerCase() + "-" + System.currentTimeMillis() + ".png";
    }

    /**
     * Save image URL and metadata to MongoDB.
     */
    private String saveToAssets(String userEmail, String userId, GeneratedImage image) throws Exception {
        try {
            log.info("Saving AI generated image URL to database for user {}", userEmail);

            MediaAsset asset = MediaAsset.builder()
                    .id(UUID.randomUUID().toString())
                    .userEmail(userEmail)
                    .userId(userId)
                    .fileName(image.fileName())
                    .mimeType("image/png")
                    .sizeBytes(image.sizeBytes())
                    .supabaseUrl(image.displayUrl())
                    .objectPath(image.objectPath())
                    .createdAt(Instant.now())
                    .tags(new ArrayList<>(List.of("ai-generated", "gpt-image")))
                    .folderPath("AI Generated")
                    .build();

            assetDao.save(asset);
            log.info("Saved AI generated image metadata as asset {} for user {}", asset.getId(), userEmail);

            return asset.getId();

        } catch (Exception e) {
            log.error("Failed to save image metadata: {}", e.getMessage(), e);
            throw new Exception("Failed to save image metadata: " + e.getMessage());
        }
    }

    /**
     * Get job status
     */
    public AIImageJob getJobStatus(String userEmail, String jobId) {
        return jobDao.findByIdAndUserEmail(jobId, userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }

    /**
     * List all jobs for user
     */
    public List<AIImageJob> listJobs(String userEmail) {
        return jobDao.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }

    private record GeneratedImage(String displayUrl, String objectPath, String fileName, long sizeBytes) {}
}
