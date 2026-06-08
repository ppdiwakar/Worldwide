package com.worldwide.paymentgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment")
@Data
public class PaymentProperties {

    private Fraud fraud = new Fraud();
    private Idempotency idempotency = new Idempotency();

    @Data
    public static class Fraud {
        private int scoreThreshold = 70;
        private int velocityWindowSeconds = 60;
        private int velocityMaxAttempts = 3;
    }

    @Data
    public static class Idempotency {
        private long ttlSeconds = 86400;
    }
}
