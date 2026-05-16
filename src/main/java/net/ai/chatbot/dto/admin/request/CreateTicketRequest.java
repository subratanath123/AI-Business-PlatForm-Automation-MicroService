package net.ai.chatbot.dto.admin.request;

import lombok.*;
import net.ai.chatbot.dto.admin.TicketCategory;
import net.ai.chatbot.dto.admin.TicketPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateTicketRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must be at most 200 characters")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description must be at most 5000 characters")
    private String description;

    private TicketCategory category;
    private TicketPriority priority;
    private List<String> attachmentUrls;
}
