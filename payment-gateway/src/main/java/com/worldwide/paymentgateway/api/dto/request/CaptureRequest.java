package com.worldwide.paymentgateway.api.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CaptureRequest {

    @Positive(message = "amount must be positive if provided")
    private Long amount;
}
