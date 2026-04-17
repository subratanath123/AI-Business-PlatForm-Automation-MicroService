package net.ai.chatbot.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code PUT /v1/api/user/business-profile}. Clerk user id is taken from the JWT {@code sub}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBusinessProfileUpdateRequest {

    private BusinessProfileData businessProfile;
    private SubscriptionSnapshotData subscription;
}
