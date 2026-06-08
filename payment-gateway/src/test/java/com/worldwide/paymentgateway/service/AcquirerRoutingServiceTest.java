package com.worldwide.paymentgateway.service;

import com.worldwide.paymentgateway.api.dto.request.PaymentRequest;
import com.worldwide.paymentgateway.domain.entity.AcquirerConfig;
import com.worldwide.paymentgateway.domain.entity.RoutingRule;
import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import com.worldwide.paymentgateway.domain.enums.CardNetwork;
import com.worldwide.paymentgateway.domain.enums.CaptureMode;
import com.worldwide.paymentgateway.domain.repository.AcquirerConfigRepository;
import com.worldwide.paymentgateway.domain.repository.RoutingRuleRepository;
import com.worldwide.paymentgateway.service.routing.AcquirerRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AcquirerRoutingServiceTest {

    @Mock private RoutingRuleRepository routingRuleRepository;
    @Mock private AcquirerConfigRepository acquirerConfigRepository;

    private AcquirerRoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new AcquirerRoutingService(routingRuleRepository, acquirerConfigRepository);
    }

    @Test
    void getOrderedAcquirers_visaCard_returnsStripeFirst() {
        AcquirerConfig stripe = buildConfig(AcquirerType.STRIPE, 1, new BigDecimal("0.97"), 300);
        AcquirerConfig adyen = buildConfig(AcquirerType.ADYEN, 2, new BigDecimal("0.95"), 400);

        RoutingRule stripeRule = RoutingRule.builder()
                .id(1L)
                .acquirerConfig(stripe)
                .priority(10)
                .cardNetwork(CardNetwork.VISA)
                .enabled(true)
                .build();

        RoutingRule adyenRule = RoutingRule.builder()
                .id(2L)
                .acquirerConfig(adyen)
                .priority(20)
                .cardNetwork(CardNetwork.MASTERCARD)
                .enabled(true)
                .build();

        given(routingRuleRepository.findAllActiveRulesWithAcquirer())
                .willReturn(List.of(stripeRule, adyenRule));

        List<AcquirerType> result = routingService.getOrderedAcquirers(buildRequest(CardNetwork.VISA, "USD", 5000L));

        assertThat(result).containsExactly(AcquirerType.STRIPE);
    }

    @Test
    void getOrderedAcquirers_noRuleMatch_fallsBackToAllAcquirers() {
        AcquirerConfig stripe = buildConfig(AcquirerType.STRIPE, 1, new BigDecimal("0.97"), 300);
        AcquirerConfig worldpay = buildConfig(AcquirerType.WORLDPAY, 4, new BigDecimal("0.90"), 600);

        given(routingRuleRepository.findAllActiveRulesWithAcquirer()).willReturn(List.of());
        given(acquirerConfigRepository.findByEnabledTrueOrderByPriorityAsc())
                .willReturn(List.of(stripe, worldpay));

        List<AcquirerType> result = routingService.getOrderedAcquirers(buildRequest(CardNetwork.UNKNOWN, "EUR", 1000L));

        assertThat(result).containsExactly(AcquirerType.STRIPE, AcquirerType.WORLDPAY);
    }

    private AcquirerConfig buildConfig(AcquirerType type, int priority, BigDecimal successRate, int latency) {
        return AcquirerConfig.builder()
                .id((long) priority)
                .acquirerType(type)
                .endpointUrl("https://example.com")
                .apiKeyRef("key-ref")
                .enabled(true)
                .priority(priority)
                .successRate(successRate)
                .avgLatencyMs(latency)
                .build();
    }

    private PaymentRequest buildRequest(CardNetwork network, String currency, Long amount) {
        PaymentRequest.PaymentMethodDto method = new PaymentRequest.PaymentMethodDto();
        method.setType("card");
        method.setToken("tok_test");
        method.setCardNetwork(network);

        PaymentRequest req = new PaymentRequest();
        req.setIdempotencyKey(UUID.randomUUID().toString());
        req.setMerchantId("merch_test");
        req.setAmount(amount);
        req.setCurrency(currency);
        req.setCaptureMode(CaptureMode.AUTOMATIC);
        req.setPaymentMethod(method);
        return req;
    }
}
