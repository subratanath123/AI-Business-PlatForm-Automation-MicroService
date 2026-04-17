package net.ai.chatbot.dao;

import net.ai.chatbot.entity.ProductEnhancementJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductEnhancementJobDao extends MongoRepository<ProductEnhancementJob, String> {

    List<ProductEnhancementJob> findByUserIdOrderByCreatedAtDesc(String userId);

    List<ProductEnhancementJob> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);
}
