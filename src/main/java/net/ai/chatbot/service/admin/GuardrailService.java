package net.ai.chatbot.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.GuardrailDao;
import net.ai.chatbot.dto.admin.ContentViolation;
import net.ai.chatbot.dto.admin.GuardrailSettings;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for content moderation and guardrail enforcement.
 * Validates content before AI generation to prevent platform misuse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardrailService {

    private final GuardrailDao guardrailDao;

    // Precompiled patterns for common violations
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new HashMap<>();

    static {
        CATEGORY_KEYWORDS.put("VIOLENCE", Arrays.asList(
                "kill", "murder", "torture", "gore", "blood", "weapon", "bomb", "terrorist",
                "shoot", "stab", "massacre", "slaughter", "decapitate", "dismember"
        ));

        CATEGORY_KEYWORDS.put("ADULT", Arrays.asList(
                "nude", "naked", "porn", "xxx", "nsfw", "erotic", "sexual", "explicit",
                "fetish", "hentai", "lewd"
        ));

        CATEGORY_KEYWORDS.put("HATE_SPEECH", Arrays.asList(
                "racist", "racism", "nazi", "supremacist", "genocide", "ethnic cleansing",
                "slur", "bigot", "xenophob"
        ));

        CATEGORY_KEYWORDS.put("SELF_HARM", Arrays.asList(
                "suicide", "self-harm", "cut myself", "kill myself", "end my life",
                "overdose", "hang myself"
        ));

        CATEGORY_KEYWORDS.put("ILLEGAL", Arrays.asList(
                "drug deal", "cocaine", "heroin", "meth", "illegal weapon", "counterfeit",
                "money launder", "human traffic", "child abuse", "pedophil"
        ));

        CATEGORY_KEYWORDS.put("MALICIOUS_CODE", Arrays.asList(
                "ransomware", "keylogger", "trojan", "malware", "virus code", "exploit",
                "sql injection", "xss attack", "ddos", "hack into", "steal password",
                "bypass security", "crack software"
        ));

        CATEGORY_KEYWORDS.put("CELEBRITY", Arrays.asList(
                "deepfake", "fake video of", "nude photo of celebrity",
                "impersonate", "pretend to be"
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SETTINGS MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    public GuardrailSettings getSettings() {
        return guardrailDao.getSettings();
    }

    public GuardrailSettings updateSettings(GuardrailSettings settings) {
        settings.setUpdatedBy(AuthUtils.getEmail());
        log.info("Updating guardrail settings by {}", settings.getUpdatedBy());
        return guardrailDao.saveSettings(settings);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validates content against guardrail rules.
     * Returns a ValidationResult with success status and any violations found.
     */
    public ValidationResult validateContent(String content, String featureType, String userId) {
        GuardrailSettings settings = getSettings();

        // If guardrails disabled, allow all
        if (!settings.isEnabled()) {
            return ValidationResult.success();
        }

        // Check if user is in bypass list
        if (settings.getBypassUserIds().contains(userId)) {
            log.debug("User {} bypasses guardrails", userId);
            return ValidationResult.success();
        }

        // Check feature-specific settings
        if (!isFeatureEnabled(settings, featureType)) {
            return ValidationResult.blocked("This feature is currently disabled by the administrator.");
        }

        String contentLower = content.toLowerCase();
        List<String> violations = new ArrayList<>();
        List<String> triggeredKeywords = new ArrayList<>();

        // Check blocked categories based on settings
        if (settings.isBlockViolence()) {
            checkCategory(contentLower, "VIOLENCE", violations, triggeredKeywords);
        }
        if (settings.isBlockAdultContent()) {
            checkCategory(contentLower, "ADULT", violations, triggeredKeywords);
        }
        if (settings.isBlockHateSpeech()) {
            checkCategory(contentLower, "HATE_SPEECH", violations, triggeredKeywords);
        }
        if (settings.isBlockSelfHarm()) {
            checkCategory(contentLower, "SELF_HARM", violations, triggeredKeywords);
        }
        if (settings.isBlockIllegalContent()) {
            checkCategory(contentLower, "ILLEGAL", violations, triggeredKeywords);
        }
        if (settings.isBlockMaliciousCode() && "CODE".equals(featureType)) {
            checkCategory(contentLower, "MALICIOUS_CODE", violations, triggeredKeywords);
        }
        if (settings.isBlockCelebrityLikeness() && "IMAGE".equals(featureType)) {
            checkCategory(contentLower, "CELEBRITY", violations, triggeredKeywords);
        }

        // Check custom blocked keywords
        for (String keyword : settings.getBlockedKeywords()) {
            if (contentLower.contains(keyword.toLowerCase())) {
                violations.add("CUSTOM_KEYWORD");
                triggeredKeywords.add(keyword);
            }
        }

        // Check custom blocked phrases
        for (String phrase : settings.getBlockedPhrases()) {
            if (contentLower.contains(phrase.toLowerCase())) {
                violations.add("CUSTOM_PHRASE");
                triggeredKeywords.add(phrase);
            }
        }

        // Check custom regex patterns
        for (String pattern : settings.getBlockedPatterns()) {
            try {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(content).find()) {
                    violations.add("CUSTOM_PATTERN");
                    triggeredKeywords.add(pattern);
                }
            } catch (Exception e) {
                log.warn("Invalid regex pattern: {}", pattern);
            }
        }

        // Check allowed keywords (whitelist)
        if (!settings.getAllowedKeywords().isEmpty()) {
            boolean hasAllowedKeyword = settings.getAllowedKeywords().stream()
                    .anyMatch(k -> contentLower.contains(k.toLowerCase()));
            if (hasAllowedKeyword) {
                violations.clear();
                triggeredKeywords.clear();
            }
        }

        // If violations found, log and potentially block
        if (!violations.isEmpty()) {
            String primaryViolation = violations.get(0);
            String severity = determineSeverity(violations);

            // Log the violation
            if (settings.isLogViolations()) {
                logViolation(userId, primaryViolation, featureType, content, triggeredKeywords, severity, settings.getViolationAction());
            }

            // Check auto-block
            if (settings.isAutoBlockEnabled()) {
                long userViolationCount = guardrailDao.countUserViolationsInPeriod(userId, 24);
                if (userViolationCount >= settings.getAutoBlockThreshold()) {
                    log.warn("User {} has reached auto-block threshold ({} violations)", userId, userViolationCount);
                    // Could trigger user blocking here
                }
            }

            // Determine action based on settings
            if ("BLOCK".equals(settings.getViolationAction())) {
                return ValidationResult.blocked(
                        "Your content was blocked due to policy violations: " + String.join(", ", violations),
                        violations,
                        triggeredKeywords
                );
            } else if ("WARN".equals(settings.getViolationAction())) {
                return ValidationResult.warned(
                        "Warning: Your content may violate our policies.",
                        violations,
                        triggeredKeywords
                );
            }
        }

        return ValidationResult.success();
    }

    /**
     * Quick validation for image prompts with additional checks.
     */
    public ValidationResult validateImagePrompt(String prompt, String userId) {
        GuardrailSettings settings = getSettings();

        if (!settings.isImageGenerationEnabled()) {
            return ValidationResult.blocked("Image generation is currently disabled.");
        }

        ValidationResult baseResult = validateContent(prompt, "IMAGE", userId);
        if (!baseResult.isAllowed()) {
            return baseResult;
        }

        // Additional image-specific checks
        String promptLower = prompt.toLowerCase();

        if (settings.isBlockRealisticFaces()) {
            List<String> faceKeywords = Arrays.asList("realistic face", "photorealistic portrait", "real person", "actual photo");
            for (String keyword : faceKeywords) {
                if (promptLower.contains(keyword)) {
                    return ValidationResult.blocked("Generating realistic human faces is not allowed.");
                }
            }
        }

        // Check blocked image styles
        for (String style : settings.getBlockedImageStyles()) {
            if (promptLower.contains(style.toLowerCase())) {
                return ValidationResult.blocked("This image style is not allowed: " + style);
            }
        }

        return ValidationResult.success();
    }

    /**
     * Validation for video generation with duration checks.
     */
    public ValidationResult validateVideoRequest(String prompt, int durationSeconds, String userId) {
        GuardrailSettings settings = getSettings();

        if (!settings.isVideoGenerationEnabled()) {
            return ValidationResult.blocked("Video generation is currently disabled.");
        }

        if (durationSeconds > settings.getMaxVideoDurationSeconds()) {
            return ValidationResult.blocked(
                    "Video duration exceeds maximum allowed (" + settings.getMaxVideoDurationSeconds() + " seconds)."
            );
        }

        return validateContent(prompt, "VIDEO", userId);
    }

    /**
     * Validation for text/content generation.
     */
    public ValidationResult validateTextContent(String content, String userId) {
        GuardrailSettings settings = getSettings();

        if (!settings.isContentGenerationEnabled()) {
            return ValidationResult.blocked("Content generation is currently disabled.");
        }

        if (content.length() > settings.getMaxContentLength()) {
            return ValidationResult.blocked(
                    "Content length exceeds maximum allowed (" + settings.getMaxContentLength() + " characters)."
            );
        }

        return validateContent(content, "CONTENT", userId);
    }

    /**
     * Validation for code generation with security checks.
     */
    public ValidationResult validateCodeRequest(String prompt, String userId) {
        GuardrailSettings settings = getSettings();

        if (!settings.isCodeGenerationEnabled()) {
            return ValidationResult.blocked("Code generation is currently disabled.");
        }

        return validateContent(prompt, "CODE", userId);
    }

    /**
     * Validation for AI Photo Studio.
     */
    public ValidationResult validatePhotoStudio(String instruction, String userId) {
        GuardrailSettings settings = getSettings();

        if (!settings.isPhotoStudioEnabled()) {
            return ValidationResult.blocked("Photo Studio is currently disabled.");
        }

        if (instruction != null && !instruction.trim().isEmpty()) {
            return validateContent(instruction, "PHOTO_STUDIO", userId);
        }

        return ValidationResult.success();
    }

    /**
     * Validation for AI Product Studio.
     */
    public ValidationResult validateProductStudio(String userId) {
        GuardrailSettings settings = getSettings();

        if (!settings.isProductStudioEnabled()) {
            return ValidationResult.blocked("Product Studio is currently disabled.");
        }

        return ValidationResult.success();
    }

    /**
     * Validation for Face Swap feature.
     */
    public ValidationResult validateFaceSwap(String userId) {
        GuardrailSettings settings = getSettings();

        if (!settings.isFaceSwapEnabled()) {
            return ValidationResult.blocked("Face Swap is currently disabled.");
        }

        return ValidationResult.success();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RATE LIMITING
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean checkRateLimit(String userId, String featureType) {
        GuardrailSettings settings = getSettings();

        if (!settings.isRateLimitEnabled()) {
            return true;
        }

        // This is a simplified check - in production, use Redis for proper rate limiting
        // For now, we just check violation count as a proxy for request count
        long recentViolations = guardrailDao.countUserViolationsInPeriod(userId, 1);
        return recentViolations < settings.getMaxRequestsPerHour();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VIOLATION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    public List<ContentViolation> getRecentViolations(int page, int size) {
        return guardrailDao.findRecentViolations(page, size);
    }

    public List<ContentViolation> getUnreviewedViolations(int page, int size) {
        return guardrailDao.findUnreviewedViolations(page, size);
    }

    public List<ContentViolation> getUserViolations(String userId, int page, int size) {
        return guardrailDao.findViolationsByUserId(userId, page, size);
    }

    public void reviewViolation(String violationId, String notes, boolean isFalsePositive) {
        guardrailDao.markAsReviewed(violationId, AuthUtils.getEmail(), notes, isFalsePositive);
        log.info("Violation {} reviewed by {}, falsePositive={}", violationId, AuthUtils.getEmail(), isFalsePositive);
    }

    public Map<String, Object> getViolationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", guardrailDao.countViolations());
        stats.put("unreviewed", guardrailDao.countUnreviewedViolations());
        stats.put("today", guardrailDao.countViolationsToday());
        stats.put("thisWeek", guardrailDao.countViolationsThisWeek());
        stats.put("thisMonth", guardrailDao.countViolationsThisMonth());

        // Count by type
        Map<String, Long> byType = new HashMap<>();
        for (String type : Arrays.asList("VIOLENCE", "ADULT", "HATE_SPEECH", "SELF_HARM", "ILLEGAL", "CUSTOM_KEYWORD")) {
            byType.put(type, guardrailDao.countViolationsByType(type));
        }
        stats.put("byType", byType);

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isFeatureEnabled(GuardrailSettings settings, String featureType) {
        return switch (featureType) {
            case "IMAGE" -> settings.isImageGenerationEnabled();
            case "VIDEO" -> settings.isVideoGenerationEnabled();
            case "CONTENT" -> settings.isContentGenerationEnabled();
            case "CODE" -> settings.isCodeGenerationEnabled();
            case "PHOTO_STUDIO" -> settings.isPhotoStudioEnabled();
            case "PRODUCT_STUDIO" -> settings.isProductStudioEnabled();
            case "FACE_SWAP" -> settings.isFaceSwapEnabled();
            default -> true;
        };
    }

    private void checkCategory(String content, String category, List<String> violations, List<String> triggeredKeywords) {
        List<String> keywords = CATEGORY_KEYWORDS.get(category);
        if (keywords == null) return;

        for (String keyword : keywords) {
            if (content.contains(keyword.toLowerCase())) {
                if (!violations.contains(category)) {
                    violations.add(category);
                }
                triggeredKeywords.add(keyword);
            }
        }
    }

    private String determineSeverity(List<String> violations) {
        if (violations.contains("ILLEGAL") || violations.contains("SELF_HARM")) {
            return "CRITICAL";
        }
        if (violations.contains("VIOLENCE") || violations.contains("ADULT") || violations.contains("HATE_SPEECH")) {
            return "HIGH";
        }
        if (violations.size() > 1) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private void logViolation(String userId, String violationType, String featureType,
                              String content, List<String> triggeredKeywords,
                              String severity, String actionTaken) {
        ContentViolation violation = ContentViolation.builder()
                .userId(userId)
                .userEmail(AuthUtils.getEmail())
                .violationType(violationType)
                .featureType(featureType)
                .inputContent(content.length() > 500 ? content.substring(0, 500) + "..." : content)
                .triggeredKeywords(triggeredKeywords)
                .triggeredCategories(List.of(violationType))
                .actionTaken(actionTaken)
                .severity(severity)
                .build();

        guardrailDao.saveViolation(violation);
        log.warn("Content violation logged: type={}, severity={}, user={}", violationType, severity, userId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION RESULT CLASS
    // ═══════════════════════════════════════════════════════════════════════════

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private final boolean allowed;
        private final boolean warned;
        private final String message;
        private final List<String> violations;
        private final List<String> triggeredKeywords;

        public static ValidationResult success() {
            return new ValidationResult(true, false, null, List.of(), List.of());
        }

        public static ValidationResult blocked(String message) {
            return new ValidationResult(false, false, message, List.of(), List.of());
        }

        public static ValidationResult blocked(String message, List<String> violations, List<String> keywords) {
            return new ValidationResult(false, false, message, violations, keywords);
        }

        public static ValidationResult warned(String message, List<String> violations, List<String> keywords) {
            return new ValidationResult(true, true, message, violations, keywords);
        }
    }
}
