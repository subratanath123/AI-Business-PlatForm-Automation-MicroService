package net.ai.chatbot.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.ai.chatbot.dto.user.BusinessProfileData;
import net.ai.chatbot.dto.user.SubscriptionSnapshotData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_business_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBusinessProfile {

    /** Clerk user id */
    @Id
    private String id;

    private BusinessProfileData businessProfile;
    private SubscriptionSnapshotData subscription;
    private Instant updatedAt;
}
