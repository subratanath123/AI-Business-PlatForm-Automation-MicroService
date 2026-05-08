package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight subscription card for the dashboard. Reads directly from the
 * {@link net.ai.chatbot.entity.UserBusinessProfile#getSubscription()} snapshot;
 * this endpoint does not call any billing provider.
 *
 * <p>All fields are nullable when the user has no business profile yet
 * (e.g. brand-new sign-up); the frontend renders a "no subscription" state
 * in that case.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDashboardSummary {

    /** Whether a profile snapshot exists. False ⇒ all other fields are null. */
    private boolean hasSubscription;

    /** Stripe price/plan id (or whatever your Stripe setup uses). */
    private String planId;

    /** Stripe billing status: active / trialing / past_due / canceled / null. */
    private String billingStatus;

    /** ISO-8601 string for the current period end as recorded in the snapshot. */
    private String currentPeriodEnd;
}
