CREATE TABLE IF NOT EXISTS routing_rules (
    id              BIGSERIAL PRIMARY KEY,
    acquirer_id     BIGINT       NOT NULL REFERENCES acquirer_configs(id),
    priority        INT          NOT NULL DEFAULT 100,
    card_network    VARCHAR(32),
    currency        CHAR(3),
    min_amount      BIGINT,
    max_amount      BIGINT,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_routing_rules_acquirer_id ON routing_rules (acquirer_id);

-- Stripe handles VISA preferentially
INSERT INTO routing_rules (acquirer_id, priority, card_network, currency)
SELECT id, 10, 'VISA', NULL FROM acquirer_configs WHERE acquirer_type = 'STRIPE';

-- Adyen handles MASTERCARD preferentially
INSERT INTO routing_rules (acquirer_id, priority, card_network, currency)
SELECT id, 10, 'MASTERCARD', NULL FROM acquirer_configs WHERE acquirer_type = 'ADYEN';

-- Braintree handles AMEX
INSERT INTO routing_rules (acquirer_id, priority, card_network, currency)
SELECT id, 10, 'AMEX', NULL FROM acquirer_configs WHERE acquirer_type = 'BRAINTREE';

-- Worldpay as catch-all fallback (no specific network, low priority)
INSERT INTO routing_rules (acquirer_id, priority, card_network, currency)
SELECT id, 100, NULL, NULL FROM acquirer_configs WHERE acquirer_type = 'WORLDPAY';
