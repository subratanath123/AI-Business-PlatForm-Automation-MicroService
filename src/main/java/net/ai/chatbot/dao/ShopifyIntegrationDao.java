package net.ai.chatbot.dao;

import net.ai.chatbot.entity.ShopifyIntegration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopifyIntegrationDao extends MongoRepository<ShopifyIntegration, String> {

    /** Preferred lookup for write paths — returns the user's currently active store. */
    Optional<ShopifyIntegration> findByUserIdAndActiveTrue(String userId);

    /** Fallback — returns any integration for the user (used during migration / disconnect). */
    Optional<ShopifyIntegration> findByUserId(String userId);

    List<ShopifyIntegration> findAllByUserId(String userId);

    Optional<ShopifyIntegration> findByUserIdAndShopDomain(String userId, String shopDomain);

    boolean existsByUserId(String userId);

    void deleteByUserId(String userId);

    void deleteByUserIdAndShopDomain(String userId, String shopDomain);
}
