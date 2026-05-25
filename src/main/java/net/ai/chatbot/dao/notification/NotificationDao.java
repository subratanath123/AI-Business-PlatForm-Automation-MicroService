package net.ai.chatbot.dao.notification;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.notification.Notification;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationDao {

    private final MongoTemplate mongoTemplate;

    public Notification save(Notification n) {
        if (n.getCreatedAt() == null) n.setCreatedAt(Instant.now());
        return mongoTemplate.save(n);
    }

    public List<Notification> findByUserId(String userId, int limit) {
        Query q = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);
        return mongoTemplate.find(q, Notification.class);
    }

    public long countUnread(String userId) {
        Query q = new Query(Criteria.where("userId").is(userId).and("read").is(false));
        return mongoTemplate.count(q, Notification.class);
    }

    public void markRead(String notificationId, String userId) {
        Query q = new Query(Criteria.where("_id").is(notificationId).and("userId").is(userId));
        mongoTemplate.updateFirst(q, Update.update("read", true), Notification.class);
    }

    public void markAllRead(String userId) {
        Query q = new Query(Criteria.where("userId").is(userId).and("read").is(false));
        mongoTemplate.updateMulti(q, Update.update("read", true), Notification.class);
    }

    public void deleteById(String id, String userId) {
        Query q = new Query(Criteria.where("_id").is(id).and("userId").is(userId));
        mongoTemplate.remove(q, Notification.class);
    }

    /** Prevent duplicate alerts for the same user+metric+threshold within 24 h. */
    public boolean existsRecent(String userId, String dedupeKey, java.time.Duration window) {
        Instant since = Instant.now().minus(window);
        Query q = new Query(Criteria.where("userId").is(userId)
                .and("metadata.dedupeKey").is(dedupeKey)
                .and("createdAt").gte(since));
        return mongoTemplate.exists(q, Notification.class);
    }
}
