-- alert-service baseline schema (matches JPA @Entity mappings)

CREATE TABLE price_alerts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT         NOT NULL,
    symbol            VARCHAR(40)    NOT NULL,
    asset_class       VARCHAR(20)    NOT NULL,
    broker_type       VARCHAR(20),
    provider_name     VARCHAR(40),
    condition         VARCHAR(20)    NOT NULL,
    target_value      NUMERIC(20, 8) NOT NULL,
    baseline_value    NUMERIC(20, 8),
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    message           VARCHAR(500),
    repeating         BOOLEAN        NOT NULL DEFAULT FALSE,
    cooldown_seconds  INTEGER        NOT NULL DEFAULT 900,
    last_triggered_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL
);
