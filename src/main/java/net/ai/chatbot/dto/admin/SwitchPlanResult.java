package net.ai.chatbot.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchPlanResult {

    private boolean success;
    private boolean requiresCheckout;
    private String planId;
    private String planCode;
    private String planName;

    public static SwitchPlanResult success(UserSubscription sub) {
        return SwitchPlanResult.builder()
                .success(true)
                .requiresCheckout(false)
                .planId(sub.getPlanId())
                .planCode(sub.getPlanCode())
                .planName(sub.getPlanName())
                .build();
    }

    public static SwitchPlanResult requiresCheckout() {
        return SwitchPlanResult.builder()
                .success(false)
                .requiresCheckout(true)
                .build();
    }
}
