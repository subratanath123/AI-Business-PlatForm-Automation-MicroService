package net.ai.chatbot.service.payment;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.SubscriptionPlanDao;
import net.ai.chatbot.dao.admin.UserSubscriptionDao;
import net.ai.chatbot.dto.admin.PricingType;
import net.ai.chatbot.dto.admin.SubscriptionPlan;
import net.ai.chatbot.dto.admin.SubscriptionStatus;
import net.ai.chatbot.dto.admin.UserSubscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles Stripe Checkout Session creation and webhook event processing.
 *
 * <p>Flow:
 * 1. createCheckoutSession() – frontend redirects user to Stripe-hosted page
 * 2. Stripe calls handleWebhook() when payment succeeds
 * 3. activateSubscription() provisions the plan for the user
 */
@Slf4j
@Service
public class StripePaymentService {

    private final SubscriptionPlanDao subscriptionPlanDao;
    private final UserSubscriptionDao userSubscriptionDao;
    private final SubscriptionEmailService subscriptionEmailService;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.frontend-url:https://subratapc.net}")
    private String frontendUrl;

    public StripePaymentService(SubscriptionPlanDao subscriptionPlanDao,
                                UserSubscriptionDao userSubscriptionDao,
                                SubscriptionEmailService subscriptionEmailService) {
        this.subscriptionPlanDao = subscriptionPlanDao;
        this.userSubscriptionDao = userSubscriptionDao;
        this.subscriptionEmailService = subscriptionEmailService;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CHECKOUT SESSION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates a Stripe Checkout Session for the given plan.
     *
     * @param userId      Clerk user ID
     * @param userEmail   user's email (used to find/create Stripe customer)
     * @param planId      MongoDB ID of the SubscriptionPlan
     * @param isAnnual    true → annual price, false → monthly price
     * @param promoCode   optional promo code already validated on our side
     * @return Stripe Checkout Session URL to redirect the user to
     */
    public String createCheckoutSession(String userId,
                                        String userEmail,
                                        String planId,
                                        boolean isAnnual,
                                        String promoCode) throws StripeException {

        configureStripe();

        SubscriptionPlan plan = subscriptionPlanDao.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        if (!plan.isActive()) {
            throw new IllegalArgumentException("Plan is not active: " + plan.getPlanCode());
        }

        String stripePriceId = isAnnual ? plan.getStripeAnnualPriceId() : plan.getStripePriceId();

        if (!StringUtils.hasText(stripePriceId)) {
            throw new IllegalStateException(
                "Stripe price ID not configured for plan " + plan.getPlanCode() +
                (isAnnual ? " (annual)" : " (monthly)")
            );
        }

        String customerId = findOrCreateStripeCustomer(userId, userEmail);

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setSuccessUrl(frontendUrl + "/subscription/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/subscription/cancel")
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(stripePriceId)
                        .setQuantity(1L)
                        .build()
                )
                // Store metadata for webhook processing
                .putMetadata("userId", userId)
                .putMetadata("planId", planId)
                .putMetadata("planCode", plan.getPlanCode())
                .putMetadata("isAnnual", String.valueOf(isAnnual));

        if (StringUtils.hasText(promoCode)) {
            paramsBuilder.putMetadata("promoCode", promoCode);
            // Let Stripe apply a coupon if one exists for the promo code
            paramsBuilder.setAllowPromotionCodes(true);
        } else {
            paramsBuilder.setAllowPromotionCodes(true);
        }

        // Allow billing address collection
        paramsBuilder.setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.AUTO);

        Session session = Session.create(paramsBuilder.build());
        log.info("Created Stripe checkout session {} for user {} plan {}", session.getId(), userId, plan.getPlanCode());
        return session.getUrl();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WEBHOOK HANDLING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Validates and routes incoming Stripe webhook events.
     *
     * @param payload   raw request body string
     * @param sigHeader Stripe-Signature header value
     */
    public void handleWebhook(String payload, String sigHeader) {
        configureStripe();

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid webhook signature", e);
        }

        log.info("Received Stripe webhook event: {} id={}", event.getType(), event.getId());

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new IllegalStateException("Cannot deserialize checkout session"));
                handleCheckoutCompleted(session);
            }
            case "customer.subscription.deleted", "customer.subscription.updated" -> {
                Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new IllegalStateException("Cannot deserialize subscription"));
                handleSubscriptionChanged(subscription, event.getType());
            }
            case "invoice.payment_failed" -> {
                Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new IllegalStateException("Cannot deserialize invoice"));
                handlePaymentFailed(invoice);
            }
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL EVENT HANDLERS
    // ═══════════════════════════════════════════════════════════════════════

    private void handleCheckoutCompleted(Session session) {
        try {
            fulfillCheckoutSession(session, null);
        } catch (Exception e) {
            log.error("Failed to fulfill checkout session {}: {}", session.getId(), e.getMessage(), e);
        }
    }

    /**
     * Confirms a completed Stripe Checkout Session and activates the subscription.
     * Idempotent — safe to call from the webhook and from the success-page fallback.
     *
     * @param expectedUserId when non-null, must match session metadata userId (authenticated verify)
     */
    public UserSubscription fulfillCheckoutSession(Session session, String expectedUserId) throws StripeException {
        configureStripe();

        if (!"complete".equals(session.getStatus())) {
            throw new IllegalStateException("Checkout is not complete yet (status: " + session.getStatus() + ")");
        }

        Map<String, String> meta = session.getMetadata() != null ? session.getMetadata() : Map.of();
        String userId = meta.get("userId");
        String planId = meta.get("planId");
        boolean isAnnual = Boolean.parseBoolean(meta.getOrDefault("isAnnual", "false"));
        String promoCode = meta.get("promoCode");

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(planId)) {
            throw new IllegalStateException("Checkout session is missing userId or planId metadata");
        }
        if (expectedUserId != null && !expectedUserId.equals(userId)) {
            throw new IllegalStateException("Checkout session does not belong to this user");
        }

        String stripeSubId = session.getSubscription();
        if (StringUtils.hasText(stripeSubId)) {
            Optional<UserSubscription> existing = userSubscriptionDao.findByStripeSubscriptionId(stripeSubId);
            if (existing.isPresent()) {
                log.info("Checkout session {} already fulfilled as subscription {}", session.getId(), existing.get().getId());
                return existing.get();
            }
        }

        SubscriptionPlan plan = subscriptionPlanDao.findById(planId)
                .orElseThrow(() -> new IllegalStateException("Plan not found: " + planId));

        if ("FREE".equalsIgnoreCase(plan.getPlanCode())) {
            throw new IllegalStateException("Paid checkout cannot activate a free plan");
        }

        BigDecimal amountPaid = session.getAmountTotal() != null
                ? BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        String email = session.getCustomerDetails() != null && session.getCustomerDetails().getEmail() != null
                ? session.getCustomerDetails().getEmail()
                : (session.getCustomerEmail() != null ? session.getCustomerEmail() : "");

        activateSubscription(
                userId,
                email,
                plan,
                isAnnual,
                stripeSubId,
                session.getCustomer(),
                promoCode,
                amountPaid
        );

        if (StringUtils.hasText(stripeSubId)) {
            return userSubscriptionDao.findByStripeSubscriptionId(stripeSubId)
                    .orElseThrow(() -> new IllegalStateException("Subscription was not saved"));
        }
        return userSubscriptionDao.findActiveByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Subscription was not saved"));
    }

    /**
     * Success-page fallback when the Stripe webhook has not run yet (common in dev).
     */
    public UserSubscription verifyCheckoutSession(String sessionId, String userId) throws StripeException {
        configureStripe();
        Session session = Session.retrieve(sessionId);
        return fulfillCheckoutSession(session, userId);
    }

    private void handleSubscriptionChanged(Subscription stripeSubscription, String eventType) {
        String stripeSubId = stripeSubscription.getId();
        Optional<UserSubscription> existing = userSubscriptionDao.findByStripeSubscriptionId(stripeSubId);
        if (existing.isEmpty()) {
            log.warn("No UserSubscription found for Stripe subscription {}", stripeSubId);
            return;
        }

        UserSubscription sub = existing.get();

        if ("customer.subscription.deleted".equals(eventType)
                || "canceled".equals(stripeSubscription.getStatus())) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setCancelledAt(Instant.now());
            sub.setCancellationReason("Cancelled via Stripe");
            userSubscriptionDao.save(sub);
            log.info("Cancelled subscription {} for user {}", sub.getId(), sub.getUserId());
        } else if ("active".equals(stripeSubscription.getStatus())) {
            sub.setStatus(SubscriptionStatus.ACTIVE);
            if (stripeSubscription.getCurrentPeriodEnd() != null) {
                sub.setEndDate(Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()));
            }
            userSubscriptionDao.save(sub);
            log.info("Renewed subscription {} for user {}", sub.getId(), sub.getUserId());
        }
    }

    private void handlePaymentFailed(Invoice invoice) {
        String stripeSubId = invoice.getSubscription();
        if (!StringUtils.hasText(stripeSubId)) return;

        userSubscriptionDao.findByStripeSubscriptionId(stripeSubId).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            userSubscriptionDao.save(sub);
            log.warn("Payment failed for subscription {} user {}", sub.getId(), sub.getUserId());
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUBSCRIPTION ACTIVATION
    // ═══════════════════════════════════════════════════════════════════════

    private void activateSubscription(String userId,
                                      String userEmail,
                                      SubscriptionPlan plan,
                                      boolean isAnnual,
                                      String stripeSubscriptionId,
                                      String stripeCustomerId,
                                      String promoCode,
                                      BigDecimal amountPaid) {

        // Cancel any existing active subscription
        userSubscriptionDao.findActiveByUserId(userId).ifPresent(existing -> {
            existing.setStatus(SubscriptionStatus.CANCELLED);
            existing.setCancelledAt(Instant.now());
            existing.setCancellationReason("Upgraded/changed plan");
            userSubscriptionDao.save(existing);
        });

        Instant now = Instant.now();
        Instant endDate = isAnnual
                ? now.plus(365, ChronoUnit.DAYS)
                : now.plus(30, ChronoUnit.DAYS);

        UserSubscription sub = UserSubscription.builder()
                .userId(userId)
                .userEmail(userEmail)
                .planId(plan.getId())
                .planCode(plan.getPlanCode())
                .planName(plan.getName())
                .status(SubscriptionStatus.ACTIVE)
                .pricingType(isAnnual ? PricingType.YEARLY : PricingType.MONTHLY)
                .amountPaid(amountPaid)
                .currency(plan.getCurrency() != null ? plan.getCurrency() : "USD")
                .startDate(now)
                .endDate(endDate)
                .autoRenew(true)
                .stripeSubscriptionId(stripeSubscriptionId)
                .stripeCustomerId(stripeCustomerId)
                .promoCodeUsed(promoCode)
                .createdAt(now)
                .updatedAt(now)
                .build();

        UserSubscription saved = userSubscriptionDao.save(sub);
        log.info("Activated plan {} ({}) for user {} until {}", plan.getPlanCode(), isAnnual ? "annual" : "monthly", userId, endDate);

        // Send confirmation email asynchronously (best-effort)
        try {
            subscriptionEmailService.sendSubscriptionConfirmation(saved);
        } catch (Exception e) {
            log.warn("Could not send subscription confirmation email for user {}: {}", userId, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STRIPE CUSTOMER HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private String findOrCreateStripeCustomer(String userId, String email) throws StripeException {
        // Try to find existing customer by metadata userId
        CustomerSearchParams searchParams = CustomerSearchParams.builder()
                .setQuery("metadata[\"userId\"]:\"" + userId + "\"")
                .build();
        CustomerSearchResult result = Customer.search(searchParams);
        if (!result.getData().isEmpty()) {
            return result.getData().get(0).getId();
        }

        // Create new customer
        Map<String, String> meta = new HashMap<>();
        meta.put("userId", userId);

        CustomerCreateParams createParams = CustomerCreateParams.builder()
                .setEmail(email)
                .setMetadata(meta)
                .build();

        Customer customer = Customer.create(createParams);
        log.info("Created Stripe customer {} for user {}", customer.getId(), userId);
        return customer.getId();
    }

    private void configureStripe() {
        if (StringUtils.hasText(stripeSecretKey)) {
            Stripe.apiKey = stripeSecretKey;
        } else {
            throw new IllegalStateException("Stripe secret key is not configured. Set STRIPE_SECRET_KEY.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FREE PLAN
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Activates a free plan for the user without any Stripe interaction.
     */
    public void activateFreePlanForUser(String userId, String userEmail, String planId) {
        SubscriptionPlan plan = subscriptionPlanDao.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        if (!plan.isActive()) {
            throw new IllegalArgumentException("Plan is not currently active");
        }

        BigDecimal price = plan.getMonthlyPrice();
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("This plan requires payment. Please use the payment flow.");
        }

        activateSubscription(userId, userEmail, plan, false, null, null, null, BigDecimal.ZERO);
        log.info("Activated free plan {} for user {}", plan.getPlanCode(), userId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC KEY ACCESSOR (for frontend config endpoint)
    // ═══════════════════════════════════════════════════════════════════════

    @Value("${stripe.publishable-key:}")
    private String stripePublishableKey;

    public String getPublishableKey() {
        return stripePublishableKey;
    }
}
