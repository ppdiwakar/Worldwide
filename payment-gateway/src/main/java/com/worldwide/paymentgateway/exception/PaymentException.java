package com.worldwide.paymentgateway.exception;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public PaymentException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
