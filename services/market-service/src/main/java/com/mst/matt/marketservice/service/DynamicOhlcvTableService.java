package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.AssetType;
import com.mst.matt.marketservice.model.OhlcvBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic DDL + DML service for per-symbol OHLCV tables.
 *
 * <p>Ported from the desktop monolith's {@code DynamicOhlcvTableService};
 * adapted for the market-service context:</p>
 * <ul>
 *   <li>Replaced {@code Trade.AssetType} with the local {@link AssetType} enum.</li>
 *   <li>Uses {@link DatabaseDialectHelper} for H2/Postgres DDL switching.</li>
 *   <li>In-memory {@code ENSURED} set guards against duplicate DDL on concurrent
 *       first-access.</li>
 * </ul>
 *
 * <p>These dynamic tables complement (but do not replace) the single
 * {@code ohlcv_bars} JPA table. They are used when aggregation pipelines
 * need a provider-qualified or timeframe-qualified table name that would
 * otherwise clash in the shared JPA table.</p>
 */
@Slf4j
@Service
public class DynamicOhlcvTableService {

    /** In-memory guard: table names for which DDL has already been executed. */
    private static final Set<String> ENSURED = ConcurrentHashMap.newKeySet();

    private final JdbcTemplate jdbc;
    private final DatabaseDialectHelper dialect;

    public DynamicOhlcvTableService(JdbcTemplate jdbc, DatabaseDialectHelper dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
    }

    // ── DDL ───────────────────────────────────────────────────────────────────

    /**
     * Ensures the dynamic table for {@code tableName} exists.
     * Idempotent — only executes DDL once per JVM lifecycle.
     */
    public void ensureTable(String tableName) {
        if (!ENSURED.add(tableName)) {
            return;
        }
        jdbc.execute(dialect.createOhlcvTableDdl(tableName));
        log.debug("Ensured OHLCV table {}", tableName);
    }

    // ── Write (upsert / replace) ──────────────────────────────────────────────

    /**
     * Upserts the provided bars — existing rows (keyed by {@code open_time}) are
     * updated, rows not in the batch are preserved.
     */
    @Transactional
    public void replaceBars(String tableName, String symbol, String timeframe,
                            AssetType assetType, List<OhlcvBar> bars) {
        upsertBars(tableName, assetType, bars);
    }

    /**
     * Fully replaces the table contents (DELETE all + INSERT fresh batch).
     * Use when the provider always returns a complete window (live-fetch path).
     */
    @Transactional
    public void replaceAllBars(String tableName, String symbol, String timeframe,
                               AssetType assetType, List<OhlcvBar> bars) {
        ensureTable(tableName);
        jdbc.update("DELETE FROM \"%s\"".formatted(tableName));
        if (!bars.isEmpty()) {
            upsertBars(tableName, assetType, bars);
        }
    }

    /**
     * Core UPSERT — inserts or updates bars by {@code open_time} without any
     * prior DELETE, preserving all existing rows not included in {@code bars}.
     */
    @Transactional
    public void upsertBars(String tableName, AssetType assetType, List<OhlcvBar> bars) {
        ensureTable(tableName);
        if (bars == null || bars.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        if (dialect.isPostgres()) {
            String insert = """
                    INSERT INTO "%s" (open_time, open_price, high_price, low_price, close_price, volume, asset_type, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (open_time) DO UPDATE SET
                        open_price = EXCLUDED.open_price,
                        high_price = EXCLUDED.high_price,
                        low_price = EXCLUDED.low_price,
                        close_price = EXCLUDED.close_price,
                        volume = EXCLUDED.volume,
                        updated_at = EXCLUDED.updated_at
                    """.formatted(tableName);
            for (OhlcvBar bar : bars) {
                jdbc.update(insert,
                        Timestamp.valueOf(bar.getOpenTime()),
                        bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(),
                        bar.getVolume(), assetType.name(), Timestamp.valueOf(now));
            }
        } else {
            // H2 uses INSERT OR REPLACE (SQLite-style syntax supported by H2 compat mode)
            String insert = """
                    MERGE INTO "%s" (open_time, open_price, high_price, low_price, close_price, volume, asset_type, updated_at)
                    KEY (open_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.formatted(tableName);
            for (OhlcvBar bar : bars) {
                jdbc.update(insert,
                        bar.getOpenTime().toString(),
                        bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(),
                        bar.getVolume(), assetType.name(), now.toString());
            }
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<OhlcvBar> findBars(String tableName, String symbol, String timeframe,
                                   AssetType assetType, int limit) {
        if (!tableExists(tableName)) {
            return List.of();
        }
        String sql = """
                SELECT open_time, open_price, high_price, low_price, close_price, volume
                FROM "%s"
                ORDER BY open_time DESC
                LIMIT ?
                """.formatted(tableName);

        List<OhlcvBar> rows = jdbc.query(sql, (rs, rowNum) -> mapRow(rs, symbol, timeframe, assetType), limit);
        List<OhlcvBar> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(OhlcvBar::getOpenTime));
        return ordered;
    }

    public int countBars(String tableName) {
        if (!tableExists(tableName)) {
            return 0;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"%s\"".formatted(tableName), Integer.class);
        return count == null ? 0 : count;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean tableExists(String tableName) {
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM \"%s\"".formatted(tableName), Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private OhlcvBar mapRow(ResultSet rs, String symbol, String timeframe,
                            AssetType assetType) throws SQLException {
        LocalDateTime openTime = dialect.isPostgres()
                ? rs.getTimestamp("open_time").toLocalDateTime()
                : LocalDateTime.parse(rs.getString("open_time").replace(" ", "T"));
        return OhlcvBar.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .openTime(openTime)
                .open(readDecimal(rs, "open_price"))
                .high(readDecimal(rs, "high_price"))
                .low(readDecimal(rs, "low_price"))
                .close(readDecimal(rs, "close_price"))
                .volume(readDecimal(rs, "volume"))
                .assetType(assetType)
                .build();
    }

    private static BigDecimal readDecimal(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column) == null ? BigDecimal.ZERO : rs.getBigDecimal(column);
    }
}
