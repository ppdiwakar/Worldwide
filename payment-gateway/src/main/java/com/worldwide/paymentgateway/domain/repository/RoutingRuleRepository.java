package com.worldwide.paymentgateway.domain.repository;

import com.worldwide.paymentgateway.domain.entity.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {

    @Query("SELECT r FROM RoutingRule r JOIN FETCH r.acquirerConfig ac WHERE r.enabled = true AND ac.enabled = true ORDER BY r.priority ASC")
    List<RoutingRule> findAllActiveRulesWithAcquirer();
}
