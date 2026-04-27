package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AmazonPendingConnection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AmazonPendingConnectionDao
        extends MongoRepository<AmazonPendingConnection, String> {

    Optional<AmazonPendingConnection> findByState(String state);

    void deleteByState(String state);

    void deleteAllByUserId(String userId);
}
