package com.worldwide.paymentgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "acquirers")
@Data
public class AcquirerProperties {

    private Map<String, AcquirerDetails> configs = new HashMap<>();

    @Data
    public static class AcquirerDetails {
        private String endpointUrl;
        private String apiKeyRef;
        private boolean enabled = true;
        private int priority = 100;
    }
}
