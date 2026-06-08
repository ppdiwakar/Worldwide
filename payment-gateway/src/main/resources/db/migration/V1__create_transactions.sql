CREATE TABLE IF NOT EXISTS transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         VARCHAR(64)  NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    amount              BIGINT       NOT NULL,
    currency            CHAR(3)      NOT NULL,
    card_token          VARCHAR(255),
    card_network        VARCHAR(32),
    capture_mode        VARCHAR(32)  NOT NULL DEFAULT 'AUTOMATIC',
    acquirer_type       VARCHAR(64),
    acquirer_txn_id     VARCHAR(255),
    failure_code        VARCHAR(64),
    failure_message     TEXT,
    fraud_score         SMALLINT     NOT NULL DEFAULT 0,
    metadata            JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_transactions_merchant_id  ON transactions (merchant_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status       ON transactions (status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at   ON transactions (created_at DESC);
