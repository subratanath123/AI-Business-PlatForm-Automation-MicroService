package net.ai.chatbot.dao.admin;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.admin.SubscriptionStatus;
import net.ai.chatbot.dto.admin.UserSubscription;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserSubscriptionDao {

    private final MongoTemplate mongoTemplate;

    public UserSubscription save(UserSubscription subscription) {
        if (subscription.getCreatedAt() == null) {
            subscription.setCreatedAt(Instant.now());
        }
        subscription.setUpdatedAt(Instant.now());
        return mongoTemplate.save(subscription);
    }

    public Optional<UserSubscription> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, UserSubscription.class));
    }

    public Optional<UserSubscription> findActiveByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("status").in(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL));
        return Optional.ofNullable(mongoTemplate.findOne(query, UserSubscription.class));
    }

    public List<UserSubscription> findByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, UserSubscription.class);
    }

    public List<UserSubscription> findAll(int page, int size, SubscriptionStatus status) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, UserSubscription.class);
    }

    public long count(SubscriptionStatus status) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        return mongoTemplate.count(query, UserSubscription.class);
    }

    public long countByStatus(SubscriptionStatus status) {
        Query query = new Query(Criteria.where("status").is(status));
        return mongoTemplate.count(query, UserSubscription.class);
    }

    public long countByPlanId(String planId) {
        Query query = new Query(Criteria.where("planId").is(planId)
                .and("status").in(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL));
        return mongoTemplate.count(query, UserSubscription.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Long> countByPlanGrouped() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").in(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL)),
                Aggregation.group("planName").count().as("count"),
                Aggregation.project("count").and("_id").as("planName")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "user_subscriptions", Map.class);

        return results.getMappedResults().stream()
                .collect(java.util.stream.Collectors.toMap(
                        m -> (String) m.get("planName"),
                        m -> ((Number) m.get("count")).longValue()
                ));
    }

    public void updateStatus(String subscriptionId, SubscriptionStatus status) {
        Query query = new Query(Criteria.where("_id").is(subscriptionId));
        Update update = new Update()
                .set("status", status)
                .set("updatedAt", Instant.now());

        if (status == SubscriptionStatus.CANCELLED) {
            update.set("cancelledAt", Instant.now());
        }

        mongoTemplate.updateFirst(query, update, UserSubscription.class);
    }

    public List<UserSubscription> findExpiringSoon(int days) {
        Instant threshold = Instant.now().plusSeconds(days * 24L * 60 * 60);
        Query query = new Query(Criteria.where("status").is(SubscriptionStatus.ACTIVE)
                .and("endDate").lte(threshold)
                .and("endDate").gte(Instant.now()));
        return mongoTemplate.find(query, UserSubscription.class);
    }

    public void delete(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, UserSubscription.class);
    }
}
