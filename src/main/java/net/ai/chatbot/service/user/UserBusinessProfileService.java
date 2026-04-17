package net.ai.chatbot.service.user;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dao.UserBusinessProfileDao;
import net.ai.chatbot.dto.user.BusinessProfileData;
import net.ai.chatbot.dto.user.SubscriptionSnapshotData;
import net.ai.chatbot.entity.UserBusinessProfile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserBusinessProfileService {

    private final UserBusinessProfileDao dao;

    public Optional<UserBusinessProfile> get(String clerkUserId) {
        return dao.findById(clerkUserId);
    }

    /**
     * Never 404 — new users get an empty shell so API clients (and proxies) always receive JSON.
     */
    public UserBusinessProfile getOrEmpty(String clerkUserId) {
        return dao.findById(clerkUserId).orElseGet(() -> UserBusinessProfile.builder()
                .id(clerkUserId)
                .businessProfile(BusinessProfileData.builder().build())
                .subscription(SubscriptionSnapshotData.builder()
                        .planId("free")
                        .billingStatus("none")
                        .currentPeriodEnd("")
                        .build())
                .build());
    }

    public UserBusinessProfile upsert(String clerkUserId, BusinessProfileData businessProfile, SubscriptionSnapshotData subscription) {
        UserBusinessProfile doc = dao.findById(clerkUserId)
                .orElse(UserBusinessProfile.builder().id(clerkUserId).build());
        if (businessProfile != null) {
            doc.setBusinessProfile(businessProfile);
        }
        if (subscription != null) {
            doc.setSubscription(subscription);
        }
        doc.setUpdatedAt(Instant.now());
        return dao.save(doc);
    }
}
