package com.worldwide.paymentgateway.api.controller;

import com.worldwide.paymentgateway.api.dto.request.CaptureRequest;
import com.worldwide.paymentgateway.api.dto.request.PaymentRequest;
import com.worldwide.paymentgateway.api.dto.request.RefundRequest;
import com.worldwide.paymentgateway.api.dto.response.PaymentResponse;
import com.worldwide.paymentgateway.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Multi-Acquirer Payment Gateway API")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Authorize (and optionally capture) a payment")
    public ResponseEntity<PaymentResponse> authorize(
            @Valid @RequestBody PaymentRequest request) {
        log.info("POST /v1/payments merchant={} amount={} {}",
                request.getMerchantId(), request.getAmount(), request.getCurrency());
        PaymentResponse response = paymentService.authorize(request);
        HttpStatus status = response.getStatus().name().startsWith("FAIL") || response.getStatus().name().equals("FRAUD_BLOCKED")
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "Transaction UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    @PostMapping("/{id}/capture")
    @Operation(summary = "Capture an authorized payment")
    public ResponseEntity<PaymentResponse> capture(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CaptureRequest captureRequest) {
        log.info("POST /v1/payments/{}/capture", id);
        return ResponseEntity.ok(paymentService.capture(id, captureRequest != null ? captureRequest : new CaptureRequest()));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a captured payment (full or partial)")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable UUID id,
            @Valid @RequestBody RefundRequest refundRequest) {
        log.info("POST /v1/payments/{}/refund amount={}", id, refundRequest.getAmount());
        return ResponseEntity.ok(paymentService.refund(id, refundRequest));
    }

    @PostMapping("/{id}/void")
    @Operation(summary = "Void an authorized payment before capture")
    public ResponseEntity<PaymentResponse> voidPayment(@PathVariable UUID id) {
        log.info("POST /v1/payments/{}/void", id);
        return ResponseEntity.ok(paymentService.voidTransaction(id));
    }
}
