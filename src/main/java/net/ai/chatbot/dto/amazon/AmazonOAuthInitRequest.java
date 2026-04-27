package net.ai.chatbot.dto.amazon;

import lombok.Data;

/**
 * Frontend -> backend when the user kicks off the LWA OAuth flow. The
 * backend generates a CSRF {@code state} keyed to this user and returns
 * the Amazon authorize URL the browser should redirect to.
 */
@Data
public class AmazonOAuthInitRequest {
    /** SP-API region to authorize against: NA | EU | FE. Default "NA". */
    private String region;
}
