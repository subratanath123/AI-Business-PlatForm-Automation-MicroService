package net.ai.chatbot.dao;

import net.ai.chatbot.entity.EbayIntegration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EbayIntegrationDao extends MongoRepository<EbayIntegration, String> {

    Optional<EbayIntegration> findByUserIdAndActiveTrue(String userId);

    Optional<EbayIntegration> findByUserId(String userId);

    List<EbayIntegration> findAllByUserId(String userId);

    Optional<EbayIntegration> findByUserIdAndSellerId(String userId, String sellerId);

    boolean existsByUserId(String userId);

    void deleteByUserId(String userId);

    void deleteByUserIdAndSellerId(String userId, String sellerId);
}
