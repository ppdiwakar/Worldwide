package com.worldwide.paymentgateway.acquirer;

import com.worldwide.paymentgateway.domain.enums.CardNetwork;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AcquirerRequest {
    private UUID transactionId;
    private String merchantId;
    private Long amount;
    private String currency;
    private String cardToken;
    private CardNetwork cardNetwork;
    private Integer expiryMonth;
    private Integer expiryYear;
    private String idempotencyKey;
}
