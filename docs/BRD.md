# Business Requirements Document (BRD)
## Multi-Acquirer Payment Gateway

**Document Version:** 1.0  
**Date:** 2026-06-08  
**Author:** Architecture Team  
**Status:** Approved

---

## 1. Executive Summary

This document outlines the business requirements for a **Multi-Acquirer Payment Gateway** — a centralized payment orchestration platform that enables merchants to process payments across multiple acquiring banks and payment processors through a single, unified API. The system provides intelligent routing, failover, cost optimization, and PCI-DSS compliant transaction management.

---

## 2. Business Context

### 2.1 Problem Statement

Merchants currently face the following challenges:
- **Single point of failure**: Reliance on one acquirer leads to revenue loss during outages.
- **High transaction costs**: No ability to route to the lowest-cost acquirer dynamically.
- **Geographic limitations**: Single acquirers lack global coverage, reducing authorization rates in some regions.
- **Operational complexity**: Integration with multiple acquirers requires separate codebases and contracts.
- **Compliance overhead**: Each acquirer integration must independently meet PCI-DSS standards.

### 2.2 Business Opportunity

| Metric | Current State | Target State |
|--------|--------------|--------------|
| Payment success rate | 91% | 97%+ |
| Average transaction cost | $0.35 | $0.22 |
| Integration time per acquirer | 8 weeks | 1 week |
| Acquirers supported | 1 | 5+ |
| Uptime SLA | 99.5% | 99.99% |

---

## 3. Stakeholders

| Role | Name/Team | Interest |
|------|-----------|----------|
| Product Owner | Payments Team | Feature prioritization |
| Merchant Operations | Business Dev | Onboarding, SLA |
| Finance | CFO Office | Cost reduction |
| Security & Compliance | InfoSec | PCI-DSS, fraud |
| Engineering | Platform Team | Technical implementation |
| Acquirer Partners | Stripe, Adyen, Worldpay, Braintree | Integration standards |

---

## 4. Business Requirements

### 4.1 Core Payment Processing

**BR-001** — The system SHALL accept payment authorization requests via a RESTful API from authenticated merchants.

**BR-002** — The system SHALL support the following payment types:
- Credit card (Visa, Mastercard, Amex, Discover)
- Debit card
- Digital wallets (Apple Pay, Google Pay)
- ACH / bank transfers

**BR-003** — The system SHALL support the following transaction types:
- Authorization
- Capture (same-day and deferred)
- Authorization + Capture (sale)
- Void / Reversal
- Refund (full and partial)

**BR-004** — The system SHALL process payment authorization within **2 seconds** (p95 latency) under normal load.

**BR-005** — The system SHALL support amounts in multiple currencies (ISO 4217) and handle FX conversion metadata.

### 4.2 Multi-Acquirer Routing

**BR-010** — The system SHALL maintain connections to a minimum of **3 active acquirers** simultaneously.

**BR-011** — The system SHALL route transactions based on configurable rules including:
- Card network preference (e.g., Visa transactions → preferred acquirer)
- Geographic region of issuer
- Transaction amount thresholds
- Merchant category code (MCC)
- Merchant-specific acquirer preferences

**BR-012** — The system SHALL support **cost-based routing** to minimize interchange and acquirer fees.

**BR-013** — The system SHALL support **performance-based routing** using real-time success rate metrics per acquirer.

**BR-014** — The system SHALL implement **automatic failover**: if the primary acquirer returns a network or system error, the transaction SHALL be retried with a secondary acquirer within **500ms**.

**BR-015** — Failover SHALL NOT occur for hard declines (insufficient funds, card stolen) — only for acquirer system errors.

### 4.3 Security & Compliance

**BR-020** — The system SHALL be **PCI-DSS Level 1** compliant.

**BR-021** — The system SHALL **never store raw PAN (Primary Account Number)**. All card data SHALL be tokenized before persistence.

**BR-022** — All data in transit SHALL be encrypted using **TLS 1.2+**.

**BR-023** — All stored sensitive data SHALL be encrypted using **AES-256**.

**BR-024** — The system SHALL implement **3DS2 (3D Secure 2.0)** authentication support.

**BR-025** — API access SHALL require **OAuth 2.0 / API key** authentication with per-merchant scoped permissions.

**BR-026** — The system SHALL perform basic **fraud scoring** on each transaction and block transactions exceeding configurable thresholds.

### 4.4 Idempotency & Reliability

**BR-030** — The system SHALL support **idempotency keys** to prevent duplicate charges on network retries.

**BR-031** — The system SHALL guarantee **exactly-once** payment processing semantics for any given idempotency key within a 24-hour window.

**BR-032** — The system SHALL maintain a complete, immutable **audit log** of all transaction state changes.

**BR-033** — The system SHALL achieve **99.99% uptime** (SLA), permitting no more than 52 minutes downtime per year.

### 4.5 Merchant Management

**BR-040** — The system SHALL support multi-tenant merchant onboarding with isolated data and credentials.

**BR-041** — Each merchant SHALL have configurable routing profiles, acquirer assignments, and fee structures.

**BR-042** — The system SHALL expose a management API for merchants to query transaction status, request refunds, and view settlement reports.

### 4.6 Reporting & Analytics

**BR-050** — The system SHALL provide real-time transaction status via API.

**BR-051** — The system SHALL expose aggregated metrics: success rate, decline rate, volume, and latency per acquirer, per merchant, per time window.

**BR-052** — The system SHALL generate daily settlement reconciliation files in ISO 8583 / CSV format.

---

## 5. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| Throughput | 1,000 TPS sustained; 5,000 TPS burst |
| Latency | p95 < 2s end-to-end; p99 < 4s |
| Availability | 99.99% (multi-region active-active) |
| Data Retention | Transaction records: 7 years (regulatory) |
| Scalability | Horizontal scale with zero downtime |
| Observability | Distributed tracing, metrics, structured logs |
| Disaster Recovery | RTO < 15 min; RPO < 1 min |

---

## 6. Assumptions & Constraints

- Acquirer integrations will use HTTP/REST or SOAP APIs (no direct ISO 8583 host-to-host initially).
- Card data entry happens client-side (merchant's frontend) using tokenization SDKs; raw card data never reaches this service.
- Initial deployment on AWS (EKS); multi-cloud is out of scope for Phase 1.
- Regulatory compliance (PCI-DSS audit, penetration testing) is a separate track but the system must be designed for it.

---

## 7. Out of Scope (Phase 1)

- Payment links / hosted payment pages (future Phase 2)
- Subscription / recurring billing engine
- Direct ISO 8583 host-to-host acquirer connections
- Marketplace / split payments
- Crypto payments

---

## 8. Success Criteria

| KPI | Target | Measurement |
|-----|--------|-------------|
| Payment success rate | ≥ 97% | Monthly avg |
| P95 authorization latency | ≤ 2 seconds | Real-time monitoring |
| System availability | ≥ 99.99% | Uptime monitoring |
| Acquirer failover success | ≥ 99% of failover attempts | Log analysis |
| Cost per transaction | ≤ $0.22 | Finance reports |

---

## 9. Timeline (Phased Delivery)

| Phase | Scope | Duration |
|-------|-------|----------|
| Phase 1 – MVP | Core API, 2 acquirers (Stripe + Adyen), basic routing, idempotency | Sprint 1–4 (8 weeks) |
| Phase 2 – Routing | Smart routing, failover, cost-based rules, 2 more acquirers | Sprint 5–8 (8 weeks) |
| Phase 3 – Scale | 3DS2, fraud scoring, analytics dashboard, EKS autoscaling | Sprint 9–12 (8 weeks) |
| Phase 4 – Enterprise | Multi-region, settlement reports, merchant management UI | Sprint 13–16 (8 weeks) |

---

*End of BRD*
