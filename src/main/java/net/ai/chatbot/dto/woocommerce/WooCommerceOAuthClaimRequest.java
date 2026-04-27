package net.ai.chatbot.dto.woocommerce;

import lombok.Data;

/**
 * Posted by the frontend after the WC Auth redirect flow completes, claiming
 * the pending credentials for the current Clerk user and promoting them to
 * a real {@code WooCommerceIntegration}.
 */
@Data
public class WooCommerceOAuthClaimRequest {
    private String nonce;
    private String storeName;
}
