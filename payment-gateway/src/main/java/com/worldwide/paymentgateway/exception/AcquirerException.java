package com.worldwide.paymentgateway.exception;

public class AcquirerException extends RuntimeException {

    private final boolean hardDecline;
    private final String declineCode;

    public AcquirerException(String message, boolean hardDecline, String declineCode) {
        super(message);
        this.hardDecline = hardDecline;
        this.declineCode = declineCode;
    }

    public boolean isHardDecline() { return hardDecline; }
    public String getDeclineCode() { return declineCode; }
}
