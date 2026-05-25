package net.ai.chatbot.dao.notification;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.notification.BillingAlertConfig;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BillingAlertConfigDao {

    private final MongoTemplate mongoTemplate;

    public BillingAlertConfig save(BillingAlertConfig config) {
        config.setUpdatedAt(Instant.now());
        return mongoTemplate.save(config);
    }

    public Optional<BillingAlertConfig> findByUserId(String userId) {
        return Optional.ofNullable(mongoTemplate.findById(userId, BillingAlertConfig.class));
    }

    public BillingAlertConfig findOrCreate(String userId) {
        return findByUserId(userId).orElseGet(() -> {
            BillingAlertConfig cfg = BillingAlertConfig.builder().userId(userId).build();
            return save(cfg);
        });
    }
}
