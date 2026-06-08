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

@Component
@RequiredArgsConstructor
@Slf4j
public class BraintreeConnector implements AcquirerConnector {

    private static final String CIRCUIT_BREAKER_NAME = "braintree";

    private final WebClient acquirerWebClient;
    private final AcquirerConfigRepository acquirerConfigRepository;

    @Override
    public AcquirerType getType() {
        return AcquirerType.BRAINTREE;
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "authorizeFallback")
    public AcquirerResponse authorize(AcquirerRequest request) {
        log.info("Authorizing via Braintree txn={}", request.getTransactionId());

        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.BRAINTREE)
                .orElseThrow(() -> new AcquirerException("Braintree not configured", false, "CONFIG_ERROR"));

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/merchants/worldwide/transactions")
                    .header("Authorization", "Basic " + resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(buildBraintreePayload(request))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return parseBraintreeResponse(response);

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                throw new AcquirerException("Braintree system error", false, "BRAINTREE_SERVER_ERROR");
            }
            return AcquirerResponse.builder()
                    .success(false).hardDecline(true)
                    .declineCode("CARD_DECLINED").declineMessage(ex.getMessage()).build();
        }
    }

    @Override
    public AcquirerResponse capture(String acquirerTxnId, long amount, String currency) {
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.BRAINTREE).orElseThrow();
        try {
            Map<?, ?> response = acquirerWebClient.put()
                    .uri(config.getEndpointUrl() + "/merchants/worldwide/transactions/" + acquirerTxnId + "/submit_for_settlement")
                    .header("Authorization", "Basic " + resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(Map.of("amount", String.format("%.2f", amount / 100.0)))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return AcquirerResponse.builder().success(true).acquirerTransactionId(acquirerTxnId).build();
        } catch (WebClientResponseException ex) {
            throw new AcquirerException("Braintree capture failed", false, "CAPTURE_ERROR");
        }
    }

    @Override
    public AcquirerResponse refund(String acquirerTxnId, long amount, String currency) {
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.BRAINTREE).orElseThrow();
        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/merchants/worldwide/transactions/" + acquirerTxnId + "/refund")
                    .header("Authorization", "Basic " + resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(Map.of("amount", String.format("%.2f", amount / 100.0)))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return AcquirerResponse.builder().success(true).acquirerTransactionId(acquirerTxnId).build();
        } catch (WebClientResponseException ex) {
            throw new AcquirerException("Braintree refund failed", false, "REFUND_ERROR");
        }
    }

    @Override
    public AcquirerResponse voidTransaction(String acquirerTxnId) {
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.BRAINTREE).orElseThrow();
        try {
            acquirerWebClient.put()
                    .uri(config.getEndpointUrl() + "/merchants/worldwide/transactions/" + acquirerTxnId + "/void")
                    .header("Authorization", "Basic " + resolveApiKey(config.getApiKeyRef()))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return AcquirerResponse.builder().success(true).acquirerTransactionId(acquirerTxnId).build();
        } catch (WebClientResponseException ex) {
            throw new AcquirerException("Braintree void failed", false, "VOID_ERROR");
        }
    }

    private AcquirerResponse authorizeFallback(AcquirerRequest request, Throwable ex) {
        throw new AcquirerException("Braintree circuit breaker open", false, "CIRCUIT_OPEN");
    }

    private Map<String, Object> buildBraintreePayload(AcquirerRequest req) {
        return Map.of(
                "amount", String.format("%.2f", req.getAmount() / 100.0),
                "paymentMethodToken", req.getCardToken(),
                "options", Map.of("submitForSettlement", false)
        );
    }

    private AcquirerResponse parseBraintreeResponse(Map<?, ?> response) {
        if (response == null) throw new AcquirerException("Empty Braintree response", false, "EMPTY_RESPONSE");
        Map<?, ?> txn = (Map<?, ?>) response.get("transaction");
        if (txn == null) throw new AcquirerException("Missing transaction in Braintree response", false, "PARSE_ERROR");

        String status = getField(txn, "status");
        boolean success = "authorized".equals(status) || "submitted_for_settlement".equals(status);
        boolean hardDecline = "processor_declined".equals(status) || "gateway_rejected".equals(status);

        return AcquirerResponse.builder()
                .success(success)
                .hardDecline(hardDecline)
                .acquirerTransactionId(getField(txn, "id"))
                .declineCode(hardDecline ? getField(txn, "processorResponseCode") : null)
                .declineMessage(hardDecline ? getField(txn, "processorResponseText") : null)
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
