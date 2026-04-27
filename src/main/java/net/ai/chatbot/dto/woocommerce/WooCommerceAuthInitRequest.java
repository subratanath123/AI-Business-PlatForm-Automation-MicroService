package net.ai.chatbot.dto.woocommerce;

import lombok.Data;

/**
 * Frontend -> backend when the user kicks off the WC Auth redirect. We
 * pre-register the intended storeUrl against a nonce so the public WC
 * callback doesn't need to trust whatever domain posts to it.
 */
@Data
public class WooCommerceAuthInitRequest {
    private String storeUrl;
}
