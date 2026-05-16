package net.ai.chatbot.dto.admin.request;

import lombok.*;
import net.ai.chatbot.dto.admin.TicketCategory;
import net.ai.chatbot.dto.admin.TicketPriority;
import net.ai.chatbot.dto.admin.TicketStatus;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateTicketRequest {

    private TicketStatus status;
    private TicketPriority priority;
    private TicketCategory category;
    private String assignedTo;
    private String assignedToEmail;
    private String resolutionNotes;
    private List<String> tags;
}
