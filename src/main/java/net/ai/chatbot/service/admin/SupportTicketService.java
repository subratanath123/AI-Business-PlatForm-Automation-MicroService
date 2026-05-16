package net.ai.chatbot.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.SupportTicketDao;
import net.ai.chatbot.dto.admin.*;
import net.ai.chatbot.dto.admin.request.CreateTicketRequest;
import net.ai.chatbot.dto.admin.request.TicketMessageRequest;
import net.ai.chatbot.dto.admin.request.UpdateTicketRequest;
import net.ai.chatbot.dto.admin.response.TicketListResponse;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketDao supportTicketDao;

    public SupportTicket createTicket(CreateTicketRequest request) {
        String userId = AuthUtils.getUserId();
        String userEmail = AuthUtils.getEmail();
        String userName = AuthUtils.getUser().getUserName();

        SupportTicket ticket = SupportTicket.builder()
                .ticketNumber(supportTicketDao.generateTicketNumber())
                .userId(userId)
                .userEmail(userEmail)
                .userName(userName)
                .subject(request.getSubject())
                .description(request.getDescription())
                .category(request.getCategory() != null ? request.getCategory() : TicketCategory.GENERAL_INQUIRY)
                .priority(request.getPriority() != null ? request.getPriority() : TicketPriority.MEDIUM)
                .status(TicketStatus.OPEN)
                .attachmentUrls(request.getAttachmentUrls())
                .build();

        TicketMessage initialMessage = TicketMessage.builder()
                .id(UUID.randomUUID().toString())
                .senderId(userId)
                .senderEmail(userEmail)
                .senderName(userName)
                .isAdmin(false)
                .content(request.getDescription())
                .attachmentUrls(request.getAttachmentUrls())
                .sentAt(Instant.now())
                .isRead(false)
                .build();

        ticket.getMessages().add(initialMessage);

        log.info("Creating support ticket {} for user {}", ticket.getTicketNumber(), userEmail);
        return supportTicketDao.save(ticket);
    }

    public SupportTicket getTicketById(String ticketId) {
        return supportTicketDao.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));
    }

    public SupportTicket getTicketByNumber(String ticketNumber) {
        return supportTicketDao.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketNumber));
    }

    public TicketListResponse getTicketsForUser(int page, int size) {
        String userId = AuthUtils.getUserId();
        List<SupportTicket> tickets = supportTicketDao.findByUserId(userId, page, size);
        long totalCount = supportTicketDao.countByUserId(userId);

        return TicketListResponse.builder()
                .tickets(tickets)
                .totalCount(totalCount)
                .page(page)
                .pageSize(size)
                .totalPages((int) Math.ceil((double) totalCount / size))
                .build();
    }

    public TicketListResponse getAllTickets(int page, int size, TicketStatus status, String search) {
        List<SupportTicket> tickets = supportTicketDao.findAll(page, size, status, search);
        long totalCount = supportTicketDao.count(status, search);

        return TicketListResponse.builder()
                .tickets(tickets)
                .totalCount(totalCount)
                .page(page)
                .pageSize(size)
                .totalPages((int) Math.ceil((double) totalCount / size))
                .openCount(supportTicketDao.countByStatus(TicketStatus.OPEN))
                .inProgressCount(supportTicketDao.countByStatus(TicketStatus.IN_PROGRESS))
                .pendingCount(supportTicketDao.countByStatus(TicketStatus.PENDING))
                .resolvedCount(supportTicketDao.countByStatus(TicketStatus.RESOLVED))
                .closedCount(supportTicketDao.countByStatus(TicketStatus.CLOSED))
                .build();
    }

    public SupportTicket updateTicket(String ticketId, UpdateTicketRequest request) {
        SupportTicket ticket = getTicketById(ticketId);

        if (request.getStatus() != null) {
            ticket.setStatus(request.getStatus());
            if (request.getStatus() == TicketStatus.RESOLVED) {
                ticket.setResolvedAt(Instant.now());
            } else if (request.getStatus() == TicketStatus.CLOSED) {
                ticket.setClosedAt(Instant.now());
            }
        }

        if (request.getPriority() != null) {
            ticket.setPriority(request.getPriority());
        }

        if (request.getCategory() != null) {
            ticket.setCategory(request.getCategory());
        }

        if (request.getAssignedTo() != null) {
            ticket.setAssignedTo(request.getAssignedTo());
            ticket.setAssignedToEmail(request.getAssignedToEmail());
        }

        if (request.getResolutionNotes() != null) {
            ticket.setResolutionNotes(request.getResolutionNotes());
        }

        if (request.getTags() != null) {
            ticket.setTags(request.getTags());
        }

        log.info("Updating ticket {} with status {}", ticket.getTicketNumber(), ticket.getStatus());
        return supportTicketDao.save(ticket);
    }

    public SupportTicket addMessage(String ticketId, TicketMessageRequest request, boolean isAdmin) {
        SupportTicket ticket = getTicketById(ticketId);

        String userId = AuthUtils.getUserId();
        String userEmail = AuthUtils.getEmail();
        String userName = AuthUtils.getUser().getUserName();

        TicketMessage message = TicketMessage.builder()
                .id(UUID.randomUUID().toString())
                .senderId(userId)
                .senderEmail(userEmail)
                .senderName(userName)
                .isAdmin(isAdmin)
                .content(request.getContent())
                .attachmentUrls(request.getAttachmentUrls())
                .sentAt(Instant.now())
                .isRead(false)
                .build();

        ticket.getMessages().add(message);

        if (isAdmin && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }

        log.info("Adding message to ticket {} by {}", ticket.getTicketNumber(), userEmail);
        return supportTicketDao.save(ticket);
    }

    public SupportTicket submitFeedback(String ticketId, Integer rating, String comment) {
        SupportTicket ticket = getTicketById(ticketId);
        ticket.setSatisfactionRating(rating);
        ticket.setFeedbackComment(comment);
        return supportTicketDao.save(ticket);
    }

    public void deleteTicket(String ticketId) {
        log.info("Deleting ticket {}", ticketId);
        supportTicketDao.delete(ticketId);
    }
}
