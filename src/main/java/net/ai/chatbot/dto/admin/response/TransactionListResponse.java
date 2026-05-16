package net.ai.chatbot.dto.admin.response;

import lombok.*;
import net.ai.chatbot.dto.admin.Transaction;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionListResponse {

    private List<Transaction> transactions;
    private long totalCount;
    private int page;
    private int pageSize;
    private int totalPages;

    private BigDecimal totalAmount;
    private long completedCount;
    private long pendingCount;
    private long failedCount;
    private long refundedCount;
}
