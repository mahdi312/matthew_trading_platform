package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DynamicOhlcvTableService {

    private static final Logger log = LoggerFactory.getLogger(DynamicOhlcvTableService.class);
    private static final Set<String> ENSURED = ConcurrentHashMap.newKeySet();

    private final JdbcTemplate jdbc;
    private final DatabaseDialectHelper dialect;

    public DynamicOhlcvTableService(JdbcTemplate jdbc, DatabaseDialectHelper dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
    }

    public void ensureTable(String tableName) {
        if (!ENSURED.add(tableName)) {
            return;
        }
        jdbc.execute(dialect.createOhlcvTableDdl(tableName));
        log.debug("Ensured OHLCV table {}", tableName);
    }

    @Transactional
    public void replaceBars(String tableName, String symbol, String timeframe,
                            Trade.AssetType assetType, List<OhlcvBar> bars) {
        ensureTable(tableName);
        jdbc.update("DELETE FROM \"%s\"".formatted(tableName));
        if (bars.isEmpty()) {
            return;
        }

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
            String insert = """
                    INSERT OR REPLACE INTO "%s"
                    (open_time, open_price, high_price, low_price, close_price, volume, asset_type, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.formatted(tableName);
            for (OhlcvBar bar : bars) {
                jdbc.update(insert,
                        bar.getOpenTime().toString(),
                        bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(),
                        bar.getVolume(), assetType.name(), now.toString());
            }
        }
    }

    public List<OhlcvBar> findBars(String tableName, String symbol, String timeframe,
                                   Trade.AssetType assetType, int limit) {
        if (!tableExists(tableName)) {
            return List.of();
        }
        String sql = """
                SELECT open_time, open_price, high_price, low_price, close_price, volume
                FROM "%s"
                ORDER BY open_time DESC
                LIMIT ?
                """.formatted(tableName);

        List<OhlcvBar> rows = jdbc.query(sql, (rs, rowNum) -> {
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
        }, limit);

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

    private boolean tableExists(String tableName) {
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM \"%s\"".formatted(tableName), Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static BigDecimal readDecimal(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        if (rs.getObject(column) == null) {
            return BigDecimal.ZERO;
        }
        return rs.getBigDecimal(column);
    }
}
