package net.ai.chatbot.controller.user;

import net.ai.chatbot.dto.MfaStatusResponse;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only MFA hints from the Clerk session JWT (no separate DB persistence).
 * For full enrollment state, prefer Clerk’s Frontend API or Next.js {@code currentUser()}.
 */
@RestController
@RequestMapping("/v1/api/user")
public class UserMfaController {

    @GetMapping("/mfa")
    public ResponseEntity<MfaStatusResponse> getMfaStatus() {
        var fva = AuthUtils.getFactorVerificationAgeMinutes();
        Boolean totpClaim = AuthUtils.getOptionalBooleanClaim("totp_enabled");
        if (totpClaim == null) {
            totpClaim = AuthUtils.getOptionalBooleanClaim("totpEnabled");
        }
        Boolean tfClaim = AuthUtils.getOptionalBooleanClaim("two_factor_enabled");
        if (tfClaim == null) {
            tfClaim = AuthUtils.getOptionalBooleanClaim("twoFactorEnabled");
        }
        return ResponseEntity.ok(new MfaStatusResponse(fva, totpClaim, tfClaim));
    }
}
