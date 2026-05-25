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

    /** MongoDB ID of the SubscriptionPlan. */
    private String planId;

    /** Human-readable plan name, e.g. "Pro", "Business". */
    private String planName;

    /** Plan code, e.g. "PRO", "BUSINESS". */
    private String planCode;

    /** Billing status: ACTIVE / TRIAL / PAST_DUE / CANCELLED / EXPIRED. */
    private String billingStatus;

    /** ISO-8601 string for the subscription end date. */
    private String currentPeriodEnd;
}
