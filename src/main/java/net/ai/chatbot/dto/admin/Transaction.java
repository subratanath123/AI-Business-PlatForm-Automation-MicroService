package net.ai.chatbot.dto.admin;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "transactions")
public class Transaction {

    @Id
    private String id;

    @Indexed
    private String transactionNumber;

    @Indexed
    private String userId;

    private String userEmail;
    private String userName;

    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    private String planId;
    private String planName;

    private BigDecimal amount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String currency;

    @Builder.Default
    private PaymentGateway gateway = PaymentGateway.STRIPE;

    private String gatewayTransactionId;
    private String gatewayPaymentIntentId;
    private String gatewayCustomerId;

    private String promoCodeUsed;

    private PricingType pricingType;

    private String subscriptionId;

    private Instant paidAt;
    private Instant refundedAt;
    private String refundReason;
    private String refundedBy;
    private BigDecimal refundAmount;

    private String invoiceUrl;
    private String receiptUrl;

    private String billingName;
    private String billingEmail;
    private String billingAddress;
    private String billingCountry;

    private String ipAddress;
    private String userAgent;

    private Instant createdAt;
    private Instant updatedAt;
}
