-- Optional bootstrap for PostgreSQL (JPA ddl-auto=update also creates core tables).
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

COMMENT ON DATABASE trading_platform IS 'Trading Intelligence Platform — market data, trades, reference data';
