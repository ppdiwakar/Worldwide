package com.worldwide.paymentgateway.service.routing;

import com.worldwide.paymentgateway.api.dto.request.PaymentRequest;
import com.worldwide.paymentgateway.domain.entity.AcquirerConfig;
import com.worldwide.paymentgateway.domain.entity.RoutingRule;
import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import com.worldwide.paymentgateway.domain.enums.CardNetwork;
import com.worldwide.paymentgateway.domain.repository.AcquirerConfigRepository;
import com.worldwide.paymentgateway.domain.repository.RoutingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcquirerRoutingService {

    private static final double SUCCESS_RATE_WEIGHT = 0.5;
    private static final double LATENCY_WEIGHT = 0.3;
    private static final double PRIORITY_WEIGHT = 0.2;

    private final RoutingRuleRepository routingRuleRepository;
    private final AcquirerConfigRepository acquirerConfigRepository;

    public List<AcquirerType> getOrderedAcquirers(PaymentRequest request) {
        CardNetwork cardNetwork = request.getPaymentMethod().getCardNetwork();
        String currency = request.getCurrency();
        Long amount = request.getAmount();

        List<RoutingRule> activeRules = routingRuleRepository.findAllActiveRulesWithAcquirer();

        // Collect matching acquirers (preserving rule priority order, deduplicating)
        Set<AcquirerConfig> matchedAcquirers = activeRules.stream()
                .filter(rule -> rule.matches(cardNetwork, currency, amount))
                .map(RoutingRule::getAcquirerConfig)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Fall back to all enabled acquirers if no specific rule matched
        if (matchedAcquirers.isEmpty()) {
            log.debug("No routing rules matched; using all enabled acquirers as fallback");
            matchedAcquirers.addAll(acquirerConfigRepository.findByEnabledTrueOrderByPriorityAsc());
        }

        List<AcquirerType> ordered = matchedAcquirers.stream()
                .sorted(Comparator.comparingDouble(a -> -computeScore(a)))
                .map(AcquirerConfig::getAcquirerType)
                .toList();

        log.info("Routing order for merchant={} cardNetwork={} currency={} amount={}: {}",
                request.getMerchantId(), cardNetwork, currency, amount, ordered);
        return ordered;
    }

    private double computeScore(AcquirerConfig config) {
        double successScore = config.getSuccessRate().doubleValue() * SUCCESS_RATE_WEIGHT;
        double latencyScore = (1.0 / Math.max(config.getAvgLatencyMs(), 1)) * LATENCY_WEIGHT * 100_000;
        double priorityScore = (1.0 / Math.max(config.getPriority(), 1)) * PRIORITY_WEIGHT * 100;
        return successScore + latencyScore + priorityScore;
    }
}
