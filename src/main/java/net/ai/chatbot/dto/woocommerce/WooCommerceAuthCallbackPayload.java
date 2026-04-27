package net.ai.chatbot.dto.woocommerce;

import lombok.Data;

/**
 * Body WooCommerce POSTs to our public callback after the user approves the
 * app in WP-admin. Matches the payload documented at
 * <a href="https://woocommerce.com/document/woocommerce-rest-api/">WooCommerce REST API auth endpoint</a>.
 */
@Data
public class WooCommerceAuthCallbackPayload {
    private String consumer_key;
    private String consumer_secret;
    private String key_permissions;
    private String user_id;
}
