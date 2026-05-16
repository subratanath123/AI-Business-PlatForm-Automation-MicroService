package net.ai.chatbot.dao.admin;

import lombok.RequiredArgsConstructor;
import net.ai.chatbot.dto.admin.ContentViolation;
import net.ai.chatbot.dto.admin.GuardrailSettings;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GuardrailDao {

    private final MongoTemplate mongoTemplate;

    // ═══════════════════════════════════════════════════════════════════════════
    // GUARDRAIL SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    public GuardrailSettings getSettings() {
        List<GuardrailSettings> settings = mongoTemplate.findAll(GuardrailSettings.class);
        if (settings.isEmpty()) {
            return createDefaultSettings();
        }
        return settings.get(0);
    }

    public GuardrailSettings saveSettings(GuardrailSettings settings) {
        if (settings.getCreatedAt() == null) {
            settings.setCreatedAt(Instant.now());
        }
        settings.setUpdatedAt(Instant.now());
        return mongoTemplate.save(settings);
    }

    private GuardrailSettings createDefaultSettings() {
        GuardrailSettings settings = GuardrailSettings.builder()
                .isEnabled(true)
                .strictnessLevel("MEDIUM")
                .violationAction("BLOCK")
                .blockViolence(true)
                .blockAdultContent(true)
                .blockHateSpeech(true)
                .blockSelfHarm(true)
                .blockIllegalContent(true)
                .blockPII(true)
                .blockCopyrightedContent(true)
                .imageGenerationEnabled(true)
                .videoGenerationEnabled(true)
                .contentGenerationEnabled(true)
                .codeGenerationEnabled(true)
                .rateLimitEnabled(true)
                .logViolations(true)
                .alertOnViolation(true)
                .autoBlockEnabled(true)
                .build();
        return saveSettings(settings);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTENT VIOLATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    public ContentViolation saveViolation(ContentViolation violation) {
        if (violation.getCreatedAt() == null) {
            violation.setCreatedAt(Instant.now());
        }
        return mongoTemplate.save(violation);
    }

    public Optional<ContentViolation> findViolationById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, ContentViolation.class));
    }

    public List<ContentViolation> findViolationsByUserId(String userId, int page, int size) {
        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ContentViolation.class);
    }

    public List<ContentViolation> findRecentViolations(int page, int size) {
        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ContentViolation.class);
    }

    public List<ContentViolation> findUnreviewedViolations(int page, int size) {
        Query query = new Query(Criteria.where("reviewed").is(false))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ContentViolation.class);
    }

    public long countViolations() {
        return mongoTemplate.count(new Query(), ContentViolation.class);
    }

    public long countUnreviewedViolations() {
        return mongoTemplate.count(new Query(Criteria.where("reviewed").is(false)), ContentViolation.class);
    }

    public long countUserViolations(String userId) {
        return mongoTemplate.count(new Query(Criteria.where("userId").is(userId)), ContentViolation.class);
    }

    public long countUserViolationsInPeriod(String userId, int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        Query query = new Query(Criteria.where("userId").is(userId)
                .and("createdAt").gte(since));
        return mongoTemplate.count(query, ContentViolation.class);
    }

    public long countViolationsByType(String violationType) {
        return mongoTemplate.count(new Query(Criteria.where("violationType").is(violationType)), ContentViolation.class);
    }

    public List<ContentViolation> findViolationsByType(String violationType, int page, int size) {
        Query query = new Query(Criteria.where("violationType").is(violationType))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ContentViolation.class);
    }

    public void markAsReviewed(String id, String reviewedBy, String notes, boolean falsePositive) {
        ContentViolation violation = mongoTemplate.findById(id, ContentViolation.class);
        if (violation != null) {
            violation.setReviewed(true);
            violation.setReviewedBy(reviewedBy);
            violation.setReviewedAt(Instant.now());
            violation.setReviewNotes(notes);
            violation.setFalsePositive(falsePositive);
            mongoTemplate.save(violation);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════════════════

    public long countViolationsToday() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return mongoTemplate.count(new Query(Criteria.where("createdAt").gte(startOfDay)), ContentViolation.class);
    }

    public long countViolationsThisWeek() {
        Instant startOfWeek = Instant.now().minus(7, ChronoUnit.DAYS);
        return mongoTemplate.count(new Query(Criteria.where("createdAt").gte(startOfWeek)), ContentViolation.class);
    }

    public long countViolationsThisMonth() {
        Instant startOfMonth = Instant.now().minus(30, ChronoUnit.DAYS);
        return mongoTemplate.count(new Query(Criteria.where("createdAt").gte(startOfMonth)), ContentViolation.class);
    }
}
