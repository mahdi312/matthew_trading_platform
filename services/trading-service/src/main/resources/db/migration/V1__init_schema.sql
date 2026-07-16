-- trading-service baseline schema (matches JPA @Entity mappings)

CREATE TABLE trades (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT         NOT NULL,
    symbol           VARCHAR(255)   NOT NULL,
    asset_name       VARCHAR(255)   NOT NULL,
    asset_type       VARCHAR(255)   NOT NULL,
    direction        VARCHAR(255)   NOT NULL,
    status           VARCHAR(255)   NOT NULL,
    source           VARCHAR(255)   NOT NULL DEFAULT 'MANUAL',
    broker_order_id  VARCHAR(64),
    entry_price      NUMERIC(20, 8) NOT NULL,
    exit_price       NUMERIC(20, 8),
    quantity         NUMERIC(20, 8) NOT NULL,
    stop_loss        NUMERIC(20, 8),
    take_profit      NUMERIC(20, 8),
    fee              NUMERIC(20, 8),
    entry_time       TIMESTAMP      NOT NULL,
    exit_time        TIMESTAMP,
    notes            VARCHAR(1000),
    exchange         VARCHAR(255),
    strategy         VARCHAR(255),
    screenshot_path  VARCHAR(512),
    pnl_amount       NUMERIC(20, 8),
    pnl_percent      NUMERIC(10, 4),
    total_invested   NUMERIC(20, 8),
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    idempotency_key  VARCHAR(128),
    CONSTRAINT uk_trades_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE outbox_events (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(64) NOT NULL,
    kafka_key    VARCHAR(64) NOT NULL,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMP   NOT NULL,
    published_at TIMESTAMP,
    attempts     INTEGER     NOT NULL DEFAULT 0
);
