package com.worldwide.paymentgateway.domain.enums;

public enum TransactionStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    VOIDED,
    FAILED,
    FRAUD_BLOCKED
}
