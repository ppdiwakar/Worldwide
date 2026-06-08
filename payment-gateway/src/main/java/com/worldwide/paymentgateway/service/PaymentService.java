package com.worldwide.paymentgateway.service;

import com.worldwide.paymentgateway.acquirer.AcquirerConnector;
import com.worldwide.paymentgateway.acquirer.AcquirerRequest;
import com.worldwide.paymentgateway.acquirer.AcquirerResponse;
import com.worldwide.paymentgateway.acquirer.factory.AcquirerConnectorFactory;
import com.worldwide.paymentgateway.api.dto.request.CaptureRequest;
import com.worldwide.paymentgateway.api.dto.request.PaymentRequest;
import com.worldwide.paymentgateway.api.dto.request.RefundRequest;
import com.worldwide.paymentgateway.api.dto.response.PaymentResponse;
import com.worldwide.paymentgateway.domain.entity.Transaction;
import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import com.worldwide.paymentgateway.domain.enums.CaptureMode;
import com.worldwide.paymentgateway.domain.enums.TransactionStatus;
import com.worldwide.paymentgateway.exception.AcquirerException;
import com.worldwide.paymentgateway.exception.PaymentException;
import com.worldwide.paymentgateway.domain.repository.TransactionRepository;
import com.worldwide.paymentgateway.service.notification.KafkaNotificationService;
import com.worldwide.paymentgateway.service.routing.AcquirerRoutingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;
    private final FraudDetectionService fraudDetectionService;
    private final AcquirerRoutingService routingService;
    private final AcquirerConnectorFactory connectorFactory;
    private final KafkaNotificationService notificationService;
    private final MeterRegistry meterRegistry;

    @Transactional
    public PaymentResponse authorize(PaymentRequest request) {
        // Idempotency check — return existing result without re-processing
        var existing = idempotencyService.findExisting(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay for key={}", request.getIdempotencyKey());
            return PaymentResponse.from(existing.get());
        }

        // Fraud scoring
        int fraudScore = fraudDetectionService.score(request);
        if (fraudDetectionService.isBlocked(fraudScore)) {
            Transaction blocked = saveTerminal(request, TransactionStatus.FRAUD_BLOCKED,
                    null, null, "FRAUD_BLOCKED", "Transaction blocked by fraud rules", fraudScore);
            notificationService.publish(blocked);
            throw new PaymentException("FRAUD_BLOCKED", "Transaction declined due to fraud risk", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Build acquirer request (canonical model)
        AcquirerRequest acquirerReq = AcquirerRequest.builder()
                .transactionId(UUID.randomUUID())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .cardToken(request.getPaymentMethod().getToken())
                .cardNetwork(request.getPaymentMethod().getCardNetwork())
                .expiryMonth(request.getPaymentMethod().getExpiryMonth())
                .expiryYear(request.getPaymentMethod().getExpiryYear())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        // Routing: get ordered acquirer list
        List<AcquirerType> acquirerOrder = routingService.getOrderedAcquirers(request);
        if (acquirerOrder.isEmpty()) {
            throw new PaymentException("NO_ACQUIRER_AVAILABLE", "No acquirers available for this payment", HttpStatus.SERVICE_UNAVAILABLE);
        }

        // Attempt acquirers in order (failover loop)
        AcquirerResponse acquirerResp = null;
        AcquirerType usedAcquirer = null;
        AcquirerException lastException = null;

        for (AcquirerType acquirerType : acquirerOrder) {
            Timer.Sample timerSample = Timer.start(meterRegistry);
            try {
                AcquirerConnector connector = connectorFactory.get(acquirerType);
                acquirerResp = connector.authorize(acquirerReq);
                usedAcquirer = acquirerType;
                timerSample.stop(meterRegistry.timer("payment.acquirer.latency",
                        "acquirer", acquirerType.name(), "result", "success"));

                if (acquirerResp.isSuccess()) {
                    break; // Successful authorization
                }

                // Hard decline — do not failover
                if (acquirerResp.isHardDecline()) {
                    log.info("Hard decline from acquirer={} code={}", acquirerType, acquirerResp.getDeclineCode());
                    break;
                }

                // Soft decline — try next acquirer
                log.warn("Soft decline from acquirer={}, trying next", acquirerType);
                acquirerResp = null;

            } catch (AcquirerException ex) {
                timerSample.stop(meterRegistry.timer("payment.acquirer.latency",
                        "acquirer", acquirerType.name(), "result", "error"));
                log.warn("Acquirer {} system error, failing over: {}", acquirerType, ex.getMessage());
                lastException = ex;
                if (ex.isHardDecline()) break;
            }
        }

        // Persist outcome
        TransactionStatus finalStatus;
        String failureCode = null;
        String failureMessage = null;

        if (acquirerResp != null && acquirerResp.isSuccess()) {
            finalStatus = request.getCaptureMode() == CaptureMode.AUTOMATIC
                    ? TransactionStatus.CAPTURED
                    : TransactionStatus.AUTHORIZED;
        } else {
            finalStatus = TransactionStatus.FAILED;
            if (acquirerResp != null) {
                failureCode = acquirerResp.getDeclineCode();
                failureMessage = acquirerResp.getDeclineMessage();
            } else if (lastException != null) {
                failureCode = "ACQUIRER_SYSTEM_ERROR";
                failureMessage = lastException.getMessage();
            }
        }

        Transaction txn = buildTransaction(request, acquirerResp, usedAcquirer,
                finalStatus, failureCode, failureMessage, fraudScore);
        txn = transactionRepository.save(txn);
        idempotencyService.store(request.getIdempotencyKey(), txn.getId());
        notificationService.publish(txn);

        meterRegistry.counter("payment.authorization.count",
                "status", finalStatus.name(),
                "acquirer", usedAcquirer != null ? usedAcquirer.name() : "none").increment();

        return PaymentResponse.from(txn);
    }

    @Transactional
    public PaymentResponse capture(UUID transactionId, CaptureRequest captureRequest) {
        Transaction txn = getTransaction(transactionId);
        validateStatus(txn, TransactionStatus.AUTHORIZED, "capture");

        long captureAmount = captureRequest.getAmount() != null ? captureRequest.getAmount() : txn.getAmount();
        AcquirerConnector connector = connectorFactory.get(txn.getAcquirerType());
        AcquirerResponse resp = connector.capture(txn.getAcquirerTxnId(), captureAmount, txn.getCurrency());

        if (!resp.isSuccess()) {
            throw new PaymentException("CAPTURE_FAILED", resp.getDeclineMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        txn.setStatus(TransactionStatus.CAPTURED);
        txn.setAmount(captureAmount);
        txn = transactionRepository.save(txn);
        notificationService.publish(txn);
        return PaymentResponse.from(txn);
    }

    @Transactional
    public PaymentResponse refund(UUID transactionId, RefundRequest refundRequest) {
        Transaction txn = getTransaction(transactionId);

        if (txn.getStatus() != TransactionStatus.CAPTURED && txn.getStatus() != TransactionStatus.PARTIALLY_REFUNDED) {
            throw new PaymentException("INVALID_STATE",
                    "Refund requires a CAPTURED transaction, current status: " + txn.getStatus(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        long refundAmount = refundRequest.getAmount() != null ? refundRequest.getAmount() : txn.getAmount();
        AcquirerConnector connector = connectorFactory.get(txn.getAcquirerType());
        AcquirerResponse resp = connector.refund(txn.getAcquirerTxnId(), refundAmount, txn.getCurrency());

        if (!resp.isSuccess()) {
            throw new PaymentException("REFUND_FAILED", resp.getDeclineMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        txn.setStatus(refundAmount >= txn.getAmount() ? TransactionStatus.REFUNDED : TransactionStatus.PARTIALLY_REFUNDED);
        txn = transactionRepository.save(txn);
        notificationService.publish(txn);
        return PaymentResponse.from(txn);
    }

    @Transactional
    public PaymentResponse voidTransaction(UUID transactionId) {
        Transaction txn = getTransaction(transactionId);
        validateStatus(txn, TransactionStatus.AUTHORIZED, "void");

        AcquirerConnector connector = connectorFactory.get(txn.getAcquirerType());
        AcquirerResponse resp = connector.voidTransaction(txn.getAcquirerTxnId());

        if (!resp.isSuccess()) {
            throw new PaymentException("VOID_FAILED", resp.getDeclineMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
        }

        txn.setStatus(TransactionStatus.VOIDED);
        txn = transactionRepository.save(txn);
        return PaymentResponse.from(txn);
    }

    public PaymentResponse getPayment(UUID transactionId) {
        return PaymentResponse.from(getTransaction(transactionId));
    }

    private Transaction getTransaction(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new PaymentException("TRANSACTION_NOT_FOUND",
                        "Transaction not found: " + id, HttpStatus.NOT_FOUND));
    }

    private void validateStatus(Transaction txn, TransactionStatus expected, String operation) {
        if (txn.getStatus() != expected) {
            throw new PaymentException("INVALID_STATE",
                    operation + " requires status " + expected + ", current: " + txn.getStatus(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private Transaction buildTransaction(PaymentRequest req, AcquirerResponse resp,
                                         AcquirerType acquirerType, TransactionStatus status,
                                         String failureCode, String failureMessage, int fraudScore) {
        return Transaction.builder()
                .merchantId(req.getMerchantId())
                .idempotencyKey(req.getIdempotencyKey())
                .status(status)
                .amount(req.getAmount())
                .currency(req.getCurrency())
                .cardToken(req.getPaymentMethod().getToken())
                .cardNetwork(req.getPaymentMethod().getCardNetwork())
                .captureMode(req.getCaptureMode())
                .acquirerType(acquirerType)
                .acquirerTxnId(resp != null ? resp.getAcquirerTransactionId() : null)
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .fraudScore(fraudScore)
                .metadata(req.getMetadata())
                .build();
    }

    private Transaction saveTerminal(PaymentRequest req, TransactionStatus status,
                                     AcquirerType acquirerType, AcquirerResponse resp,
                                     String failureCode, String failureMessage, int fraudScore) {
        Transaction txn = buildTransaction(req, resp, acquirerType, status, failureCode, failureMessage, fraudScore);
        txn = transactionRepository.save(txn);
        idempotencyService.store(req.getIdempotencyKey(), txn.getId());
        return txn;
    }
}
