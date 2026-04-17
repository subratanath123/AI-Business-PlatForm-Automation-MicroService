package net.ai.chatbot.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSnapshotData {

    private String planId;
    private String billingStatus;
    private String currentPeriodEnd;
}
