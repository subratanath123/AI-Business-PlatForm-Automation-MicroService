package net.ai.chatbot.dao.admin;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.admin.PromoCode;
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
public class PromoCodeDao {

    private final MongoTemplate mongoTemplate;

    public PromoCode save(PromoCode promoCode) {
        if (promoCode.getCreatedAt() == null) {
            promoCode.setCreatedAt(Instant.now());
        }
        promoCode.setUpdatedAt(Instant.now());
        return mongoTemplate.save(promoCode);
    }

    public Optional<PromoCode> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, PromoCode.class));
    }

    public Optional<PromoCode> findByCode(String code) {
        Query query = new Query(Criteria.where("code").is(code.toUpperCase()));
        return Optional.ofNullable(mongoTemplate.findOne(query, PromoCode.class));
    }

    public List<PromoCode> findAll(int page, int size, Boolean isActive) {
        Query query = new Query();
        if (isActive != null) {
            query.addCriteria(Criteria.where("isActive").is(isActive));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, PromoCode.class);
    }

    public long count(Boolean isActive) {
        Query query = new Query();
        if (isActive != null) {
            query.addCriteria(Criteria.where("isActive").is(isActive));
        }
        return mongoTemplate.count(query, PromoCode.class);
    }

    public List<PromoCode> findValidCodes() {
        Instant now = Instant.now();
        Query query = new Query(Criteria.where("isActive").is(true)
                .andOperator(
                        new Criteria().orOperator(
                                Criteria.where("validFrom").isNull(),
                                Criteria.where("validFrom").lte(now)
                        ),
                        new Criteria().orOperator(
                                Criteria.where("validUntil").isNull(),
                                Criteria.where("validUntil").gte(now)
                        )
                ));
        return mongoTemplate.find(query, PromoCode.class);
    }

    public boolean isCodeValid(String code, String userId, String planId) {
        Instant now = Instant.now();
        PromoCode promoCode = findByCode(code).orElse(null);

        if (promoCode == null || !promoCode.isActive()) {
            return false;
        }

        if (promoCode.getValidFrom() != null && promoCode.getValidFrom().isAfter(now)) {
            return false;
        }

        if (promoCode.getValidUntil() != null && promoCode.getValidUntil().isBefore(now)) {
            return false;
        }

        if (promoCode.getMaxUsageCount() != null &&
                promoCode.getCurrentUsageCount() >= promoCode.getMaxUsageCount()) {
            return false;
        }

        if (promoCode.getUsedByUserIds() != null && userId != null) {
            long userUsageCount = promoCode.getUsedByUserIds().stream()
                    .filter(id -> id.equals(userId))
                    .count();
            if (promoCode.getMaxUsagePerUser() != null &&
                    userUsageCount >= promoCode.getMaxUsagePerUser()) {
                return false;
            }
        }

        if (planId != null && !promoCode.getApplicablePlanIds().isEmpty() &&
                !promoCode.getApplicablePlanIds().contains(planId)) {
            return false;
        }

        if (planId != null && promoCode.getExcludedPlanIds().contains(planId)) {
            return false;
        }

        return true;
    }

    public void incrementUsage(String promoCodeId, String userId) {
        Query query = new Query(Criteria.where("_id").is(promoCodeId));
        Update update = new Update()
                .inc("currentUsageCount", 1)
                .push("usedByUserIds", userId)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, PromoCode.class);
    }

    public void setActive(String id, boolean isActive) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("isActive", isActive)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, PromoCode.class);
    }

    public void delete(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, PromoCode.class);
    }

    public boolean existsByCode(String code) {
        Query query = new Query(Criteria.where("code").is(code.toUpperCase()));
        return mongoTemplate.exists(query, PromoCode.class);
    }
}
