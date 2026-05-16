package net.ai.chatbot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.SupportTicket;
import net.ai.chatbot.dto.admin.TicketStatus;
import net.ai.chatbot.dto.admin.request.CreateTicketRequest;
import net.ai.chatbot.dto.admin.request.TicketMessageRequest;
import net.ai.chatbot.dto.admin.request.UpdateTicketRequest;
import net.ai.chatbot.dto.admin.response.TicketListResponse;
import net.ai.chatbot.service.admin.SupportTicketService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/support")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicket> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        try {
            SupportTicket ticket = supportTicketService.createTicket(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
        } catch (Exception e) {
            log.error("Error creating ticket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/tickets")
    public ResponseEntity<TicketListResponse> getMyTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            TicketListResponse response = supportTicketService.getTicketsForUser(page, size);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching user tickets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/tickets/{ticketId}")
    public ResponseEntity<SupportTicket> getTicket(@PathVariable String ticketId) {
        try {
            SupportTicket ticket = supportTicketService.getTicketById(ticketId);
            String userId = AuthUtils.getUserId();
            if (!ticket.getUserId().equals(userId) && !AuthUtils.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(ticket);
        } catch (Exception e) {
            log.error("Error fetching ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/tickets/{ticketId}/messages")
    public ResponseEntity<SupportTicket> addMessage(
            @PathVariable String ticketId,
            @Valid @RequestBody TicketMessageRequest request) {
        try {
            SupportTicket ticket = supportTicketService.getTicketById(ticketId);
            String userId = AuthUtils.getUserId();
            boolean isAdmin = AuthUtils.isAdmin();

            if (!ticket.getUserId().equals(userId) && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            SupportTicket updated = supportTicketService.addMessage(ticketId, request, isAdmin);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error adding message to ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/tickets/{ticketId}/feedback")
    public ResponseEntity<SupportTicket> submitFeedback(
            @PathVariable String ticketId,
            @RequestBody Map<String, Object> feedback) {
        try {
            Integer rating = (Integer) feedback.get("rating");
            String comment = (String) feedback.get("comment");
            SupportTicket updated = supportTicketService.submitFeedback(ticketId, rating, comment);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error submitting feedback for ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
