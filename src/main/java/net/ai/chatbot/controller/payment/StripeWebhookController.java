package net.ai.chatbot.controller.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.service.payment.StripePaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoint that receives webhook events directly from Stripe.
 *
 * <p>This endpoint MUST be excluded from CSRF protection and authentication.
 * Stripe signs every request with its webhook secret; we verify the signature
 * inside {@link StripePaymentService#handleWebhook}.
 *
 * <p>Register this URL in the Stripe Dashboard →  Developers → Webhooks:
 * <pre>https://api.jadeordersmedia.com/v1/api/payments/stripe/webhook</pre>
 *
 * <p>Enable at least these events:
 * <ul>
 *   <li>checkout.session.completed</li>
 *   <li>customer.subscription.deleted</li>
 *   <li>customer.subscription.updated</li>
 *   <li>invoice.payment_failed</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/v1/api/payments/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripePaymentService stripePaymentService;

    /**
     * Receives raw webhook payload from Stripe and routes to the appropriate handler.
     *
     * @param payload   raw request body (must NOT be parsed before this point)
     * @param sigHeader value of the {@code Stripe-Signature} HTTP header
     */
    @PostMapping(value = "/webhook", consumes = "application/json")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (sigHeader == null || sigHeader.isBlank()) {
            log.warn("Stripe webhook called without Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            stripePaymentService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            // Signature mismatch or metadata problem — return 400 so Stripe retries
            log.warn("Stripe webhook rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Unexpected error processing Stripe webhook", e);
            // Return 200 so Stripe does NOT keep retrying for transient errors we need to investigate manually
            return ResponseEntity.ok().build();
        }
    }
}
