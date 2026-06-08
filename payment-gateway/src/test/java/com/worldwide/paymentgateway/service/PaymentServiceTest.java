package com.worldwide.paymentgateway.service;

import com.worldwide.paymentgateway.acquirer.AcquirerConnector;
import com.worldwide.paymentgateway.acquirer.AcquirerRequest;
import com.worldwide.paymentgateway.acquirer.AcquirerResponse;
import com.worldwide.paymentgateway.acquirer.factory.AcquirerConnectorFactory;
import com.worldwide.paymentgateway.api.dto.request.PaymentRequest;
import com.worldwide.paymentgateway.api.dto.response.PaymentResponse;
import com.worldwide.paymentgateway.domain.entity.Transaction;
import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import com.worldwide.paymentgateway.domain.enums.CardNetwork;
import com.worldwide.paymentgateway.domain.enums.CaptureMode;
import com.worldwide.paymentgateway.domain.enums.TransactionStatus;
import com.worldwide.paymentgateway.domain.repository.TransactionRepository;
import com.worldwide.paymentgateway.exception.PaymentException;
import com.worldwide.paymentgateway.service.notification.KafkaNotificationService;
import com.worldwide.paymentgateway.service.routing.AcquirerRoutingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private FraudDetectionService fraudDetectionService;
    @Mock private AcquirerRoutingService routingService;
    @Mock private AcquirerConnectorFactory connectorFactory;
    @Mock private KafkaNotificationService notificationService;
    @Mock private AcquirerConnector stripeConnector;

    private MeterRegistry meterRegistry;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        paymentService = new PaymentService(
                transactionRepository, idempotencyService, fraudDetectionService,
                routingService, connectorFactory, notificationService, meterRegistry);
    }

    @Test
    void authorize_successfulAuthorization_returnsCapturedTransaction() {
        PaymentRequest request = buildPaymentRequest();

        given(idempotencyService.findExisting(any())).willReturn(Optional.empty());
        given(fraudDetectionService.score(any())).willReturn(5);
        given(fraudDetectionService.isBlocked(5)).willReturn(false);
        given(routingService.getOrderedAcquirers(any())).willReturn(List.of(AcquirerType.STRIPE));
        given(connectorFactory.get(AcquirerType.STRIPE)).willReturn(stripeConnector);
        given(stripeConnector.authorize(any(AcquirerRequest.class))).willReturn(
                AcquirerResponse.builder().success(true).acquirerTransactionId("pi_stripe_123").build());

        Transaction savedTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .status(TransactionStatus.CAPTURED)
                .amount(1000L)
                .currency("USD")
                .acquirerType(AcquirerType.STRIPE)
                .acquirerTxnId("pi_stripe_123")
                .idempotencyKey(request.getIdempotencyKey())
                .merchantId(request.getMerchantId())
                .captureMode(CaptureMode.AUTOMATIC)
                .fraudScore(5)
                .build();
        given(transactionRepository.save(any())).willReturn(savedTxn);

        PaymentResponse response = paymentService.authorize(request);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.CAPTURED);
        assertThat(response.getAcquirer()).isEqualTo(AcquirerType.STRIPE);
        assertThat(response.getAcquirerTransactionId()).isEqualTo("pi_stripe_123");
    }

    @Test
    void authorize_idempotentReplay_returnsExistingTransaction() {
        PaymentRequest request = buildPaymentRequest();

        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .status(TransactionStatus.CAPTURED)
                .amount(1000L)
                .currency("USD")
                .acquirerType(AcquirerType.STRIPE)
                .acquirerTxnId("pi_existing_456")
                .idempotencyKey(request.getIdempotencyKey())
                .merchantId(request.getMerchantId())
                .captureMode(CaptureMode.AUTOMATIC)
                .fraudScore(0)
                .build();

        given(idempotencyService.findExisting(request.getIdempotencyKey())).willReturn(Optional.of(existing));

        PaymentResponse response = paymentService.authorize(request);

        assertThat(response.getAcquirerTransactionId()).isEqualTo("pi_existing_456");
        then(stripeConnector).shouldHaveNoInteractions();
        then(routingService).shouldHaveNoInteractions();
    }

    @Test
    void authorize_fraudBlocked_throwsPaymentException() {
        PaymentRequest request = buildPaymentRequest();

        given(idempotencyService.findExisting(any())).willReturn(Optional.empty());
        given(fraudDetectionService.score(any())).willReturn(80);
        given(fraudDetectionService.isBlocked(80)).willReturn(true);

        Transaction blockedTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .status(TransactionStatus.FRAUD_BLOCKED)
                .amount(1000L)
                .currency("USD")
                .idempotencyKey(request.getIdempotencyKey())
                .merchantId(request.getMerchantId())
                .captureMode(CaptureMode.AUTOMATIC)
                .fraudScore(80)
                .build();
        given(transactionRepository.save(any())).willReturn(blockedTxn);

        assertThatThrownBy(() -> paymentService.authorize(request))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo("FRAUD_BLOCKED");
    }

    @Test
    void authorize_primaryAcquirerFails_failsOverToSecondary() {
        PaymentRequest request = buildPaymentRequest();
        AcquirerConnector adyenConnector = mock(AcquirerConnector.class);

        given(idempotencyService.findExisting(any())).willReturn(Optional.empty());
        given(fraudDetectionService.score(any())).willReturn(0);
        given(fraudDetectionService.isBlocked(0)).willReturn(false);
        given(routingService.getOrderedAcquirers(any())).willReturn(List.of(AcquirerType.STRIPE, AcquirerType.ADYEN));

        given(connectorFactory.get(AcquirerType.STRIPE)).willReturn(stripeConnector);
        given(stripeConnector.authorize(any()))
                .willReturn(AcquirerResponse.builder().success(false).hardDecline(false).declineCode("SYSTEM_ERROR").build());

        given(connectorFactory.get(AcquirerType.ADYEN)).willReturn(adyenConnector);
        given(adyenConnector.authorize(any()))
                .willReturn(AcquirerResponse.builder().success(true).acquirerTransactionId("psp_adyen_789").build());

        Transaction savedTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .status(TransactionStatus.CAPTURED)
                .amount(1000L)
                .currency("USD")
                .acquirerType(AcquirerType.ADYEN)
                .acquirerTxnId("psp_adyen_789")
                .idempotencyKey(request.getIdempotencyKey())
                .merchantId(request.getMerchantId())
                .captureMode(CaptureMode.AUTOMATIC)
                .fraudScore(0)
                .build();
        given(transactionRepository.save(any())).willReturn(savedTxn);

        PaymentResponse response = paymentService.authorize(request);

        assertThat(response.getAcquirer()).isEqualTo(AcquirerType.ADYEN);
        assertThat(response.getAcquirerTransactionId()).isEqualTo("psp_adyen_789");
    }

    private PaymentRequest buildPaymentRequest() {
        PaymentRequest.PaymentMethodDto method = new PaymentRequest.PaymentMethodDto();
        method.setType("card");
        method.setToken("tok_visa_4242");
        method.setCardNetwork(CardNetwork.VISA);
        method.setExpiryMonth(12);
        method.setExpiryYear(2028);

        PaymentRequest req = new PaymentRequest();
        req.setIdempotencyKey("test-key-" + UUID.randomUUID());
        req.setMerchantId("merch_test");
        req.setAmount(1000L);
        req.setCurrency("USD");
        req.setCaptureMode(CaptureMode.AUTOMATIC);
        req.setPaymentMethod(method);
        return req;
    }
}
