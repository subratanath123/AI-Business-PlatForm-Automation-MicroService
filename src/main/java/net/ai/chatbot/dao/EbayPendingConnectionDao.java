package net.ai.chatbot.dao;

import net.ai.chatbot.entity.EbayPendingConnection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EbayPendingConnectionDao
        extends MongoRepository<EbayPendingConnection, String> {

    Optional<EbayPendingConnection> findByState(String state);

    void deleteByState(String state);

    void deleteAllByUserId(String userId);
}
