package net.ai.chatbot.dto.ebay;

import lombok.Data;

/**
 * Enable / disable eBay platform notifications. When enabling, the caller
 * supplies the public HTTPS endpoint eBay should POST notifications to
 * (typically {@code https://…/api/public/ebay/notify/{userId}}) along
 * with a shared secret eBay will echo back in the signature header.
 */
@Data
public class EbayNotificationsToggleRequest {
    private boolean enabled;
    /** Publicly reachable HTTPS endpoint eBay should POST notifications to. */
    private String endpointUrl;
    /** Shared secret used to validate incoming notifications (HMAC). */
    private String verificationToken;
}
