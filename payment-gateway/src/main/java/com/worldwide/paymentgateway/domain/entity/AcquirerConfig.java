package com.worldwide.paymentgateway.domain.entity;

import com.worldwide.paymentgateway.domain.enums.AcquirerType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "acquirer_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcquirerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquirer_type", nullable = false, unique = true, length = 64)
    private AcquirerType acquirerType;

    @Column(name = "endpoint_url", nullable = false, length = 512)
    private String endpointUrl;

    @Column(name = "api_key_ref", nullable = false, length = 255)
    private String apiKeyRef;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(name = "success_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal successRate = BigDecimal.ONE;

    @Column(name = "avg_latency_ms", nullable = false)
    @Builder.Default
    private Integer avgLatencyMs = 500;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
