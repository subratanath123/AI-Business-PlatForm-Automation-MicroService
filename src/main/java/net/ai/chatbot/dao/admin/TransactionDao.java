package net.ai.chatbot.dao.admin;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.admin.PaymentGateway;
import net.ai.chatbot.dto.admin.Transaction;
import net.ai.chatbot.dto.admin.TransactionStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TransactionDao {

    private final MongoTemplate mongoTemplate;

    public Transaction save(Transaction transaction) {
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(Instant.now());
        }
        transaction.setUpdatedAt(Instant.now());
        return mongoTemplate.save(transaction);
    }

    public Optional<Transaction> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, Transaction.class));
    }

    public Optional<Transaction> findByTransactionNumber(String transactionNumber) {
        Query query = new Query(Criteria.where("transactionNumber").is(transactionNumber));
        return Optional.ofNullable(mongoTemplate.findOne(query, Transaction.class));
    }

    public List<Transaction> findByUserId(String userId, int page, int size) {
        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Transaction.class);
    }

    public List<Transaction> findByUserEmail(String userEmail) {
        Query query = new Query(Criteria.where("userEmail").is(userEmail))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, Transaction.class);
    }

    public List<Transaction> findAll(int page, int size, TransactionStatus status,
                                      PaymentGateway gateway, String search) {
        Query query = buildSearchQuery(status, gateway, search);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Transaction.class);
    }

    public long count(TransactionStatus status, PaymentGateway gateway, String search) {
        Query query = buildSearchQuery(status, gateway, search);
        return mongoTemplate.count(query, Transaction.class);
    }

    private Query buildSearchQuery(TransactionStatus status, PaymentGateway gateway, String search) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (gateway != null) {
            query.addCriteria(Criteria.where("gateway").is(gateway));
        }
        if (search != null && !search.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("transactionNumber").regex(search, "i"),
                    Criteria.where("userEmail").regex(search, "i"),
                    Criteria.where("planName").regex(search, "i")
            ));
        }
        return query;
    }

    public long countByStatus(TransactionStatus status) {
        Query query = new Query(Criteria.where("status").is(status));
        return mongoTemplate.count(query, Transaction.class);
    }

    public BigDecimal sumTotalAmount(TransactionStatus status) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(status)),
                Aggregation.group().sum("totalAmount").as("total")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "transactions", Map.class);

        Map result = results.getUniqueMappedResult();
        if (result == null || result.get("total") == null) {
            return BigDecimal.ZERO;
        }
        Object total = result.get("total");
        if (total instanceof Number) {
            return BigDecimal.valueOf(((Number) total).doubleValue());
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal sumTotalAmountBetween(TransactionStatus status, Instant start, Instant end) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(status)
                        .and("createdAt").gte(start).lte(end)),
                Aggregation.group().sum("totalAmount").as("total")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "transactions", Map.class);

        Map result = results.getUniqueMappedResult();
        if (result == null || result.get("total") == null) {
            return BigDecimal.ZERO;
        }
        Object total = result.get("total");
        if (total instanceof Number) {
            return BigDecimal.valueOf(((Number) total).doubleValue());
        }
        return BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRevenueByDay(int days) {
        Instant startDate = Instant.now().minus(days, ChronoUnit.DAYS);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(TransactionStatus.COMPLETED)
                        .and("createdAt").gte(startDate)),
                Aggregation.project()
                        .andExpression("dateToString('%Y-%m-%d', createdAt)").as("date")
                        .and("totalAmount").as("amount"),
                Aggregation.group("date")
                        .sum("amount").as("revenue")
                        .count().as("transactionCount"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project("revenue", "transactionCount")
                        .and("_id").as("date")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation, "transactions", Map.class);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map m : results.getMappedResults()) {
            output.add((Map<String, Object>) m);
        }
        return output;
    }

    public void updateStatus(String transactionId, TransactionStatus status) {
        Query query = new Query(Criteria.where("_id").is(transactionId));
        Update update = new Update()
                .set("status", status)
                .set("updatedAt", Instant.now());

        if (status == TransactionStatus.COMPLETED) {
            update.set("paidAt", Instant.now());
        } else if (status == TransactionStatus.REFUNDED) {
            update.set("refundedAt", Instant.now());
        }

        mongoTemplate.updateFirst(query, update, Transaction.class);
    }

    public String generateTransactionNumber() {
        String prefix = "TXN-" + LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
        long count = mongoTemplate.count(new Query(), Transaction.class);
        return prefix + "-" + String.format("%06d", count + 1);
    }

    public void delete(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, Transaction.class);
    }
}
