CREATE TABLE IF NOT EXISTS acquirer_configs (
    id              BIGSERIAL PRIMARY KEY,
    acquirer_type   VARCHAR(64)    NOT NULL UNIQUE,
    endpoint_url    VARCHAR(512)   NOT NULL,
    api_key_ref     VARCHAR(255)   NOT NULL,
    enabled         BOOLEAN        NOT NULL DEFAULT TRUE,
    priority        INT            NOT NULL DEFAULT 100,
    success_rate    DECIMAL(5,4)   NOT NULL DEFAULT 1.0,
    avg_latency_ms  INT            NOT NULL DEFAULT 500,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

INSERT INTO acquirer_configs (acquirer_type, endpoint_url, api_key_ref, enabled, priority)
VALUES
    ('STRIPE',    'https://api.stripe.com',               'dev-stripe-key',    TRUE, 1),
    ('ADYEN',     'https://checkout-test.adyen.com',      'dev-adyen-key',     TRUE, 2),
    ('BRAINTREE', 'https://payments.sandbox.braintree-api.com', 'dev-bt-key',  TRUE, 3),
    ('WORLDPAY',  'https://api.sandbox.worldpay.com',     'dev-worldpay-key',  TRUE, 4);
