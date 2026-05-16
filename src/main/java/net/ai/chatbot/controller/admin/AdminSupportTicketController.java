package net.ai.chatbot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.SupportTicket;
import net.ai.chatbot.dto.admin.TicketStatus;
import net.ai.chatbot.dto.admin.request.TicketMessageRequest;
import net.ai.chatbot.dto.admin.request.UpdateTicketRequest;
import net.ai.chatbot.dto.admin.response.TicketListResponse;
import net.ai.chatbot.service.admin.SupportTicketService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api/admin/support")
@RequiredArgsConstructor
public class AdminSupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping("/tickets")
    public ResponseEntity<TicketListResponse> getAllTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) String search) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            TicketListResponse response = supportTicketService.getAllTickets(page, size, status, search);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching all tickets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/tickets/{ticketId}")
    public ResponseEntity<SupportTicket> getTicket(@PathVariable String ticketId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            SupportTicket ticket = supportTicketService.getTicketById(ticketId);
            return ResponseEntity.ok(ticket);
        } catch (Exception e) {
            log.error("Error fetching ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/tickets/{ticketId}")
    public ResponseEntity<SupportTicket> updateTicket(
            @PathVariable String ticketId,
            @RequestBody UpdateTicketRequest request) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            SupportTicket updated = supportTicketService.updateTicket(ticketId, request);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error updating ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/tickets/{ticketId}/reply")
    public ResponseEntity<SupportTicket> replyToTicket(
            @PathVariable String ticketId,
            @Valid @RequestBody TicketMessageRequest request) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            SupportTicket updated = supportTicketService.addMessage(ticketId, request, true);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error replying to ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/tickets/{ticketId}/status")
    public ResponseEntity<SupportTicket> updateTicketStatus(
            @PathVariable String ticketId,
            @RequestBody UpdateTicketRequest request) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            SupportTicket updated = supportTicketService.updateTicket(ticketId, request);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error updating ticket status {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/tickets/{ticketId}")
    public ResponseEntity<Void> deleteTicket(@PathVariable String ticketId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            supportTicketService.deleteTicket(ticketId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
