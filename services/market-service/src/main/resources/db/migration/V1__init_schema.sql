-- market-service baseline schema (matches JPA @Entity mappings)

CREATE TABLE markets (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(32)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    asset_type    VARCHAR(20)  NOT NULL,
    country       VARCHAR(64),
    currency      VARCHAR(16),
    exchange_name VARCHAR(128),
    description   VARCHAR(512),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_markets_code UNIQUE (code)
);

CREATE INDEX idx_market_code ON markets (code);

CREATE TABLE companies (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(256)   NOT NULL,
    ticker        VARCHAR(20),
    sector        VARCHAR(128),
    industry      VARCHAR(128),
    country       VARCHAR(64),
    website       VARCHAR(256),
    description   VARCHAR(2000),
    market_cap    NUMERIC(24, 2),
    data_provider VARCHAR(64),
    created_at    TIMESTAMP      NOT NULL,
    updated_at    TIMESTAMP      NOT NULL
);

CREATE INDEX idx_company_ticker ON companies (ticker);

CREATE TABLE shares (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(32)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    market_id       BIGINT       NOT NULL,
    company_id      BIGINT,
    asset_type      VARCHAR(20)  NOT NULL,
    base_currency   VARCHAR(32),
    quote_currency  VARCHAR(32),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_shares_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_shares_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE UNIQUE INDEX idx_share_symbol_market ON shares (symbol, market_id);

CREATE TABLE symbol_entries (
    id         BIGSERIAL PRIMARY KEY,
    symbol     VARCHAR(40) NOT NULL,
    name       VARCHAR(200),
    asset_type VARCHAR(10) NOT NULL,
    exchange   VARCHAR(60),
    source     VARCHAR(40),
    CONSTRAINT uk_symbol_entries_type_symbol UNIQUE (asset_type, symbol)
);

CREATE INDEX idx_symbol_entries_type ON symbol_entries (asset_type);
CREATE INDEX idx_symbol_entries_search ON symbol_entries (symbol, name);

CREATE TABLE ohlcv_bars (
    id         BIGSERIAL PRIMARY KEY,
    symbol     VARCHAR(20)    NOT NULL,
    timeframe  VARCHAR(10)    NOT NULL,
    open_time  TIMESTAMP      NOT NULL,
    open       NUMERIC(20, 8) NOT NULL,
    high       NUMERIC(20, 8) NOT NULL,
    low        NUMERIC(20, 8) NOT NULL,
    close      NUMERIC(20, 8) NOT NULL,
    volume     NUMERIC(30, 8) NOT NULL,
    asset_type VARCHAR(255)   NOT NULL,
    provider   VARCHAR(32)
);

CREATE INDEX idx_ohlcv_symbol_tf_time ON ohlcv_bars (symbol, timeframe, open_time);

CREATE TABLE market_data_table_registry (
    id          BIGSERIAL PRIMARY KEY,
    table_name  VARCHAR(128) NOT NULL,
    symbol      VARCHAR(32)  NOT NULL,
    provider    VARCHAR(32)  NOT NULL,
    timeframe   VARCHAR(10)  NOT NULL,
    asset_type  VARCHAR(20)  NOT NULL,
    last_sync_at TIMESTAMP,
    next_sync_at TIMESTAMP,
    bar_count   INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX idx_mdt_symbol_tf_provider ON market_data_table_registry (symbol, timeframe, provider);
CREATE UNIQUE INDEX idx_mdt_table_name ON market_data_table_registry (table_name);

CREATE TABLE indicator_configs (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT       NOT NULL,
    active_profile           VARCHAR(255) NOT NULL,
    macd_enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    macd_weight              INTEGER      NOT NULL DEFAULT 0,
    rsi_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    rsi_weight               INTEGER      NOT NULL DEFAULT 0,
    rsi_period               INTEGER      NOT NULL DEFAULT 0,
    rsi_overbought           INTEGER      NOT NULL DEFAULT 0,
    rsi_oversold             INTEGER      NOT NULL DEFAULT 0,
    ichimoku_enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    ichimoku_weight          INTEGER      NOT NULL DEFAULT 0,
    ichimoku_tenkan_period   INTEGER      NOT NULL DEFAULT 0,
    ichimoku_kijun_period    INTEGER      NOT NULL DEFAULT 0,
    ichimoku_senkou_period   INTEGER      NOT NULL DEFAULT 0,
    ema_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    ema_weight               INTEGER      NOT NULL DEFAULT 0,
    ema_fast_period          INTEGER      NOT NULL DEFAULT 0,
    ema_slow_period          INTEGER      NOT NULL DEFAULT 0,
    gold_cross_short_period  INTEGER      NOT NULL DEFAULT 0,
    gold_cross_long_period   INTEGER      NOT NULL DEFAULT 0,
    bollinger_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    bollinger_weight         INTEGER      NOT NULL DEFAULT 0,
    bollinger_period         INTEGER      NOT NULL DEFAULT 0,
    bollinger_deviation      DOUBLE PRECISION NOT NULL DEFAULT 0,
    fibonacci_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    fibonacci_weight         INTEGER      NOT NULL DEFAULT 0,
    fibonacci_lookback       INTEGER      NOT NULL DEFAULT 0,
    stochastic_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    stochastic_weight        INTEGER      NOT NULL DEFAULT 0,
    stochastic_k_period      INTEGER      NOT NULL DEFAULT 0,
    stochastic_d_period      INTEGER      NOT NULL DEFAULT 0,
    atr_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    atr_period               INTEGER      NOT NULL DEFAULT 0,
    vwap_enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    vwap_weight              INTEGER      NOT NULL DEFAULT 0,
    cci_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    cci_weight               INTEGER      NOT NULL DEFAULT 0,
    cci_period               INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uk_indicator_configs_user_id UNIQUE (user_id)
);

CREATE TABLE drawing_layouts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    symbol          VARCHAR(30)  NOT NULL,
    timeframe       VARCHAR(10)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    saved_at_epoch  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_layout_user_symbol_tf_name UNIQUE (user_id, symbol, timeframe, name)
);

CREATE INDEX idx_layout_user_sym_tf ON drawing_layouts (user_id, symbol, timeframe);

CREATE TABLE chart_drawings (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL,
    symbol            VARCHAR(30) NOT NULL,
    timeframe         VARCHAR(10) NOT NULL,
    tool_type         VARCHAR(60) NOT NULL,
    points_json       TEXT        NOT NULL,
    properties_json   TEXT,
    locked            BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at_epoch  BIGINT      NOT NULL DEFAULT 0,
    layout_name       VARCHAR(100)
);

CREATE INDEX idx_cd_user_sym_tf ON chart_drawings (user_id, symbol, timeframe);

CREATE TABLE watchlist_items (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    symbol      VARCHAR(40) NOT NULL,
    asset_class VARCHAR(20),
    added_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_watchlist_user_symbol UNIQUE (user_id, symbol)
);

CREATE INDEX idx_watchlist_user_id ON watchlist_items (user_id);
