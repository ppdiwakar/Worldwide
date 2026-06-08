package com.worldwide.paymentgateway.acquirer;

import com.worldwide.paymentgateway.domain.enums.AcquirerType;

public interface AcquirerConnector {

    AcquirerType getType();

    AcquirerResponse authorize(AcquirerRequest request);

    AcquirerResponse capture(String acquirerTxnId, long amount, String currency);

    AcquirerResponse refund(String acquirerTxnId, long amount, String currency);

    AcquirerResponse voidTransaction(String acquirerTxnId);
}
