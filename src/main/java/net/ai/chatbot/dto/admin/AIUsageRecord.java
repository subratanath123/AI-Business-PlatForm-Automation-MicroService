package net.ai.chatbot.dto.admin;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Records individual AI feature usage for tracking and billing purposes.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "ai_usage_records")
@CompoundIndex(name = "user_feature_date", def = "{'userId': 1, 'featureType': 1, 'createdAt': -1}")
public class AIUsageRecord {

    @Id
    private String id;

    @Indexed
    private String userId;
    
    private String userEmail;

    @Indexed
    private String featureType;

    private String model;

    private int inputTokens;
    private int outputTokens;
    private int totalTokens;

    private BigDecimal estimatedCost;
    private String currency;

    private int durationMs;

    private boolean success;
    private String errorMessage;

    private String requestId;
    private String sessionId;

    private String metadata;

    @Indexed
    private Instant createdAt;
}
