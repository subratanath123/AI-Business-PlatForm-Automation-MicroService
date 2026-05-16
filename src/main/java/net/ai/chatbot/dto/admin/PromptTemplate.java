package net.ai.chatbot.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "prompt_templates")
public class PromptTemplate {

    @Id
    private String id;

    @Indexed(unique = true)
    private String templateCode;

    private String name;
    private String description;

    @Indexed
    private String category;

    private String subcategory;

    // Emoji icon for display (e.g., "💡", "📧")
    private String emoji;

    // Label for the output section (e.g., "Blog Ideas & Outline")
    private String outputLabel;

    // The actual prompt template with {{variable}} placeholders
    private String promptContent;

    // Form fields for user input
    @Builder.Default
    private List<TemplateField> fields = new ArrayList<>();

    private String outputFormat;
    private String exampleOutput;

    @Field("isActive")
    @JsonProperty("isActive")
    @Builder.Default
    private boolean isActive = true;

    @Field("isPremium")
    @JsonProperty("isPremium")
    @Builder.Default
    private boolean isPremium = false;

    @Builder.Default
    private List<String> requiredPlanIds = new ArrayList<>();

    private String iconUrl;
    private String iconClass;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private Integer displayOrder;

    @Builder.Default
    private Integer usageCount = 0;

    private Double averageRating;
    private Integer ratingCount;

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TemplateField {
        private String id;
        private String label;
        private String placeholder;
        private String type; // text, textarea, select
        private boolean required;
        private Integer maxLength;
        private Integer rows; // for textarea
        private List<String> options; // for select
        private String defaultValue;
    }
}
