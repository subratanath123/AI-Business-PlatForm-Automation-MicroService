package net.ai.chatbot.controller.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.UserSubscription;
import net.ai.chatbot.service.payment.StripePaymentService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for payment-related operations.
 *
 * <p>POST /v1/api/payments/stripe/checkout  — creates a Stripe Checkout Session (requires auth)
 * <p>GET  /v1/api/payments/stripe/config    — returns publishable key (public)
 */
@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final StripePaymentService stripePaymentService;

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the Stripe publishable key so the frontend can initialise Stripe.js if needed.
     */
    @GetMapping("/stripe/config")
    public ResponseEntity<Map<String, String>> stripeConfig() {
        return ResponseEntity.ok(Map.of("publishableKey", stripePaymentService.getPublishableKey()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AUTHENTICATED
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a Stripe Checkout Session and returns the hosted checkout URL.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "planId":    "mongo-object-id",
     *   "isAnnual":  false,
     *   "promoCode": "OPTIONAL"
     * }
     * </pre>
     *
     * <p>Response:
     * <pre>{ "checkoutUrl": "https://checkout.stripe.com/..." }</pre>
     */
    @PostMapping("/stripe/checkout")
    public ResponseEntity<?> createCheckoutSession(@RequestBody CheckoutRequest request) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        String userEmail = AuthUtils.getUserEmail();

        try {
            String url = stripePaymentService.createCheckoutSession(
                userId,
                userEmail != null ? userEmail : "",
                request.getPlanId(),
                Boolean.TRUE.equals(request.getIsAnnual()),
                request.getPromoCode()
            );
            return ResponseEntity.ok(Map.of("checkoutUrl", url));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Checkout validation error for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating Stripe checkout session for user {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to create checkout session. Please try again."));
        }
    }

    /**
     * Verifies a completed Stripe Checkout Session and activates the subscription.
     * Call from the success page when {@code session_id} is in the URL (webhook fallback).
     */
    @PostMapping("/stripe/verify-session")
    public ResponseEntity<?> verifyCheckoutSession(@RequestBody VerifySessionRequest request) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId is required"));
        }

        try {
            UserSubscription sub = stripePaymentService.verifyCheckoutSession(request.getSessionId().trim(), userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "planName", sub.getPlanName() != null ? sub.getPlanName() : "",
                    "planCode", sub.getPlanCode() != null ? sub.getPlanCode() : "",
                    "status", sub.getStatus() != null ? sub.getStatus().name() : "ACTIVE"
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Verify session failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error verifying checkout session for user {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to verify payment. Please contact support."));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FREE PLAN ACTIVATION (no payment required)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Activates a free plan directly without going through Stripe.
     */
    @PostMapping("/free/activate")
    public ResponseEntity<?> activateFreePlan(@RequestBody FreePlanRequest request) {
        String userId = AuthUtils.getUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        String userEmail = AuthUtils.getUserEmail();

        try {
            stripePaymentService.activateFreePlanForUser(
                userId,
                userEmail != null ? userEmail : "",
                request.getPlanId()
            );
            return ResponseEntity.ok(Map.of("success", true, "message", "Free plan activated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error activating free plan for user {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to activate plan. Please try again."));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DTO
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CheckoutRequest {
        private String planId;
        private Boolean isAnnual;
        private String promoCode;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VerifySessionRequest {
        private String sessionId;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FreePlanRequest {
        private String planId;
    }
}
