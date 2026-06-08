package com.worldwide.paymentgateway.service;

import com.worldwide.paymentgateway.config.PaymentProperties;
import com.worldwide.paymentgateway.domain.entity.Transaction;
import com.worldwide.paymentgateway.domain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final TransactionRepository transactionRepository;
    private final PaymentProperties properties;

    public Optional<Transaction> findExisting(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        Object cached = redisTemplate.opsForValue().get(redisKey);

        if (cached != null) {
            UUID txnId = UUID.fromString(cached.toString());
            return transactionRepository.findById(txnId);
        }

        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }

    public void store(String idempotencyKey, UUID transactionId) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(
                redisKey,
                transactionId.toString(),
                properties.getIdempotency().getTtlSeconds(),
                TimeUnit.SECONDS);
        log.debug("Stored idempotency key {} -> {}", idempotencyKey, transactionId);
    }
}
