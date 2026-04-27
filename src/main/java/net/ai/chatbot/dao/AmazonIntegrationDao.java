package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AmazonIntegration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AmazonIntegrationDao extends MongoRepository<AmazonIntegration, String> {

    /** Preferred lookup for write paths — returns the user's currently active store. */
    Optional<AmazonIntegration> findByUserIdAndActiveTrue(String userId);

    /** Fallback — returns any integration for the user. */
    Optional<AmazonIntegration> findByUserId(String userId);

    List<AmazonIntegration> findAllByUserId(String userId);

    Optional<AmazonIntegration> findByUserIdAndSellerId(String userId, String sellerId);

    boolean existsByUserId(String userId);

    void deleteByUserId(String userId);

    void deleteByUserIdAndSellerId(String userId, String sellerId);
}
