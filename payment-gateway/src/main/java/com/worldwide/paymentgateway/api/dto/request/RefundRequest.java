package com.worldwide.paymentgateway.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundRequest {

    @Positive(message = "amount must be positive if provided")
    private Long amount;

    @NotBlank(message = "idempotency_key is required")
    @Size(max = 255)
    private String idempotencyKey;

    private String reason;
}
