package net.ai.chatbot.dao;

import net.ai.chatbot.entity.WooCommerceIntegration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WooCommerceIntegrationDao extends MongoRepository<WooCommerceIntegration, String> {

    /** Preferred lookup for write paths — returns the user's currently active store. */
    Optional<WooCommerceIntegration> findByUserIdAndActiveTrue(String userId);

    /** Fallback — returns any integration for the user. */
    Optional<WooCommerceIntegration> findByUserId(String userId);

    List<WooCommerceIntegration> findAllByUserId(String userId);

    Optional<WooCommerceIntegration> findByUserIdAndStoreUrl(String userId, String storeUrl);

    boolean existsByUserId(String userId);

    void deleteByUserId(String userId);

    void deleteByUserIdAndStoreUrl(String userId, String storeUrl);
}
