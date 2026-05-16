package net.ai.chatbot.dao.admin;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.admin.PromptTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PromptTemplateDao {

    private final MongoTemplate mongoTemplate;

    public PromptTemplate save(PromptTemplate template) {
        if (template.getCreatedAt() == null) {
            template.setCreatedAt(Instant.now());
        }
        template.setUpdatedAt(Instant.now());
        return mongoTemplate.save(template);
    }

    public Optional<PromptTemplate> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, PromptTemplate.class));
    }

    public Optional<PromptTemplate> findByTemplateCode(String templateCode) {
        Query query = new Query(Criteria.where("templateCode").is(templateCode));
        return Optional.ofNullable(mongoTemplate.findOne(query, PromptTemplate.class));
    }

    public List<PromptTemplate> findAll(int page, int size, String category, Boolean isActive) {
        Query query = new Query();

        if (category != null && !category.isBlank()) {
            query.addCriteria(Criteria.where("category").is(category));
        }

        if (isActive != null) {
            query.addCriteria(Criteria.where("isActive").is(isActive));
        }

        query.with(Sort.by(Sort.Direction.ASC, "displayOrder", "name"))
                .skip((long) page * size)
                .limit(size);

        return mongoTemplate.find(query, PromptTemplate.class);
    }

    public long count(String category, Boolean isActive) {
        Query query = new Query();
        if (category != null && !category.isBlank()) {
            query.addCriteria(Criteria.where("category").is(category));
        }
        if (isActive != null) {
            query.addCriteria(Criteria.where("isActive").is(isActive));
        }
        return mongoTemplate.count(query, PromptTemplate.class);
    }

    public List<PromptTemplate> findByCategory(String category) {
        Criteria activeCriteria = new Criteria().orOperator(
                Criteria.where("isActive").is(true),
                Criteria.where("active").is(true)
        );
        Query query = new Query(Criteria.where("category").is(category).andOperator(activeCriteria))
                .with(Sort.by(Sort.Direction.ASC, "displayOrder", "name"));
        return mongoTemplate.find(query, PromptTemplate.class);
    }

    public List<String> findAllCategories() {
        Criteria activeCriteria = new Criteria().orOperator(
                Criteria.where("isActive").is(true),
                Criteria.where("active").is(true)
        );
        return mongoTemplate.findDistinct(
                new Query(activeCriteria),
                "category",
                PromptTemplate.class,
                String.class
        );
    }

    public List<PromptTemplate> findPopular(int limit) {
        Criteria activeCriteria = new Criteria().orOperator(
                Criteria.where("isActive").is(true),
                Criteria.where("active").is(true)
        );
        Query query = new Query(activeCriteria)
                .with(Sort.by(Sort.Direction.DESC, "usageCount"))
                .limit(limit);
        return mongoTemplate.find(query, PromptTemplate.class);
    }

    public List<PromptTemplate> search(String keyword) {
        Criteria activeCriteria = new Criteria().orOperator(
                Criteria.where("isActive").is(true),
                Criteria.where("active").is(true)
        );
        Criteria searchCriteria = new Criteria().orOperator(
                Criteria.where("name").regex(keyword, "i"),
                Criteria.where("description").regex(keyword, "i"),
                Criteria.where("tags").regex(keyword, "i")
        );
        Query query = new Query(new Criteria().andOperator(activeCriteria, searchCriteria))
                .with(Sort.by(Sort.Direction.DESC, "usageCount"));
        return mongoTemplate.find(query, PromptTemplate.class);
    }

    public void incrementUsageCount(String templateId) {
        Query query = new Query(Criteria.where("_id").is(templateId));
        Update update = new Update().inc("usageCount", 1);
        mongoTemplate.updateFirst(query, update, PromptTemplate.class);
    }

    public void setActive(String id, boolean isActive) {
        PromptTemplate template = mongoTemplate.findById(id, PromptTemplate.class);
        if (template != null) {
            template.setActive(isActive);
            template.setUpdatedAt(Instant.now());
            mongoTemplate.save(template);
        }
    }

    public void delete(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, PromptTemplate.class);
    }

    public boolean existsByTemplateCode(String templateCode) {
        Query query = new Query(Criteria.where("templateCode").is(templateCode));
        return mongoTemplate.exists(query, PromptTemplate.class);
    }

    public long count() {
        return mongoTemplate.count(new Query(), PromptTemplate.class);
    }

    public List<PromptTemplate> findAllActive() {
        Criteria activeCriteria = new Criteria().orOperator(
                Criteria.where("isActive").is(true),
                Criteria.where("active").is(true)
        );
        Query query = new Query(activeCriteria)
                .with(Sort.by(Sort.Direction.ASC, "displayOrder", "name"));
        return mongoTemplate.find(query, PromptTemplate.class);
    }
}
