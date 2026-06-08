package com.worldwide.paymentgateway.exception;

import java.util.UUID;

public class IdempotencyConflictException extends RuntimeException {

    private final UUID existingTransactionId;

    public IdempotencyConflictException(UUID existingTransactionId) {
        super("Idempotency key already used for transaction: " + existingTransactionId);
        this.existingTransactionId = existingTransactionId;
    }

    public UUID getExistingTransactionId() { return existingTransactionId; }
}
