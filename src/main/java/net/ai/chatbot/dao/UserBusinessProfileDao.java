package net.ai.chatbot.dao;

import net.ai.chatbot.entity.UserBusinessProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserBusinessProfileDao extends MongoRepository<UserBusinessProfile, String> {
}
