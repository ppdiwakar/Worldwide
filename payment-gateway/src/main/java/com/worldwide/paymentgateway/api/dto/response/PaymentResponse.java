package com.worldwide.paymentgateway.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.worldwide.paymentgateway.domain.entity.Transaction;
import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import com.worldwide.paymentgateway.domain.enums.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private UUID id;
    private TransactionStatus status;
    private Long amount;
    private String currency;
    private AcquirerType acquirer;
    private String acquirerTransactionId;
    private String failureCode;
    private String failureMessage;
    private OffsetDateTime createdAt;

    public static PaymentResponse from(Transaction txn) {
        return PaymentResponse.builder()
                .id(txn.getId())
                .status(txn.getStatus())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .acquirer(txn.getAcquirerType())
                .acquirerTransactionId(txn.getAcquirerTxnId())
                .failureCode(txn.getFailureCode())
                .failureMessage(txn.getFailureMessage())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
