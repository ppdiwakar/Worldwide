package com.worldwide.paymentgateway.acquirer.impl;

import com.worldwide.paymentgateway.acquirer.AcquirerConnector;
import com.worldwide.paymentgateway.acquirer.AcquirerRequest;
import com.worldwide.paymentgateway.acquirer.AcquirerResponse;
import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import com.worldwide.paymentgateway.domain.repository.AcquirerConfigRepository;
import com.worldwide.paymentgateway.exception.AcquirerException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StripeConnector implements AcquirerConnector {

    private static final String CIRCUIT_BREAKER_NAME = "stripe";

    private final WebClient acquirerWebClient;
    private final AcquirerConfigRepository acquirerConfigRepository;

    @Override
    public AcquirerType getType() {
        return AcquirerType.STRIPE;
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "authorizeFallback")
    public AcquirerResponse authorize(AcquirerRequest request) {
        log.info("Authorizing via Stripe txn={} amount={} {}", request.getTransactionId(), request.getAmount(), request.getCurrency());

        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.STRIPE)
                .orElseThrow(() -> new AcquirerException("Stripe not configured", false, "CONFIG_ERROR"));

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v1/payment_intents")
                    .header("Authorization", "Bearer " + resolveApiKey(config.getApiKeyRef()))
                    .header("Idempotency-Key", request.getIdempotencyKey())
                    .bodyValue(buildStripePayload(request))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return parseStripeResponse(response);

        } catch (WebClientResponseException ex) {
            return handleStripeError(ex);
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AcquirerResponse capture(String acquirerTxnId, long amount, String currency) {
        log.info("Capturing Stripe payment_intent={} amount={}", acquirerTxnId, amount);
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.STRIPE).orElseThrow();

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v1/payment_intents/" + acquirerTxnId + "/capture")
                    .header("Authorization", "Bearer " + resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(Map.of("amount_to_capture", amount))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return AcquirerResponse.builder()
                    .success("succeeded".equals(getField(response, "status")))
                    .acquirerTransactionId(getField(response, "id"))
                    .build();
        } catch (WebClientResponseException ex) {
            return handleStripeError(ex);
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AcquirerResponse refund(String acquirerTxnId, long amount, String currency) {
        log.info("Refunding Stripe payment_intent={} amount={}", acquirerTxnId, amount);
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.STRIPE).orElseThrow();

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v1/refunds")
                    .header("Authorization", "Bearer " + resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(Map.of("payment_intent", acquirerTxnId, "amount", amount))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return AcquirerResponse.builder()
                    .success("succeeded".equals(getField(response, "status")))
                    .acquirerTransactionId(getField(response, "id"))
                    .build();
        } catch (WebClientResponseException ex) {
            return handleStripeError(ex);
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AcquirerResponse voidTransaction(String acquirerTxnId) {
        log.info("Voiding Stripe payment_intent={}", acquirerTxnId);
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.STRIPE).orElseThrow();

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v1/payment_intents/" + acquirerTxnId + "/cancel")
                    .header("Authorization", "Bearer " + resolveApiKey(config.getApiKeyRef()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return AcquirerResponse.builder()
                    .success("canceled".equals(getField(response, "status")))
                    .acquirerTransactionId(getField(response, "id"))
                    .build();
        } catch (WebClientResponseException ex) {
            return handleStripeError(ex);
        }
    }

    private AcquirerResponse authorizeFallback(AcquirerRequest request, Throwable ex) {
        log.error("Stripe circuit breaker open for txn={}: {}", request.getTransactionId(), ex.getMessage());
        throw new AcquirerException("Stripe circuit breaker open", false, "CIRCUIT_OPEN");
    }

    private Map<String, Object> buildStripePayload(AcquirerRequest req) {
        return Map.of(
                "amount", req.getAmount(),
                "currency", req.getCurrency().toLowerCase(),
                "payment_method", req.getCardToken(),
                "confirm", true,
                "metadata", Map.of("merchant_id", req.getMerchantId())
        );
    }

    private AcquirerResponse parseStripeResponse(Map<?, ?> response) {
        if (response == null) {
            throw new AcquirerException("Empty response from Stripe", false, "EMPTY_RESPONSE");
        }

        String status = getField(response, "status");
        boolean success = "succeeded".equals(status) || "requires_capture".equals(status);

        return AcquirerResponse.builder()
                .success(success)
                .acquirerTransactionId(getField(response, "id"))
                .build();
    }

    private AcquirerResponse handleStripeError(WebClientResponseException ex) {
        boolean hardDecline = ex.getStatusCode() == HttpStatus.PAYMENT_REQUIRED
                || ex.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY;
        log.error("Stripe HTTP error {}: {}", ex.getStatusCode(), ex.getMessage());

        if (ex.getStatusCode().is5xxServerError()) {
            throw new AcquirerException("Stripe system error: " + ex.getMessage(), false, "STRIPE_SERVER_ERROR");
        }

        String declineCode = "CARD_DECLINED";
        return AcquirerResponse.builder()
                .success(false)
                .hardDecline(hardDecline)
                .declineCode(declineCode)
                .declineMessage(ex.getMessage())
                .build();
    }

    private String resolveApiKey(String apiKeyRef) {
        // In production, resolve from AWS Secrets Manager via SecretsManagerClient
        // For dev, the ref IS the key
        return apiKeyRef;
    }

    @SuppressWarnings("unchecked")
    private String getField(Map<?, ?> map, String key) {
        Object value = map != null ? map.get(key) : null;
        return value != null ? value.toString() : null;
    }
}
