package com.worldwide.paymentgateway.service;

import com.worldwide.paymentgateway.api.dto.request.PaymentRequest;
import com.worldwide.paymentgateway.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private static final String BLOCKLIST_KEY = "fraud:blocklist:tokens";
    private static final String VELOCITY_PREFIX = "fraud:velocity:";
    private static final long HIGH_AMOUNT_THRESHOLD = 500000L; // $5,000 in cents

    private final RedisTemplate<String, Object> redisTemplate;
    private final PaymentProperties properties;

    public int score(PaymentRequest request) {
        int score = 0;

        score += scoreHighAmount(request.getAmount());
        score += scoreBlocklistedToken(request.getPaymentMethod().getToken());
        score += scoreVelocity(request.getMerchantId());

        score = Math.min(score, 100);
        log.debug("Fraud score for merchant {} idempotency {}: {}", request.getMerchantId(), request.getIdempotencyKey(), score);
        return score;
    }

    public boolean isBlocked(int fraudScore) {
        return fraudScore >= properties.getFraud().getScoreThreshold();
    }

    private int scoreHighAmount(Long amount) {
        return amount > HIGH_AMOUNT_THRESHOLD ? 20 : 0;
    }

    private int scoreBlocklistedToken(String token) {
        Boolean isBlocklisted = redisTemplate.opsForSet().isMember(BLOCKLIST_KEY, token);
        return Boolean.TRUE.equals(isBlocklisted) ? 60 : 0;
    }

    private int scoreVelocity(String merchantId) {
        PaymentProperties.Fraud fraudConfig = properties.getFraud();
        String velocityKey = VELOCITY_PREFIX + merchantId;
        Long count = redisTemplate.opsForValue().increment(velocityKey);

        if (count != null && count == 1) {
            redisTemplate.expire(velocityKey, fraudConfig.getVelocityWindowSeconds(), TimeUnit.SECONDS);
        }

        return (count != null && count > fraudConfig.getVelocityMaxAttempts()) ? 30 : 0;
    }
}
