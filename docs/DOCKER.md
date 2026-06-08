# Docker & PostgreSQL Setup

This guide explains how to run the Trading Intelligence Platform with a **PostgreSQL** database in Docker.

## Architecture

| Component | Role |
|-----------|------|
| **postgres** (Docker) | Persistent market data, trades, companies, per-symbol OHLCV tables |
| **app** (host or Docker) | JavaFX desktop UI + Spring Boot services |

The desktop UI needs a display. The recommended setup on Windows is:

1. Run **only PostgreSQL** in Docker.
2. Run the **JavaFX app on your host** with `SPRING_PROFILES_ACTIVE=docker`.

---

## Quick start (recommended — Postgres in Docker, app on host)

### 1. Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows)
- JDK 21
- Maven 3.9+

### 2. Start PostgreSQL

From the project root:

```powershell
docker compose up -d postgres
```

Verify health:

```powershell
docker compose ps
```

### 3. Configure API keys (optional)

```powershell
copy src\main\resources\application.properties.example src\main\resources\application-local.properties
```

Edit `application-local.properties` and add any API keys you need.

### 4. Run the app against PostgreSQL

```powershell
.\scripts\run-with-postgres.ps1
```

Or manually:

```powershell
docker compose up -d postgres
$env:SPRING_PROFILES_ACTIVE="docker"
$env:POSTGRES_HOST="localhost"
$env:POSTGRES_PORT="5432"
$env:POSTGRES_DB="trading_platform"
$env:POSTGRES_USER="trading"
$env:POSTGRES_PASSWORD="trading"
mvn javafx:run
```

Or build and run the JAR:

```powershell
mvn -DskipTests package
java -Dspring.profiles.active=docker -jar target\TradingPlatformApp-0.0.1-SNAPSHOT.jar
```

### 5. Stop PostgreSQL

```powershell
docker compose down
```

Data is kept in the `trading_pgdata` volume. To wipe it:

```powershell
docker compose down -v
```

---

## Full docker-compose (app + database)

```powershell
docker compose up --build
```

> **Note:** The `app` service builds the Spring Boot JAR but **does not provide a JavaFX display** inside the container. Use this for headless/CI validation or adapt with X11/VNC. For normal desktop use, prefer **Postgres in Docker + app on host** (above).

---

## Environment variables

Copy `.env.example` to `.env` and adjust if needed:

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `trading_platform` | Database name |
| `POSTGRES_USER` | `trading` | DB user |
| `POSTGRES_PASSWORD` | `trading` | DB password |
| `POSTGRES_PORT` | `5432` | Host port mapping |
| `POSTGRES_HOST` | `localhost` (host run) / `postgres` (compose network) | JDBC host |

With the `docker` profile, these settings apply:

- `app.market-data.dynamic-tables.enabled=true` — per-provider OHLCV tables (e.g. `ETHUSDT_BINANCE_1h`)
- `app.market-data.sync.enabled=true` — background API polling by timeframe

---

## SQLite fallback (no Docker)

Without `SPRING_PROFILES_ACTIVE=docker`, the app uses SQLite at:

`%USERPROFILE%\.trading-platform\trading.db`

Enable dynamic tables manually if desired:

```properties
app.market-data.dynamic-tables.enabled=true
```

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `Connection refused` to Postgres | Check `docker compose ps postgres` shows **`0.0.0.0:5432->5432/tcp`**. If you only see `5432/tcp`, the port is not on the host — run `docker compose up -d postgres --force-recreate`. Or use `.\scripts\run-with-postgres.ps1` which starts Postgres and waits for it |
| `relation "markets" already exists` / `idx_company_ticker already exists` | Usually leftover schema from `ddl-auto=create-drop` or a prior run. Reset the volume: `docker compose down -v`, then `docker compose up -d postgres` |
| `boolean = integer` on `price_alerts.active` | Postgres was reached with the SQLite Hibernate dialect. Ensure `SPRING_PROFILES_ACTIVE=docker` (sets `PostgreSQLDialect`). Reset the DB volume if tables were created with the wrong dialect |
| `null value in column "id" of relation "user_profiles"` | Same root cause — `user_profiles.id` lacks a Postgres sequence/identity. Run `docker compose down -v` and start Postgres again after fixing the docker profile |
| `constraint "…" does not exist, skipping` on startup | Harmless Hibernate `ddl-auto=update` noise while reconciling indexes — not a failure. A one-time `docker compose down -v` clears stale schema if it repeats every boot |
| `PostgreSQLDialect does not need to be specified explicitly` | Informational — dialect is auto-detected from the JDBC URL when the `docker` profile is active |
| App still uses SQLite after restarting Postgres | Restarting the Postgres container is not enough — set `$env:SPRING_PROFILES_ACTIVE="docker"` before `mvn javafx:run` |
| Empty charts on first load | Normal — tables bootstrap from API on first request; see `MARKET_DATA_TABLES.md` |
| API rate limits | Add keys in `application-local.properties`; see `API_PROVIDERS.md` |
| Binance `Connect timed out` | Network/firewall issue reaching `api.binance.com` — unrelated to the database. The app falls back to REST; check VPN or regional restrictions |
