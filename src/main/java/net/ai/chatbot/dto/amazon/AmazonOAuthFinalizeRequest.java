package net.ai.chatbot.dto.amazon;

import lombok.Data;

/**
 * Browser-side finalize call. The browser received the Amazon
 * redirect with {@code spapi_oauth_code}, {@code state} and
 * {@code selling_partner_id} and posts them here to complete the
 * connection.
 */
@Data
public class AmazonOAuthFinalizeRequest {
    /** CSRF token minted at init time — must match the pending connection. */
    private String state;
    /** One-time auth code from Amazon's redirect. */
    private String spapiOauthCode;
    /** Seller id Amazon chose to authorize (may be many per LWA app). */
    private String sellingPartnerId;
    /** Optional friendly name; falls back to the sellerId. */
    private String storeName;
    /** Optional user-provided display name override. */
    private String defaultProductType;
}
