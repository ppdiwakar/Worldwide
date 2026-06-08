package com.worldwide.paymentgateway.domain.entity;

import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import com.worldwide.paymentgateway.domain.enums.CaptureMode;
import com.worldwide.paymentgateway.domain.enums.CardNetwork;
import com.worldwide.paymentgateway.domain.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_txn_status", columnList = "status"),
        @Index(name = "idx_txn_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionStatus status;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "card_token", length = 255)
    private String cardToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_network", length = 32)
    private CardNetwork cardNetwork;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_mode", nullable = false, length = 32)
    @Builder.Default
    private CaptureMode captureMode = CaptureMode.AUTOMATIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquirer_type", length = 64)
    private AcquirerType acquirerType;

    @Column(name = "acquirer_txn_id", length = 255)
    private String acquirerTxnId;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "fraud_score", nullable = false)
    @Builder.Default
    private Integer fraudScore = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
