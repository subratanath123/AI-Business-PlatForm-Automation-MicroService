package net.ai.chatbot.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.TransactionDao;
import net.ai.chatbot.dto.admin.*;
import net.ai.chatbot.dto.admin.response.TransactionListResponse;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionDao transactionDao;

    public Transaction createTransaction(String userId, String userEmail, String planId,
                                          String planName, BigDecimal amount, BigDecimal discount,
                                          PaymentGateway gateway, PricingType pricingType,
                                          String promoCode) {
        String userName = AuthUtils.getUser() != null ? AuthUtils.getUser().getUserName() : null;

        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = amount.subtract(discount != null ? discount : BigDecimal.ZERO);

        Transaction transaction = Transaction.builder()
                .transactionNumber(transactionDao.generateTransactionNumber())
                .userId(userId)
                .userEmail(userEmail)
                .userName(userName)
                .status(TransactionStatus.PENDING)
                .planId(planId)
                .planName(planName)
                .amount(amount)
                .discountAmount(discount)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .currency("USD")
                .gateway(gateway)
                .pricingType(pricingType)
                .promoCodeUsed(promoCode)
                .build();

        log.info("Creating transaction {} for user {}", transaction.getTransactionNumber(), userEmail);
        return transactionDao.save(transaction);
    }

    public Transaction getTransactionById(String id) {
        return transactionDao.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));
    }

    public Transaction getTransactionByNumber(String transactionNumber) {
        return transactionDao.findByTransactionNumber(transactionNumber)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionNumber));
    }

    public TransactionListResponse getTransactionsForUser(int page, int size) {
        String userId = AuthUtils.getUserId();
        List<Transaction> transactions = transactionDao.findByUserId(userId, page, size);

        return TransactionListResponse.builder()
                .transactions(transactions)
                .page(page)
                .pageSize(size)
                .build();
    }

    public TransactionListResponse getAllTransactions(int page, int size, TransactionStatus status,
                                                       PaymentGateway gateway, String search) {
        List<Transaction> transactions = transactionDao.findAll(page, size, status, gateway, search);
        long totalCount = transactionDao.count(status, gateway, search);

        BigDecimal totalAmount = transactionDao.sumTotalAmount(TransactionStatus.COMPLETED);

        return TransactionListResponse.builder()
                .transactions(transactions)
                .totalCount(totalCount)
                .page(page)
                .pageSize(size)
                .totalPages((int) Math.ceil((double) totalCount / size))
                .totalAmount(totalAmount)
                .completedCount(transactionDao.countByStatus(TransactionStatus.COMPLETED))
                .pendingCount(transactionDao.countByStatus(TransactionStatus.PENDING))
                .failedCount(transactionDao.countByStatus(TransactionStatus.FAILED))
                .refundedCount(transactionDao.countByStatus(TransactionStatus.REFUNDED))
                .build();
    }

    public Transaction updateTransactionStatus(String transactionId, TransactionStatus status) {
        Transaction transaction = getTransactionById(transactionId);
        transaction.setStatus(status);

        if (status == TransactionStatus.COMPLETED) {
            transaction.setPaidAt(Instant.now());
        } else if (status == TransactionStatus.REFUNDED) {
            transaction.setRefundedAt(Instant.now());
        }

        log.info("Updating transaction {} status to {}", transaction.getTransactionNumber(), status);
        return transactionDao.save(transaction);
    }

    public Transaction updateGatewayDetails(String transactionId, String gatewayTransactionId,
                                             String gatewayPaymentIntentId, String gatewayCustomerId) {
        Transaction transaction = getTransactionById(transactionId);
        transaction.setGatewayTransactionId(gatewayTransactionId);
        transaction.setGatewayPaymentIntentId(gatewayPaymentIntentId);
        transaction.setGatewayCustomerId(gatewayCustomerId);
        return transactionDao.save(transaction);
    }

    public Transaction processRefund(String transactionId, BigDecimal refundAmount, String reason) {
        Transaction transaction = getTransactionById(transactionId);

        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new RuntimeException("Can only refund completed transactions");
        }

        if (refundAmount == null) {
            refundAmount = transaction.getTotalAmount();
        }

        if (refundAmount.compareTo(transaction.getTotalAmount()) > 0) {
            throw new RuntimeException("Refund amount cannot exceed transaction total");
        }

        transaction.setStatus(TransactionStatus.REFUNDED);
        transaction.setRefundedAt(Instant.now());
        transaction.setRefundAmount(refundAmount);
        transaction.setRefundReason(reason);

        log.info("Processing refund for transaction {} - amount: {}", 
                transaction.getTransactionNumber(), refundAmount);
        return transactionDao.save(transaction);
    }

    public void setInvoiceAndReceipt(String transactionId, String invoiceUrl, String receiptUrl) {
        Transaction transaction = getTransactionById(transactionId);
        transaction.setInvoiceUrl(invoiceUrl);
        transaction.setReceiptUrl(receiptUrl);
        transactionDao.save(transaction);
    }

    public BigDecimal getTotalRevenue() {
        return transactionDao.sumTotalAmount(TransactionStatus.COMPLETED);
    }

    public List<java.util.Map<String, Object>> getRevenueByDay(int days) {
        return transactionDao.getRevenueByDay(days);
    }
}
