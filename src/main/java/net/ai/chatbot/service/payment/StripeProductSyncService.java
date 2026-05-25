package net.ai.chatbot.service.payment;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.PriceUpdateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.ProductUpdateParams;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.SubscriptionPlan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Keeps Stripe Products and Prices in sync whenever an admin creates or updates
 * a subscription plan.
 *
 * <p>Rules:
 * <ul>
 *   <li>Free plans (price = 0 or null) are skipped — no Stripe object needed.</li>
 *   <li>On first save: a Stripe Product + monthly Price (+ annual Price if set) are created.</li>
 *   <li>On subsequent saves: product name is updated; if the price amount changed a new Price
 *       is created (Stripe does not allow mutating price amounts) and the old one is archived.</li>
 * </ul>
 */
@Slf4j
@Service
public class StripeProductSyncService {

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Syncs the plan to Stripe and populates the Stripe ID fields on the plan object.
     * Mutates the passed-in plan in place; caller must persist it afterwards.
     *
     * @return true if any Stripe IDs were changed and the plan should be re-saved
     */
    public boolean syncPlanToStripe(SubscriptionPlan plan) {
        if (!isConfigured()) {
            log.debug("Stripe not configured — skipping sync for plan {}", plan.getPlanCode());
            return false;
        }

        if (isFreePlan(plan)) {
            log.debug("Skipping Stripe sync for free plan {}", plan.getPlanCode());
            return false;
        }

        configureStripe();
        boolean changed = false;

        try {
            // ── 1. Ensure Product exists ──────────────────────────────────
            String productId = plan.getStripeProductId();
            if (!StringUtils.hasText(productId)) {
                productId = createProduct(plan);
                plan.setStripeProductId(productId);
                changed = true;
                log.info("Created Stripe product {} for plan {}", productId, plan.getPlanCode());
            } else {
                updateProductName(productId, plan.getName());
            }

            // ── 2. Monthly price ──────────────────────────────────────────
            if (plan.getMonthlyPrice() != null && plan.getMonthlyPrice().compareTo(BigDecimal.ZERO) > 0) {
                String newPriceId = ensurePrice(
                    productId,
                    plan.getStripePriceId(),
                    plan.getMonthlyPrice(),
                    plan.getCurrency(),
                    PriceCreateParams.Recurring.Interval.MONTH,
                    plan.getPlanCode() + " Monthly"
                );
                if (!newPriceId.equals(plan.getStripePriceId())) {
                    plan.setStripePriceId(newPriceId);
                    changed = true;
                    log.info("Set monthly Stripe price {} for plan {}", newPriceId, plan.getPlanCode());
                }
            }

            // ── 3. Annual price ───────────────────────────────────────────
            if (plan.getAnnualPrice() != null && plan.getAnnualPrice().compareTo(BigDecimal.ZERO) > 0) {
                // Annual billed as one payment per year
                BigDecimal annualTotal = plan.getAnnualPrice().multiply(BigDecimal.valueOf(12));
                String newAnnualPriceId = ensurePrice(
                    productId,
                    plan.getStripeAnnualPriceId(),
                    annualTotal,
                    plan.getCurrency(),
                    PriceCreateParams.Recurring.Interval.YEAR,
                    plan.getPlanCode() + " Annual"
                );
                if (!newAnnualPriceId.equals(plan.getStripeAnnualPriceId())) {
                    plan.setStripeAnnualPriceId(newAnnualPriceId);
                    changed = true;
                    log.info("Set annual Stripe price {} for plan {}", newAnnualPriceId, plan.getPlanCode());
                }
            }

        } catch (StripeException e) {
            // Non-fatal: log and continue — admin can retry or set IDs manually
            log.error("Stripe sync failed for plan {}: {} — {}",
                plan.getPlanCode(), e.getCode(), e.getMessage());
        }

        return changed;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STRIPE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private String createProduct(SubscriptionPlan plan) throws StripeException {
        ProductCreateParams params = ProductCreateParams.builder()
                .setName(plan.getName())
                .setDescription(plan.getDescription() != null ? plan.getDescription() : plan.getName())
                .putMetadata("planCode", plan.getPlanCode())
                .build();
        return Product.create(params).getId();
    }

    private void updateProductName(String productId, String name) {
        try {
            ProductUpdateParams params = ProductUpdateParams.builder()
                    .setName(name)
                    .build();
            Product.retrieve(productId).update(params);
        } catch (StripeException e) {
            log.warn("Could not update Stripe product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Returns the existing price ID if the amount matches, otherwise creates a new Price
     * and archives the old one.
     */
    private String ensurePrice(String productId,
                                String existingPriceId,
                                BigDecimal amount,
                                String currency,
                                PriceCreateParams.Recurring.Interval interval,
                                String nickname) throws StripeException {

        long unitAmount = toStripeAmount(amount);

        // Check if the existing price still matches
        if (StringUtils.hasText(existingPriceId)) {
            try {
                Price existing = Price.retrieve(existingPriceId);
                if (existing.getActive() && existing.getUnitAmount() != null
                        && existing.getUnitAmount().equals(unitAmount)) {
                    return existingPriceId; // nothing to do
                }
                // Amount changed — archive the old price
                existing.update(PriceUpdateParams.builder().setActive(false).build());
                log.info("Archived old Stripe price {} (amount changed)", existingPriceId);
            } catch (StripeException e) {
                log.warn("Could not retrieve/archive old price {}: {}", existingPriceId, e.getMessage());
            }
        }

        // Create new price
        PriceCreateParams params = PriceCreateParams.builder()
                .setProduct(productId)
                .setUnitAmount(unitAmount)
                .setCurrency(currency != null ? currency.toLowerCase() : "usd")
                .setRecurring(
                    PriceCreateParams.Recurring.builder()
                        .setInterval(interval)
                        .build()
                )
                .setNickname(nickname)
                .build();

        return Price.create(params).getId();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════

    /** Converts a dollar amount to Stripe's integer cents format. */
    private long toStripeAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                     .setScale(0, RoundingMode.HALF_UP)
                     .longValue();
    }

    private boolean isFreePlan(SubscriptionPlan plan) {
        boolean noMonthly = plan.getMonthlyPrice() == null
                || plan.getMonthlyPrice().compareTo(BigDecimal.ZERO) <= 0;
        boolean noAnnual = plan.getAnnualPrice() == null
                || plan.getAnnualPrice().compareTo(BigDecimal.ZERO) <= 0;
        return noMonthly && noAnnual;
    }

    private boolean isConfigured() {
        return StringUtils.hasText(stripeSecretKey);
    }

    private void configureStripe() {
        Stripe.apiKey = stripeSecretKey;
    }
}
