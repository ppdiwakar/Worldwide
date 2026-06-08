package com.worldwide.paymentgateway.domain.repository;

import com.worldwide.paymentgateway.domain.entity.AcquirerConfig;
import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcquirerConfigRepository extends JpaRepository<AcquirerConfig, Long> {

    List<AcquirerConfig> findByEnabledTrueOrderByPriorityAsc();

    Optional<AcquirerConfig> findByAcquirerType(AcquirerType acquirerType);
}
