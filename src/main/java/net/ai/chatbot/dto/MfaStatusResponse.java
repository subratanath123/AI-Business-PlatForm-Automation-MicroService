package net.ai.chatbot.dto;

import java.util.List;

/**
 * MFA-related fields derived from the Clerk session JWT.
 * <p>
 * Optional boolean claims {@code totp_enabled} and {@code two_factor_enabled} appear only if you add them
 * in Clerk Dashboard → Sessions → Customize session token (see Clerk docs for the claims editor syntax
 * for your API version).
 * <p>
 * The {@code fva} (factor verification age) claim is always present on v2 session tokens:
 * minutes since last first-factor auth, and minutes since last second-factor auth ({@code -1} if none).
 */
public record MfaStatusResponse(
        List<Integer> factorVerificationAgeMinutes,
        Boolean totpEnabledClaim,
        Boolean twoFactorEnabledClaim
) {
}
