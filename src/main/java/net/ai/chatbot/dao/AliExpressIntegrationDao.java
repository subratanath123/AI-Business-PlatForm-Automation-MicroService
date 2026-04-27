package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AliExpressIntegration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AliExpressIntegrationDao extends MongoRepository<AliExpressIntegration, String> {

    Optional<AliExpressIntegration> findByUserIdAndActiveTrue(String userId);

    List<AliExpressIntegration> findAllByUserId(String userId);

    Optional<AliExpressIntegration> findByUserIdAndSellerId(String userId, String sellerId);

    void deleteByUserId(String userId);

    void deleteByUserIdAndSellerId(String userId, String sellerId);
}
