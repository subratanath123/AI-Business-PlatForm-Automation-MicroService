package net.ai.chatbot.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Guardrail settings for content moderation across AI generation features.
 * Controls what types of content can be generated to prevent platform misuse.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "guardrail_settings")
public class GuardrailSettings {

    @Id
    private String id;

    // ═══════════════════════════════════════════════════════════════════════════
    // GLOBAL SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    @Field("isEnabled")
    @JsonProperty("isEnabled")
    @Builder.Default
    private boolean isEnabled = true;

    // Moderation strictness level: LOW, MEDIUM, HIGH, STRICT
    @Builder.Default
    private String strictnessLevel = "MEDIUM";

    // Action to take when violation detected: BLOCK, WARN, LOG_ONLY
    @Builder.Default
    private String violationAction = "BLOCK";

    // ═══════════════════════════════════════════════════════════════════════════
    // BLOCKED CONTENT CATEGORIES
    // ═══════════════════════════════════════════════════════════════════════════

    @Field("blockViolence")
    @JsonProperty("blockViolence")
    @Builder.Default
    private boolean blockViolence = true;

    @Field("blockAdultContent")
    @JsonProperty("blockAdultContent")
    @Builder.Default
    private boolean blockAdultContent = true;

    @Field("blockHateSpeech")
    @JsonProperty("blockHateSpeech")
    @Builder.Default
    private boolean blockHateSpeech = true;

    @Field("blockSelfHarm")
    @JsonProperty("blockSelfHarm")
    @Builder.Default
    private boolean blockSelfHarm = true;

    @Field("blockIllegalContent")
    @JsonProperty("blockIllegalContent")
    @Builder.Default
    private boolean blockIllegalContent = true;

    @Field("blockPII")
    @JsonProperty("blockPII")
    @Builder.Default
    private boolean blockPII = true;

    @Field("blockMisinformation")
    @JsonProperty("blockMisinformation")
    @Builder.Default
    private boolean blockMisinformation = false;

    @Field("blockPoliticalContent")
    @JsonProperty("blockPoliticalContent")
    @Builder.Default
    private boolean blockPoliticalContent = false;

    @Field("blockReligiousContent")
    @JsonProperty("blockReligiousContent")
    @Builder.Default
    private boolean blockReligiousContent = false;

    @Field("blockCopyrightedContent")
    @JsonProperty("blockCopyrightedContent")
    @Builder.Default
    private boolean blockCopyrightedContent = true;

    // ═══════════════════════════════════════════════════════════════════════════
    // CUSTOM BLOCKED KEYWORDS & PHRASES
    // ═══════════════════════════════════════════════════════════════════════════

    @Builder.Default
    private List<String> blockedKeywords = new ArrayList<>();

    @Builder.Default
    private List<String> blockedPhrases = new ArrayList<>();

    // Regex patterns for advanced filtering
    @Builder.Default
    private List<String> blockedPatterns = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // ALLOWED/WHITELISTED CONTENT
    // ═══════════════════════════════════════════════════════════════════════════

    @Builder.Default
    private List<String> allowedKeywords = new ArrayList<>();

    // User IDs or emails that bypass guardrails (admin, trusted users)
    @Builder.Default
    private List<String> bypassUserIds = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // FEATURE-SPECIFIC SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    // AI Image Generation
    @Field("imageGenerationEnabled")
    @JsonProperty("imageGenerationEnabled")
    @Builder.Default
    private boolean imageGenerationEnabled = true;

    @Builder.Default
    private List<String> blockedImageStyles = new ArrayList<>();

    @Field("blockRealisticFaces")
    @JsonProperty("blockRealisticFaces")
    @Builder.Default
    private boolean blockRealisticFaces = false;

    @Field("blockCelebrityLikeness")
    @JsonProperty("blockCelebrityLikeness")
    @Builder.Default
    private boolean blockCelebrityLikeness = true;

    // AI Video Generation
    @Field("videoGenerationEnabled")
    @JsonProperty("videoGenerationEnabled")
    @Builder.Default
    private boolean videoGenerationEnabled = true;

    @Field("maxVideoDurationSeconds")
    @JsonProperty("maxVideoDurationSeconds")
    @Builder.Default
    private int maxVideoDurationSeconds = 60;

    // AI Photo Studio
    @Field("photoStudioEnabled")
    @JsonProperty("photoStudioEnabled")
    @Builder.Default
    private boolean photoStudioEnabled = true;

    // AI Product Studio
    @Field("productStudioEnabled")
    @JsonProperty("productStudioEnabled")
    @Builder.Default
    private boolean productStudioEnabled = true;

    // AI Face Swap
    @Field("faceSwapEnabled")
    @JsonProperty("faceSwapEnabled")
    @Builder.Default
    private boolean faceSwapEnabled = true;

    // AI Content/Text Generation
    @Field("contentGenerationEnabled")
    @JsonProperty("contentGenerationEnabled")
    @Builder.Default
    private boolean contentGenerationEnabled = true;

    @Field("maxContentLength")
    @JsonProperty("maxContentLength")
    @Builder.Default
    private int maxContentLength = 10000;

    // AI Code Generation
    @Field("codeGenerationEnabled")
    @JsonProperty("codeGenerationEnabled")
    @Builder.Default
    private boolean codeGenerationEnabled = true;

    @Field("blockMaliciousCode")
    @JsonProperty("blockMaliciousCode")
    @Builder.Default
    private boolean blockMaliciousCode = true;

    // ═══════════════════════════════════════════════════════════════════════════
    // RATE LIMITING
    // ═══════════════════════════════════════════════════════════════════════════

    @Field("rateLimitEnabled")
    @JsonProperty("rateLimitEnabled")
    @Builder.Default
    private boolean rateLimitEnabled = true;

    // Max requests per user per hour
    @Builder.Default
    private int maxRequestsPerHour = 100;

    // Max requests per user per day
    @Builder.Default
    private int maxRequestsPerDay = 500;

    // ═══════════════════════════════════════════════════════════════════════════
    // LOGGING & ALERTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Field("logViolations")
    @JsonProperty("logViolations")
    @Builder.Default
    private boolean logViolations = true;

    @Field("alertOnViolation")
    @JsonProperty("alertOnViolation")
    @Builder.Default
    private boolean alertOnViolation = true;

    // Email addresses to notify on violations
    @Builder.Default
    private List<String> alertEmails = new ArrayList<>();

    // Number of violations before alerting
    @Builder.Default
    private int alertThreshold = 5;

    // ═══════════════════════════════════════════════════════════════════════════
    // USER BLOCKING
    // ═══════════════════════════════════════════════════════════════════════════

    @Field("autoBlockEnabled")
    @JsonProperty("autoBlockEnabled")
    @Builder.Default
    private boolean autoBlockEnabled = true;

    // Number of violations before auto-blocking a user
    @Builder.Default
    private int autoBlockThreshold = 10;

    // Auto-block duration in hours (0 = permanent)
    @Builder.Default
    private int autoBlockDurationHours = 24;

    // ═══════════════════════════════════════════════════════════════════════════
    // METADATA
    // ═══════════════════════════════════════════════════════════════════════════

    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;
}
