package net.ai.chatbot.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dto.admin.PaymentGateway;
import net.ai.chatbot.dto.admin.Transaction;
import net.ai.chatbot.dto.admin.TransactionStatus;
import net.ai.chatbot.dto.admin.response.TransactionListResponse;
import net.ai.chatbot.service.admin.TransactionService;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true", allowedHeaders = "*")
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping("/transactions")
    public ResponseEntity<TransactionListResponse> getMyTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            TransactionListResponse response = transactionService.getTransactionsForUser(page, size);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching user transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable String transactionId) {
        try {
            Transaction transaction = transactionService.getTransactionById(transactionId);
            String userId = AuthUtils.getUserId();
            if (!transaction.getUserId().equals(userId) && !AuthUtils.isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(transaction);
        } catch (Exception e) {
            log.error("Error fetching transaction {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/admin/transactions")
    public ResponseEntity<TransactionListResponse> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) PaymentGateway gateway,
            @RequestParam(required = false) String search) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            TransactionListResponse response = transactionService.getAllTransactions(
                    page, size, status, gateway, search);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching all transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/admin/transactions/{transactionId}")
    public ResponseEntity<Transaction> getTransactionAdmin(@PathVariable String transactionId) {
        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Transaction transaction = transactionService.getTransactionById(transactionId);
            return ResponseEntity.ok(transaction);
        } catch (Exception e) {
            log.error("Error fetching transaction {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/admin/transactions/{transactionId}/status")
    public ResponseEntity<Transaction> updateTransactionStatus(
            @PathVariable String transactionId,
            @RequestBody Map<String, String> body) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            TransactionStatus status = TransactionStatus.valueOf(body.get("status"));
            Transaction transaction = transactionService.updateTransactionStatus(transactionId, status);
            return ResponseEntity.ok(transaction);
        } catch (Exception e) {
            log.error("Error updating transaction status {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/admin/transactions/{transactionId}/refund")
    public ResponseEntity<Transaction> processRefund(
            @PathVariable String transactionId,
            @RequestBody Map<String, Object> body) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            BigDecimal refundAmount = body.get("amount") != null ?
                    new BigDecimal(body.get("amount").toString()) : null;
            String reason = (String) body.get("reason");

            Transaction transaction = transactionService.processRefund(transactionId, refundAmount, reason);
            return ResponseEntity.ok(transaction);
        } catch (Exception e) {
            log.error("Error processing refund for transaction {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/admin/transactions/revenue")
    public ResponseEntity<Map<String, Object>> getRevenueSummary(
            @RequestParam(defaultValue = "30") int days) {

        if (!AuthUtils.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            BigDecimal totalRevenue = transactionService.getTotalRevenue();
            List<Map<String, Object>> revenueByDay = transactionService.getRevenueByDay(days);

            Map<String, Object> response = Map.of(
                    "totalRevenue", totalRevenue,
                    "revenueByDay", revenueByDay
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching revenue summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
