package com.worldwide.paymentgateway.service.notification;

import com.worldwide.paymentgateway.domain.entity.Transaction;
import com.worldwide.paymentgateway.domain.enums.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaNotificationService {

    private static final String TOPIC_AUTHORIZED = "payment.authorized";
    private static final String TOPIC_CAPTURED = "payment.captured";
    private static final String TOPIC_FAILED = "payment.failed";
    private static final String TOPIC_REFUNDED = "payment.refunded";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Transaction transaction) {
        String topic = resolveTopic(transaction.getStatus());
        if (topic == null) return;

        kafkaTemplate.send(topic, transaction.getId().toString(), buildPayload(transaction))
                .thenAccept(result -> log.debug("Published {} event for txn {}", topic, transaction.getId()))
                .exceptionally(ex -> {
                    log.error("Failed to publish {} event for txn {}: {}", topic, transaction.getId(), ex.getMessage());
                    return null;
                });
    }

    private String resolveTopic(TransactionStatus status) {
        return switch (status) {
            case AUTHORIZED -> TOPIC_AUTHORIZED;
            case CAPTURED -> TOPIC_CAPTURED;
            case FAILED, FRAUD_BLOCKED -> TOPIC_FAILED;
            case REFUNDED, PARTIALLY_REFUNDED -> TOPIC_REFUNDED;
            default -> null;
        };
    }

    private Object buildPayload(Transaction txn) {
        return new TransactionEvent(
                txn.getId().toString(),
                txn.getMerchantId(),
                txn.getStatus().name(),
                txn.getAmount(),
                txn.getCurrency(),
                txn.getAcquirerType() != null ? txn.getAcquirerType().name() : null,
                txn.getCreatedAt().toString()
        );
    }

    public record TransactionEvent(
            String transactionId,
            String merchantId,
            String status,
            Long amount,
            String currency,
            String acquirer,
            String timestamp) {
    }
}
