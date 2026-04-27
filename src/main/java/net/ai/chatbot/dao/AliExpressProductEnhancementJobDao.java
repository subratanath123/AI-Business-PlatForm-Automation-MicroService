package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AliExpressProductEnhancementJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AliExpressProductEnhancementJobDao extends MongoRepository<AliExpressProductEnhancementJob, String> {

    List<AliExpressProductEnhancementJob> findByUserIdOrderByCreatedAtDesc(String userId);
}
