package net.ai.chatbot.dto.admin.response;

import lombok.*;
import net.ai.chatbot.dto.admin.SupportTicket;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketListResponse {

    private List<SupportTicket> tickets;
    private long totalCount;
    private int page;
    private int pageSize;
    private int totalPages;

    private long openCount;
    private long inProgressCount;
    private long pendingCount;
    private long resolvedCount;
    private long closedCount;
}
