package net.ai.chatbot.dto.admin;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "support_tickets")
public class SupportTicket {

    @Id
    private String id;

    @Indexed
    private String ticketNumber;

    @Indexed
    private String userId;

    private String userEmail;
    private String userName;

    private String subject;
    private String description;

    @Builder.Default
    private TicketCategory category = TicketCategory.GENERAL_INQUIRY;

    @Builder.Default
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Builder.Default
    private TicketStatus status = TicketStatus.OPEN;

    private String assignedTo;
    private String assignedToEmail;

    @Builder.Default
    private List<TicketMessage> messages = new ArrayList<>();

    @Builder.Default
    private List<String> attachmentUrls = new ArrayList<>();

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;
    private Instant resolvedAt;
    private Instant closedAt;

    private String resolutionNotes;

    private Integer satisfactionRating;
    private String feedbackComment;
}
