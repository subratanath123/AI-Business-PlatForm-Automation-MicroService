package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compact KPI strip rendered at the top of the new dashboard. Designed to be
 * fetched in a single round-trip so the user sees meaningful numbers
 * immediately, before the heavier per-section summaries arrive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiStripSummary {
    /** Active chatbots owned by the current user. */
    private long activeChatbots;

    /** New conversations in the last 7 days, scoped to the user's chatbots. */
    private long conversations7d;

    /** New chat messages in the last 7 days, scoped to the user's chatbots. */
    private long messages7d;

    /** Currently scheduled (not yet published) social posts. */
    private long postsScheduled;

    /** Posts published in the last 7 days. */
    private long postsPublished7d;

    /** Total AI generations across all studios in the last 30 days. */
    private long aiGenerations30d;

    /** Connected e-commerce stores summed across Shopify / Woo / Amazon / eBay / AliExpress. */
    private long connectedStores;

    /** Subscription plan id ({@code null} when no profile snapshot exists). */
    private String planId;

    /** Subscription billing status ({@code active}, {@code trialing}, …); may be {@code null}. */
    private String billingStatus;

    /** Subscription period end ISO-8601 string; may be {@code null}. */
    private String currentPeriodEnd;
}
