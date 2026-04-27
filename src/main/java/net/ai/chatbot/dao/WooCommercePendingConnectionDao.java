package net.ai.chatbot.dao;

import net.ai.chatbot.entity.WooCommercePendingConnection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WooCommercePendingConnectionDao
        extends MongoRepository<WooCommercePendingConnection, String> {

    Optional<WooCommercePendingConnection> findByNonce(String nonce);

    void deleteByNonce(String nonce);

    void deleteAllByUserId(String userId);
}
