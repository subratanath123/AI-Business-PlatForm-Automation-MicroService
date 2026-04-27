package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AmazonProductEnhancementJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmazonProductEnhancementJobDao
        extends MongoRepository<AmazonProductEnhancementJob, String> {

    List<AmazonProductEnhancementJob> findByUserIdOrderByCreatedAtDesc(String userId);

    List<AmazonProductEnhancementJob> findByUserIdAndStatusOrderByCreatedAtDesc(
            String userId, String status);
}
