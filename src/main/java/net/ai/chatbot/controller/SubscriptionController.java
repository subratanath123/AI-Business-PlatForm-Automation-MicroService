package net.ai.chatbot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.PromoCodeDao;
import net.ai.chatbot.dao.admin.SubscriptionPlanDao;
import net.ai.chatbot.dto.admin.PromoCode;
import net.ai.chatbot.dto.admin.SubscriptionPlan;
import net.ai.chatbot.service.subscription.PlanLimitValidator;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public and authenticated endpoints for subscription plans and promo code validation.
 */
@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPlanDao subscriptionPlanDao;
    private final PromoCodeDao promoCodeDao;
    private final PlanLimitValidator planLimitValidator;

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC ENDPOINTS (no auth required)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get all active subscription plans for display on pricing page.
     * This is a public endpoint - no authentication required.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getActivePlans() {
        try {
            log.info("Fetching active subscription plans");
            List<SubscriptionPlan> plans = subscriptionPlanDao.findAllActive();
            log.info("Found {} active plans", plans.size());
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            log.error("Error fetching active subscription plans", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get a specific plan by code.
     */
    @GetMapping("/plans/{planCode}")
    public ResponseEntity<SubscriptionPlan> getPlanByCode(@PathVariable String planCode) {
        return subscriptionPlanDao.findByPlanCode(planCode)
                .filter(SubscriptionPlan::isActive)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROMO CODE VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate a promo code for checkout.
     * Returns discount details if valid, error if not.
     */
    @PostMapping("/promo-codes/validate")
    public ResponseEntity<PromoValidationResponse> validatePromoCode(
            @RequestBody PromoValidationRequest request) {
        
        String userId = AuthUtils.getUserId();
        String code = request.getCode();
        String planId = request.getPlanId();
        BigDecimal originalAmount = request.getOriginalAmount();

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(
                PromoValidationResponse.invalid("Please enter a promo code")
            );
        }

        Optional<PromoCode> promoOpt = promoCodeDao.findByCode(code.toUpperCase().trim());
        
        if (promoOpt.isEmpty()) {
            return ResponseEntity.ok(
                PromoValidationResponse.invalid("Invalid promo code")
            );
        }

        PromoCode promo = promoOpt.get();

        // Check if active
        if (!promo.isActive()) {
            return ResponseEntity.ok(
                PromoValidationResponse.invalid("This promo code is no longer active")
            );
        }

        // Check validity period
        Instant now = Instant.now();
        if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom())) {
            return ResponseEntity.ok(
                PromoValidationResponse.invalid("This promo code is not yet active")
            );
        }
        if (promo.getValidUntil() != null && now.isAfter(promo.getValidUntil())) {
            return ResponseEntity.ok(
                PromoValidationResponse.invalid("This promo code has expired")
            );
        }

        // Check usage limits
        if (promo.getMaxUsageCount() != null && promo.getCurrentUsageCount() >= promo.getMaxUsageCount()) {
            return ResponseEntity.ok(
                PromoValidationResponse.invalid("This promo code has reached its usage limit")
            );
        }

        // Check per-user limit
        if (userId != null && promo.getMaxUsagePerUser() != null) {
            long userUsageCount = promo.getUsedByUserIds() != null 
                ? promo.getUsedByUserIds().stream().filter(id -> id.equals(userId)).count()
                : 0;
            if (userUsageCount >= promo.getMaxUsagePerUser()) {
                return ResponseEntity.ok(
                    PromoValidationResponse.invalid("You have already used this promo code")
                );
            }
        }

        // Check first-time only
        if (promo.isFirstTimeOnly() && userId != null) {
            // TODO: Check if user has any previous subscriptions
        }

        // Check plan applicability
        if (planId != null && promo.getApplicablePlanIds() != null && !promo.getApplicablePlanIds().isEmpty()) {
            if (!promo.getApplicablePlanIds().contains(planId)) {
                return ResponseEntity.ok(
                    PromoValidationResponse.invalid("This promo code is not valid for the selected plan")
                );
            }
        }

        if (planId != null && promo.getExcludedPlanIds() != null && promo.getExcludedPlanIds().contains(planId)) {
            return ResponseEntity.ok(
                PromoValidationResponse.invalid("This promo code cannot be used with the selected plan")
            );
        }

        // Check minimum purchase amount
        if (promo.getMinPurchaseAmount() != null && originalAmount != null) {
            if (originalAmount.compareTo(promo.getMinPurchaseAmount()) < 0) {
                return ResponseEntity.ok(
                    PromoValidationResponse.invalid(
                        String.format("Minimum purchase of $%.2f required for this promo code", 
                            promo.getMinPurchaseAmount())
                    )
                );
            }
        }

        // Calculate discount
        BigDecimal discountAmount = calculateDiscount(promo, originalAmount);
        BigDecimal finalAmount = originalAmount != null 
            ? originalAmount.subtract(discountAmount).max(BigDecimal.ZERO)
            : BigDecimal.ZERO;

        return ResponseEntity.ok(PromoValidationResponse.valid(
            promo.getCode(),
            promo.getName(),
            promo.getDiscountType().name(),
            promo.getDiscountValue(),
            discountAmount,
            finalAmount
        ));
    }

    private BigDecimal calculateDiscount(PromoCode promo, BigDecimal originalAmount) {
        if (originalAmount == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount;
        
        switch (promo.getDiscountType()) {
            case PERCENTAGE:
                discount = originalAmount
                    .multiply(promo.getDiscountValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                break;
            case FIXED_AMOUNT:
                discount = promo.getDiscountValue();
                break;
            case FREE_TRIAL_DAYS:
                discount = BigDecimal.ZERO;
                break;
            default:
                discount = BigDecimal.ZERO;
        }

        // Apply max discount cap
        if (promo.getMaxDiscountAmount() != null && discount.compareTo(promo.getMaxDiscountAmount()) > 0) {
            discount = promo.getMaxDiscountAmount();
        }

        return discount.min(originalAmount);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AUTHENTICATED ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get current user's usage summary and limits.
     */
    @GetMapping("/my-usage")
    public ResponseEntity<PlanLimitValidator.UsageSummary> getMyUsage() {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        PlanLimitValidator.UsageSummary summary = planLimitValidator.getUserUsageSummary(userId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Check if user can perform a specific action.
     */
    @GetMapping("/check-limit/{limitType}")
    public ResponseEntity<PlanLimitValidator.LimitCheckResult> checkLimit(
            @PathVariable String limitType,
            @RequestParam(required = false) String chatbotId) {
        
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        PlanLimitValidator.LimitCheckResult result = switch (limitType) {
            case "chatbot" -> planLimitValidator.canCreateChatbot(userId);
            case "message" -> planLimitValidator.canSendMessage(userId);
            case "knowledge-page" -> planLimitValidator.canAddKnowledgeBasePage(userId, chatbotId);
            case "social-account" -> planLimitValidator.canConnectSocialAccount(userId);
            case "post" -> planLimitValidator.canSchedulePost(userId);
            case "ai-content" -> planLimitValidator.canUseAiContentAssistant(userId);
            case "api" -> planLimitValidator.canUseApi(userId);
            case "branding" -> planLimitValidator.canUseCustomBranding(userId);
            default -> PlanLimitValidator.LimitCheckResult.denied(
                "Unknown limit type", 
                "The requested limit type is not recognized", 
                limitType, 0, 0
            );
        };

        return ResponseEntity.ok(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REQUEST/RESPONSE CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PromoValidationRequest {
        private String code;
        private String planId;
        private BigDecimal originalAmount;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PromoValidationResponse {
        private boolean valid;
        private String errorMessage;
        private String code;
        private String name;
        private String discountType;
        private BigDecimal discountValue;
        private BigDecimal discountAmount;
        private BigDecimal finalAmount;

        public static PromoValidationResponse invalid(String message) {
            return PromoValidationResponse.builder()
                    .valid(false)
                    .errorMessage(message)
                    .build();
        }

        public static PromoValidationResponse valid(String code, String name, String discountType,
                BigDecimal discountValue, BigDecimal discountAmount, BigDecimal finalAmount) {
            return PromoValidationResponse.builder()
                    .valid(true)
                    .code(code)
                    .name(name)
                    .discountType(discountType)
                    .discountValue(discountValue)
                    .discountAmount(discountAmount)
                    .finalAmount(finalAmount)
                    .build();
        }
    }
}
