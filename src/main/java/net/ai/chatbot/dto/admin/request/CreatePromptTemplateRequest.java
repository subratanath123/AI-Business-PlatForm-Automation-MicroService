package net.ai.chatbot.dto.admin.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import net.ai.chatbot.dto.admin.PromptTemplate.TemplateField;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreatePromptTemplateRequest {

    @NotBlank(message = "Template code is required")
    private String templateCode;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
    private String emoji;

    @NotBlank(message = "Category is required")
    private String category;

    private String subcategory;
    private String outputLabel;

    @NotBlank(message = "Prompt content is required")
    private String promptContent;

    private List<TemplateField> fields;

    private String outputFormat;
    private String exampleOutput;

    @JsonProperty("isActive")
    private boolean isActive;

    @JsonProperty("isPremium")
    private boolean isPremium;

    private List<String> requiredPlanIds;

    private String iconUrl;
    private String iconClass;

    private List<String> tags;
    private Integer displayOrder;
}
