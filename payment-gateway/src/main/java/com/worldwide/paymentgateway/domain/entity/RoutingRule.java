package com.worldwide.paymentgateway.domain.entity;

import com.worldwide.paymentgateway.domain.enums.CardNetwork;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "routing_rules", indexes = {
        @Index(name = "idx_routing_rules_acquirer_id", columnList = "acquirer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acquirer_id", nullable = false)
    private AcquirerConfig acquirerConfig;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_network", length = 32)
    private CardNetwork cardNetwork;

    @Column(length = 3)
    private String currency;

    @Column(name = "min_amount")
    private Long minAmount;

    @Column(name = "max_amount")
    private Long maxAmount;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public boolean matches(CardNetwork requestCardNetwork, String requestCurrency, Long requestAmount) {
        if (cardNetwork != null && cardNetwork != requestCardNetwork) return false;
        if (currency != null && !currency.equals(requestCurrency)) return false;
        if (minAmount != null && requestAmount < minAmount) return false;
        if (maxAmount != null && requestAmount > maxAmount) return false;
        return true;
    }
}
