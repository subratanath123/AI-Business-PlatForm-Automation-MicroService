package net.ai.chatbot.dao.admin;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.admin.PricingType;
import net.ai.chatbot.dto.admin.SubscriptionPlan;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SubscriptionPlanDao {

    private final MongoTemplate mongoTemplate;

    public SubscriptionPlan save(SubscriptionPlan plan) {
        if (plan.getCreatedAt() == null) {
            plan.setCreatedAt(Instant.now());
        }
        plan.setUpdatedAt(Instant.now());
        return mongoTemplate.save(plan);
    }

    public Optional<SubscriptionPlan> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, SubscriptionPlan.class));
    }

    public Optional<SubscriptionPlan> findByPlanCode(String planCode) {
        Query query = new Query(Criteria.where("planCode").is(planCode));
        return Optional.ofNullable(mongoTemplate.findOne(query, SubscriptionPlan.class));
    }

    public List<SubscriptionPlan> findAll() {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.ASC, "displayOrder"));
        return mongoTemplate.find(query, SubscriptionPlan.class);
    }

    public List<SubscriptionPlan> findAllActive() {
        // Handle both 'isActive' and 'active' field names for backwards compatibility
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("isActive").is(true),
                Criteria.where("active").is(true)
        )).with(Sort.by(Sort.Direction.ASC, "displayOrder"));
        return mongoTemplate.find(query, SubscriptionPlan.class);
    }

    public List<SubscriptionPlan> findByPricingType(PricingType pricingType) {
        Query query = new Query(
                new Criteria().andOperator(
                        Criteria.where("pricingType").is(pricingType),
                        new Criteria().orOperator(
                                Criteria.where("isActive").is(true),
                                Criteria.where("active").is(true)
                        )
                )
        ).with(Sort.by(Sort.Direction.ASC, "displayOrder"));
        return mongoTemplate.find(query, SubscriptionPlan.class);
    }

    public List<SubscriptionPlan> findFeatured() {
        Query query = new Query(
                new Criteria().andOperator(
                        new Criteria().orOperator(
                                Criteria.where("isFeatured").is(true),
                                Criteria.where("featured").is(true)
                        ),
                        new Criteria().orOperator(
                                Criteria.where("isActive").is(true),
                                Criteria.where("active").is(true)
                        )
                )
        ).with(Sort.by(Sort.Direction.ASC, "displayOrder"));
        return mongoTemplate.find(query, SubscriptionPlan.class);
    }

    public void setActive(String id, boolean isActive) {
        SubscriptionPlan plan = mongoTemplate.findById(id, SubscriptionPlan.class);
        if (plan != null) {
            plan.setActive(isActive);
            plan.setUpdatedAt(Instant.now());
            mongoTemplate.save(plan);
        }
    }

    public void delete(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, SubscriptionPlan.class);
    }

    public boolean existsByPlanCode(String planCode) {
        Query query = new Query(Criteria.where("planCode").is(planCode));
        return mongoTemplate.exists(query, SubscriptionPlan.class);
    }

    public long count() {
        return mongoTemplate.count(new Query(), SubscriptionPlan.class);
    }

    public long countActive() {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("isActive").is(true),
                Criteria.where("active").is(true)
        ));
        return mongoTemplate.count(query, SubscriptionPlan.class);
    }
}
