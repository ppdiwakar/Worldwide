package com.worldwide.paymentgateway.acquirer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AcquirerResponse {
    private boolean success;
    private String acquirerTransactionId;
    private String declineCode;
    private String declineMessage;
    private boolean hardDecline;
}
