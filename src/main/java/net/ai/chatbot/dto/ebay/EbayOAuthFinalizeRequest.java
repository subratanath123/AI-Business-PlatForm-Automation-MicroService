package net.ai.chatbot.dto.ebay;

import lombok.Data;

/**
 * Browser-side finalize call. The browser received eBay's redirect
 * with {@code code} + {@code state} and posts them here to complete
 * the connection. The backend exchanges the code for tokens using the
 * configured RuName as {@code redirect_uri}.
 */
@Data
public class EbayOAuthFinalizeRequest {
    /** CSRF token minted at init time — must match the pending connection. */
    private String state;
    /** One-time authorization code from eBay's redirect. */
    private String code;
    /** Optional friendly store name; falls back to the seller's userId. */
    private String storeName;
}
