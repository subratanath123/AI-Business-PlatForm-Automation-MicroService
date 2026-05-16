package net.ai.chatbot.dao.admin;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.admin.AdminUser;
import net.ai.chatbot.dto.admin.UserRole;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AdminUserDao {

    private final MongoTemplate mongoTemplate;

    private static final String COLLECTION_NAME = "user";

    public AdminUser save(AdminUser user) {
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now());
        }
        user.setUpdatedAt(Instant.now());
        return mongoTemplate.save(user, COLLECTION_NAME);
    }

    public Optional<AdminUser> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, AdminUser.class, COLLECTION_NAME));
    }

    public Optional<AdminUser> findByEmail(String email) {
        Query query = new Query(Criteria.where("email").is(email));
        return Optional.ofNullable(mongoTemplate.findOne(query, AdminUser.class, COLLECTION_NAME));
    }

    public List<AdminUser> findAll(int page, int size, UserRole role, Boolean isActive, String search) {
        Query query = buildSearchQuery(role, isActive, search);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, AdminUser.class, COLLECTION_NAME);
    }

    public long count(UserRole role, Boolean isActive, String search) {
        Query query = buildSearchQuery(role, isActive, search);
        return mongoTemplate.count(query, AdminUser.class, COLLECTION_NAME);
    }

    private Query buildSearchQuery(UserRole role, Boolean isActive, String search) {
        Query query = new Query();
        if (role != null) {
            query.addCriteria(Criteria.where("role").is(role));
        }
        if (isActive != null) {
            query.addCriteria(Criteria.where("isActive").is(isActive));
        }
        if (search != null && !search.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("email").regex(search, "i"),
                    Criteria.where("userName").regex(search, "i")
            ));
        }
        return query;
    }

    public long countTotal() {
        return mongoTemplate.count(new Query(), COLLECTION_NAME);
    }

    public long countActive() {
        Query query = new Query(Criteria.where("isActive").is(true));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    public long countByRole(UserRole role) {
        Query query = new Query(Criteria.where("role").is(role));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    public long countCreatedBetween(Instant start, Instant end) {
        Query query = new Query(Criteria.where("createdAt").gte(start).lte(end));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    public long countCreatedToday() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return countCreatedBetween(startOfDay, Instant.now());
    }

    public long countCreatedThisMonth() {
        Instant startOfMonth = Instant.now().truncatedTo(ChronoUnit.DAYS)
                .minus(Instant.now().atZone(java.time.ZoneOffset.UTC).getDayOfMonth() - 1, ChronoUnit.DAYS);
        return countCreatedBetween(startOfMonth, Instant.now());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> countByCountry() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("country").exists(true).ne(null)),
                Aggregation.group("country").count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(20),
                Aggregation.project("count").and("_id").as("country")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, COLLECTION_NAME, Map.class);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map m : results.getMappedResults()) {
            output.add((Map<String, Object>) m);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUserRegistrationsByDay(int days) {
        Instant startDate = Instant.now().minus(days, ChronoUnit.DAYS);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(startDate)),
                Aggregation.project()
                        .andExpression("dateToString('%Y-%m-%d', createdAt)").as("date"),
                Aggregation.group("date").count().as("count"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project("count").and("_id").as("date")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, COLLECTION_NAME, Map.class);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map m : results.getMappedResults()) {
            output.add((Map<String, Object>) m);
        }
        return output;
    }

    public void updateRole(String userId, UserRole role) {
        Query query = new Query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .set("role", role)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
    }

    public void setActive(String userId, boolean isActive) {
        Query query = new Query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .set("isActive", isActive)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
    }

    public void updateLastLogin(String userId) {
        Query query = new Query(Criteria.where("_id").is(userId));
        Update update = new Update().set("lastLoginAt", Instant.now());
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
    }

    public void delete(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }

    public boolean isAdmin(String email) {
        Query query = new Query(Criteria.where("email").is(email)
                .and("role").in(UserRole.ADMIN, UserRole.SUPER_ADMIN));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

    public long countRecentlyActive(int minutes) {
        Instant cutoff = Instant.now().minus(minutes, ChronoUnit.MINUTES);
        Query query = new Query(Criteria.where("lastLoginAt").gte(cutoff));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    public long countLoggedInToday() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Query query = new Query(Criteria.where("lastLoginAt").gte(startOfDay));
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    public long countNewUsersThisMonth() {
        java.time.LocalDate now = java.time.LocalDate.now();
        Instant startOfMonth = now.withDayOfMonth(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        return countCreatedBetween(startOfMonth, Instant.now());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDailyRegistrationsThisMonth() {
        java.time.LocalDate now = java.time.LocalDate.now();
        Instant startOfMonth = now.withDayOfMonth(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(startOfMonth)),
                Aggregation.project()
                        .andExpression("dayOfMonth(createdAt)").as("day"),
                Aggregation.group("day").count().as("count"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project("count").and("_id").as("day")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, COLLECTION_NAME, Map.class);

        // Fill in missing days with 0
        int daysInMonth = now.lengthOfMonth();
        java.util.Map<Integer, Integer> dayCountMap = new java.util.HashMap<>();
        for (Map m : results.getMappedResults()) {
            int day = ((Number) m.get("day")).intValue();
            int count = ((Number) m.get("count")).intValue();
            dayCountMap.put(day, count);
        }

        List<Map<String, Object>> output = new ArrayList<>();
        for (int d = 1; d <= daysInMonth; d++) {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("day", d);
            entry.put("count", dayCountMap.getOrDefault(d, 0));
            output.add(entry);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMonthlyRegistrationsThisYear() {
        java.time.LocalDate now = java.time.LocalDate.now();
        Instant startOfYear = now.withDayOfYear(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("createdAt").gte(startOfYear)),
                Aggregation.project()
                        .andExpression("month(createdAt)").as("monthNum"),
                Aggregation.group("monthNum").count().as("count"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project("count").and("_id").as("monthNum")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, COLLECTION_NAME, Map.class);

        // Map month numbers to names and fill missing months
        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                               "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        java.util.Map<Integer, Integer> monthCountMap = new java.util.HashMap<>();
        for (Map m : results.getMappedResults()) {
            int monthNum = ((Number) m.get("monthNum")).intValue();
            int count = ((Number) m.get("count")).intValue();
            monthCountMap.put(monthNum, count);
        }

        List<Map<String, Object>> output = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("month", monthNames[m - 1]);
            entry.put("count", monthCountMap.getOrDefault(m, 0));
            output.add(entry);
        }
        return output;
    }
}
