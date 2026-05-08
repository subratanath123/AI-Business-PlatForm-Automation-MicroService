package net.ai.chatbot.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.dashboard.SubscriptionDashboardSummary;
import net.ai.chatbot.dto.user.SubscriptionSnapshotData;
import net.ai.chatbot.entity.UserBusinessProfile;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Reads the subscription snapshot from the {@code user_business_profiles}
 * collection (id = Clerk user id) for the dashboard subscription card.
 *
 * <p>This is intentionally a read of the cached snapshot only — it does not
 * call Stripe or any other billing provider. Returns
 * {@code hasSubscription = false} when the profile or snapshot is missing.
 */
@Service
@Slf4j
public class SubscriptionDashboardService {

    private final MongoTemplate mongoTemplate;

    public SubscriptionDashboardService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public SubscriptionDashboardSummary getSummary() {
        String userId = AuthUtils.getUserId();
        if (userId == null || userId.isBlank()) {
            return SubscriptionDashboardSummary.builder().hasSubscription(false).build();
        }

        UserBusinessProfile profile = mongoTemplate.findOne(
                new Query(Criteria.where("_id").is(userId)),
                UserBusinessProfile.class);

        if (profile == null || profile.getSubscription() == null) {
            return SubscriptionDashboardSummary.builder().hasSubscription(false).build();
        }

        SubscriptionSnapshotData snapshot = profile.getSubscription();
        boolean hasAny = snapshot.getPlanId() != null
                || snapshot.getBillingStatus() != null
                || snapshot.getCurrentPeriodEnd() != null;

        return SubscriptionDashboardSummary.builder()
                .hasSubscription(hasAny)
                .planId(snapshot.getPlanId())
                .billingStatus(snapshot.getBillingStatus())
                .currentPeriodEnd(snapshot.getCurrentPeriodEnd())
                .build();
    }
}
