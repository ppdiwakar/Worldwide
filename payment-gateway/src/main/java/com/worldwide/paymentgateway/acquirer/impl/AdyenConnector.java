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
public class AdyenConnector implements AcquirerConnector {

    private static final String CIRCUIT_BREAKER_NAME = "adyen";

    private final WebClient acquirerWebClient;
    private final AcquirerConfigRepository acquirerConfigRepository;

    @Override
    public AcquirerType getType() {
        return AcquirerType.ADYEN;
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "authorizeFallback")
    public AcquirerResponse authorize(AcquirerRequest request) {
        log.info("Authorizing via Adyen txn={}", request.getTransactionId());

        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.ADYEN)
                .orElseThrow(() -> new AcquirerException("Adyen not configured", false, "CONFIG_ERROR"));

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v68/payments")
                    .header("X-API-Key", resolveApiKey(config.getApiKeyRef()))
                    .header("Idempotency-Key", request.getIdempotencyKey())
                    .bodyValue(buildAdyenPayload(request))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return parseAdyenResponse(response);

        } catch (WebClientResponseException ex) {
            return handleAdyenError(ex);
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AcquirerResponse capture(String acquirerTxnId, long amount, String currency) {
        log.info("Capturing Adyen pspReference={}", acquirerTxnId);
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.ADYEN).orElseThrow();

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v68/payments/" + acquirerTxnId + "/captures")
                    .header("X-API-Key", resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(Map.of(
                            "amount", Map.of("value", amount, "currency", currency),
                            "merchantAccount", "WorldwideECOM"
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return AcquirerResponse.builder()
                    .success("received".equals(getField(response, "status")))
                    .acquirerTransactionId(getField(response, "pspReference"))
                    .build();
        } catch (WebClientResponseException ex) {
            return handleAdyenError(ex);
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AcquirerResponse refund(String acquirerTxnId, long amount, String currency) {
        log.info("Refunding Adyen pspReference={}", acquirerTxnId);
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.ADYEN).orElseThrow();

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v68/payments/" + acquirerTxnId + "/refunds")
                    .header("X-API-Key", resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(Map.of(
                            "amount", Map.of("value", amount, "currency", currency),
                            "merchantAccount", "WorldwideECOM"
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return AcquirerResponse.builder()
                    .success("received".equals(getField(response, "status")))
                    .acquirerTransactionId(getField(response, "pspReference"))
                    .build();
        } catch (WebClientResponseException ex) {
            return handleAdyenError(ex);
        }
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public AcquirerResponse voidTransaction(String acquirerTxnId) {
        log.info("Reversing Adyen pspReference={}", acquirerTxnId);
        var config = acquirerConfigRepository.findByAcquirerType(AcquirerType.ADYEN).orElseThrow();

        try {
            Map<?, ?> response = acquirerWebClient.post()
                    .uri(config.getEndpointUrl() + "/v68/payments/" + acquirerTxnId + "/reversals")
                    .header("X-API-Key", resolveApiKey(config.getApiKeyRef()))
                    .bodyValue(Map.of("merchantAccount", "WorldwideECOM"))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return AcquirerResponse.builder()
                    .success("received".equals(getField(response, "status")))
                    .acquirerTransactionId(getField(response, "pspReference"))
                    .build();
        } catch (WebClientResponseException ex) {
            return handleAdyenError(ex);
        }
    }

    private AcquirerResponse authorizeFallback(AcquirerRequest request, Throwable ex) {
        throw new AcquirerException("Adyen circuit breaker open", false, "CIRCUIT_OPEN");
    }

    private Map<String, Object> buildAdyenPayload(AcquirerRequest req) {
        return Map.of(
                "amount", Map.of("value", req.getAmount(), "currency", req.getCurrency()),
                "paymentMethod", Map.of("type", "scheme", "storedPaymentMethodId", req.getCardToken()),
                "merchantAccount", "WorldwideECOM",
                "reference", req.getIdempotencyKey(),
                "shopperReference", req.getMerchantId()
        );
    }

    private AcquirerResponse parseAdyenResponse(Map<?, ?> response) {
        if (response == null) throw new AcquirerException("Empty Adyen response", false, "EMPTY_RESPONSE");

        String resultCode = getField(response, "resultCode");
        boolean success = "Authorised".equals(resultCode);
        boolean hardDecline = "Refused".equals(resultCode);

        return AcquirerResponse.builder()
                .success(success)
                .hardDecline(hardDecline)
                .acquirerTransactionId(getField(response, "pspReference"))
                .declineCode(hardDecline ? getField(response, "refusalReasonCode") : null)
                .declineMessage(hardDecline ? getField(response, "refusalReason") : null)
                .build();
    }

    private AcquirerResponse handleAdyenError(WebClientResponseException ex) {
        if (ex.getStatusCode().is5xxServerError()) {
            throw new AcquirerException("Adyen system error", false, "ADYEN_SERVER_ERROR");
        }
        return AcquirerResponse.builder()
                .success(false)
                .hardDecline(true)
                .declineCode("CARD_DECLINED")
                .declineMessage(ex.getMessage())
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
