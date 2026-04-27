package net.ai.chatbot.dao;

import net.ai.chatbot.entity.AliExpressPendingConnection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AliExpressPendingConnectionDao extends MongoRepository<AliExpressPendingConnection, String> {

    Optional<AliExpressPendingConnection> findByState(String state);

    void deleteByState(String state);
}
