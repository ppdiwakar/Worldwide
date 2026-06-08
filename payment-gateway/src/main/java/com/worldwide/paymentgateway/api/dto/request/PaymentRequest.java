package com.worldwide.paymentgateway.api.dto.request;

import com.worldwide.paymentgateway.domain.enums.CaptureMode;
import com.worldwide.paymentgateway.domain.enums.CardNetwork;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Map;

@Data
public class PaymentRequest {

    @NotBlank(message = "idempotency_key is required")
    @Size(max = 255)
    private String idempotencyKey;

    @NotBlank(message = "merchant_id is required")
    @Size(max = 64)
    private String merchantId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private Long amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be ISO 4217 (3 chars)")
    private String currency;

    @Valid
    @NotNull(message = "payment_method is required")
    private PaymentMethodDto paymentMethod;

    private CaptureMode captureMode = CaptureMode.AUTOMATIC;

    private Map<String, String> metadata;

    @Data
    public static class PaymentMethodDto {

        @NotBlank(message = "type is required")
        private String type;

        @NotBlank(message = "token is required")
        @Size(max = 255)
        private String token;

        private CardNetwork cardNetwork;

        @Min(1) @Max(12)
        private Integer expiryMonth;

        @Min(2024)
        private Integer expiryYear;
    }
}
