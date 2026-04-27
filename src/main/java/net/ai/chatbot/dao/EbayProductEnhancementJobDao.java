package net.ai.chatbot.dao;

import net.ai.chatbot.entity.EbayProductEnhancementJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EbayProductEnhancementJobDao
        extends MongoRepository<EbayProductEnhancementJob, String> {

    List<EbayProductEnhancementJob> findByUserIdOrderByCreatedAtDesc(String userId);

    List<EbayProductEnhancementJob> findByUserIdAndStatusOrderByCreatedAtDesc(
            String userId, String status);
}
