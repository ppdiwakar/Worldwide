package com.worldwide.paymentgateway.acquirer.impl;

import com.worldwide.paymentgateway.acquirer.AcquirerConnector;
import com.worldwide.paymentgateway.acquirer.AcquirerRequest;
import com.worldwide.paymentgateway.acquirer.AcquirerResponse;
import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import com.worldwide.paymentgateway.domain.repository.AcquirerConfigRepository;
import com.worldwide.paymentgateway.exception.AcquirerException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorldpayConnector implements AcquirerConnector {

    private static final String CIRCUIT_BREAKER_NAME = "worldpay";

    private final WebClient acquirerWebClient;
    private final AcquirerConfigRepository acquirerConfigRepository;

    @Override
    public AcquirerType getType() {
        return AcquirerType.WORLDPAY;
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "authorizeFallback")
    public AcquirerResponse authorize(AcquirerRequest request) {
        log.info("Authorizing via Worldpay txn={}", request.getTransactionId());

        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.WORLDPAY)
                .orElseThrow(() -> new AcquirerException("Worldpay not configured", false, "CONFIG_ERROR"));

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v1/orders")
                    .header("Authorization", "Basic " + resolveApiKey(config.getApiKeyRef()))
                    .header("X-WP-IdempotencyKey", request.getIdempotencyKey())
                    .bodyValue(buildWorldpayPayload(request))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return parseWorldpayResponse(response);

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                throw new AcquirerException("Worldpay system error", false, "WORLDPAY_SERVER_ERROR");
            }
            return AcquirerResponse.builder()
                    .success(false).hardDecline(true)
                    .declineCode("CARD_DECLINED").declineMessage(ex.getMessage()).build();
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AcquirerResponse capture(String acquirerTxnId, long amount, String currency) {
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.WORLDPAY).orElseThrow();
        try {
            acquirerWebClient.put()
                    .uri(config.getEndpointUrl() + "/v1/orders/" + acquirerTxnId + "/capture")
                    .header("Authorization", "Basic " + resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(Map.of("captureAmount", amount))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return AcquirerResponse.builder().success(true).acquirerTransactionId(acquirerTxnId).build();
        } catch (WebClientResponseException ex) {
            throw new AcquirerException("Worldpay capture failed", false, "CAPTURE_ERROR");
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AcquirerResponse refund(String acquirerTxnId, long amount, String currency) {
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.WORLDPAY).orElseThrow();
        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v1/orders/" + acquirerTxnId + "/refunds")
                    .header("Authorization", "Basic " + resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(Map.of("refundAmount", amount))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            String refundId = getField(response, "refundId");
            return AcquirerResponse.builder().success(true).acquirerTransactionId(refundId).build();
        } catch (WebClientResponseException ex) {
            throw new AcquirerException("Worldpay refund failed", false, "REFUND_ERROR");
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AcquirerResponse voidTransaction(String acquirerTxnId) {
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.WORLDPAY).orElseThrow();
        try {
            acquirerWebClient.delete()
                    .uri(config.getEndpointUrl() + "/v1/orders/" + acquirerTxnId)
                    .header("Authorization", "Basic " + resolveApiKey(config.getApiKeyRef()))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return AcquirerResponse.builder().success(true).acquirerTransactionId(acquirerTxnId).build();
        } catch (WebClientResponseException ex) {
            throw new AcquirerException("Worldpay void failed", false, "VOID_ERROR");
        }
    }

    private AcquirerResponse authorizeFallback(AcquirerRequest request, Throwable ex) {
        throw new AcquirerException("Worldpay circuit breaker open", false, "CIRCUIT_OPEN");
    }

    private Map<String, Object> buildWorldpayPayload(AcquirerRequest req) {
        return Map.of(
                "orderType", "ECOM",
                "amount", req.getAmount(),
                "currencyCode", req.getCurrency(),
                "token", req.getCardToken(),
                "orderDescription", "Payment " + req.getTransactionId()
        );
    }

    private AcquirerResponse parseWorldpayResponse(Map<?, ?> response) {
        if (response == null) throw new AcquirerException("Empty Worldpay response", false, "EMPTY_RESPONSE");
        String status = getField(response, "paymentStatus");
        boolean success = "SUCCESS".equals(status) || "AUTHORIZED".equals(status);
        boolean hardDecline = "FAILED".equals(status);
        return AcquirerResponse.builder()
                .success(success)
                .hardDecline(hardDecline)
                .acquirerTransactionId(getField(response, "orderCode"))
                .declineCode(hardDecline ? getField(response, "reasonCode") : null)
                .build();
    }

    private String resolveApiKey(String apiKeyRef) {
        return apiKeyRef;
    }

    private String getField(Map<?, ?> map, String key) {
        Object value = map != null ? map.get(key) : null;
        return value != null ? value.toString() : null;
    }
}
