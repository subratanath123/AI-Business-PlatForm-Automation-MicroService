package net.ai.chatbot.dao.admin;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.admin.SupportTicket;
import net.ai.chatbot.dto.admin.TicketStatus;
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
public class SupportTicketDao {

    private final MongoTemplate mongoTemplate;

    public SupportTicket save(SupportTicket ticket) {
        if (ticket.getCreatedAt() == null) {
            ticket.setCreatedAt(Instant.now());
        }
        ticket.setUpdatedAt(Instant.now());
        return mongoTemplate.save(ticket);
    }

    public Optional<SupportTicket> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, SupportTicket.class));
    }

    public Optional<SupportTicket> findByTicketNumber(String ticketNumber) {
        Query query = new Query(Criteria.where("ticketNumber").is(ticketNumber));
        return Optional.ofNullable(mongoTemplate.findOne(query, SupportTicket.class));
    }

    public List<SupportTicket> findByUserId(String userId, int page, int size) {
        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, SupportTicket.class);
    }

    public List<SupportTicket> findAll(int page, int size, TicketStatus status, String search) {
        Query query = new Query();

        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }

        if (search != null && !search.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("ticketNumber").regex(search, "i"),
                    Criteria.where("subject").regex(search, "i"),
                    Criteria.where("userEmail").regex(search, "i")
            ));
        }

        query.with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);

        return mongoTemplate.find(query, SupportTicket.class);
    }

    public long count(TicketStatus status, String search) {
        Query query = new Query();

        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }

        if (search != null && !search.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("ticketNumber").regex(search, "i"),
                    Criteria.where("subject").regex(search, "i"),
                    Criteria.where("userEmail").regex(search, "i")
            ));
        }

        return mongoTemplate.count(query, SupportTicket.class);
    }

    public long countByStatus(TicketStatus status) {
        Query query = new Query(Criteria.where("status").is(status));
        return mongoTemplate.count(query, SupportTicket.class);
    }

    public long countByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        return mongoTemplate.count(query, SupportTicket.class);
    }

    public void updateStatus(String ticketId, TicketStatus status) {
        Query query = new Query(Criteria.where("_id").is(ticketId));
        Update update = new Update()
                .set("status", status)
                .set("updatedAt", Instant.now());

        if (status == TicketStatus.RESOLVED) {
            update.set("resolvedAt", Instant.now());
        } else if (status == TicketStatus.CLOSED) {
            update.set("closedAt", Instant.now());
        }

        mongoTemplate.updateFirst(query, update, SupportTicket.class);
    }

    public String generateTicketNumber() {
        long count = mongoTemplate.count(new Query(), SupportTicket.class);
        return String.format("TKT-%06d", count + 1);
    }

    public void delete(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, SupportTicket.class);
    }
}
