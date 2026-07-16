package com.mst.matt.marketservice.service;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Detects whether the configured DataSource is H2 or PostgreSQL
 * and produces the correct DDL fragment for dynamic OHLCV tables.
 *
 * <p>Ported from the desktop monolith's {@code DatabaseDialectHelper};
 * adapted for the market-service single-table architecture (H2 in dev,
 * Postgres in prod). The service currently uses H2 — Postgres migration
 * path is preserved here so the switch requires only a datasource URL
 * change in {@code application.yml}.</p>
 */
@Component
public class DatabaseDialectHelper {

    private final boolean postgres;

    public DatabaseDialectHelper(DataSource dataSource) {
        this.postgres = detectPostgres(dataSource);
    }

    public boolean isPostgres() {
        return postgres;
    }

    private static boolean detectPostgres(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String product = meta.getDatabaseProductName();
            return product != null && product.toLowerCase().contains("postgresql");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the CREATE TABLE DDL for a dynamic OHLCV table, using the
     * correct dialect for the current database.
     *
     * <p>H2 uses {@code AUTOINCREMENT} and stores timestamps as TEXT (ISO-8601).
     * Postgres uses {@code BIGSERIAL} and native {@code TIMESTAMP} with
     * {@code ON CONFLICT (open_time) DO UPDATE} upserts.</p>
     */
    public String createOhlcvTableDdl(String tableName) {
        if (postgres) {
            return """
                    CREATE TABLE IF NOT EXISTS "%s" (
                        id BIGSERIAL PRIMARY KEY,
                        open_time TIMESTAMP NOT NULL,
                        open_price NUMERIC(20,8) NOT NULL,
                        high_price NUMERIC(20,8) NOT NULL,
                        low_price NUMERIC(20,8) NOT NULL,
                        close_price NUMERIC(20,8) NOT NULL,
                        volume NUMERIC(30,8) NOT NULL,
                        asset_type VARCHAR(20) NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(open_time)
                    )
                    """.formatted(tableName);
        }
        // H2 dialect — used in dev/test
        return """
                CREATE TABLE IF NOT EXISTS "%s" (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    open_time TEXT NOT NULL,
                    open_price REAL NOT NULL,
                    high_price REAL NOT NULL,
                    low_price REAL NOT NULL,
                    close_price REAL NOT NULL,
                    volume REAL NOT NULL,
                    asset_type TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    UNIQUE(open_time)
                )
                """.formatted(tableName);
    }

    /**
     * Returns the CREATE TABLE DDL for an indicator time-series table.
     * Used by {@code DynamicOhlcvTableService} for indicator overlays.
     */
    public String createIndicatorSeriesTableDdl(String tableName) {
        if (postgres) {
            return """
                    CREATE TABLE IF NOT EXISTS "%s" (
                        open_time TIMESTAMP NOT NULL PRIMARY KEY,
                        value DOUBLE PRECISION NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """.formatted(tableName);
        }
        return """
                CREATE TABLE IF NOT EXISTS "%s" (
                    open_time TEXT NOT NULL PRIMARY KEY,
                    value REAL NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.formatted(tableName);
    }
}
