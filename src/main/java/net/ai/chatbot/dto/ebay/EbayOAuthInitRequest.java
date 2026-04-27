package net.ai.chatbot.dto.ebay;

import lombok.Data;

/**
 * Frontend -> backend when the user kicks off the eBay OAuth flow.
 * The backend generates a CSRF {@code state} keyed to this user and
 * returns the eBay authorize URL the browser should redirect to.
 */
@Data
public class EbayOAuthInitRequest {
    /** PRODUCTION | SANDBOX. Default "PRODUCTION". */
    private String environment;
}
