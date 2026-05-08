package net.ai.chatbot.service.dashboard;

import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.dashboard.TeamDashboardSummary;
import net.ai.chatbot.entity.TeamMembership;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the team-membership summary for the current owner. The
 * {@code team_memberships} collection is keyed by {@code ownerEmail}, which
 * matches the JWT email of the signed-in owner.
 */
@Service
@Slf4j
public class TeamDashboardService {

    private final MongoTemplate mongoTemplate;

    public TeamDashboardService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public TeamDashboardSummary getSummary() {
        String ownerEmail = normalize(AuthUtils.getEmail());
        if (ownerEmail == null) {
            return TeamDashboardSummary.builder()
                    .size(0).byRole(Map.of()).recent(List.of()).build();
        }

        long size = mongoTemplate.count(
                new Query(Criteria.where("ownerEmail").is(ownerEmail)),
                TeamMembership.class);

        Map<String, Long> byRole = countByRole(ownerEmail);

        List<TeamMembership> recentDocs = mongoTemplate.find(
                new Query(Criteria.where("ownerEmail").is(ownerEmail))
                        .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .limit(5),
                TeamMembership.class);

        List<TeamDashboardSummary.RecentMember> recent = recentDocs.stream()
                .map(m -> TeamDashboardSummary.RecentMember.builder()
                        .memberEmail(m.getMemberEmail())
                        .role(m.getRole())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return TeamDashboardSummary.builder()
                .size(size)
                .byRole(byRole)
                .recent(recent)
                .build();
    }

    private Map<String, Long> countByRole(String ownerEmail) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("ownerEmail").is(ownerEmail)),
                Aggregation.group("role").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, TeamMembership.class, Map.class);
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map<?, ?> row : results.getMappedResults()) {
            Object role = row.get("_id");
            Object count = row.get("count");
            if (role != null && count instanceof Number n) {
                out.put(String.valueOf(role), n.longValue());
            }
        }
        return out;
    }

    private static String normalize(String email) {
        if (email == null) return null;
        String trimmed = email.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
