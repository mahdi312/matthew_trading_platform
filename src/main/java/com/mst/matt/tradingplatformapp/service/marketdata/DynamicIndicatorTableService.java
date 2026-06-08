package com.mst.matt.tradingplatformapp.service.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Per-symbol/timeframe indicator series tables (open_time + value). */
@Service
public class DynamicIndicatorTableService {

    private static final Logger log = LoggerFactory.getLogger(DynamicIndicatorTableService.class);
    private static final Set<String> ENSURED = ConcurrentHashMap.newKeySet();

    private final JdbcTemplate jdbc;
    private final DatabaseDialectHelper dialect;

    public DynamicIndicatorTableService(JdbcTemplate jdbc, DatabaseDialectHelper dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
    }

    public void ensureTable(String tableName) {
        if (!ENSURED.add(tableName)) {
            return;
        }
        jdbc.execute(dialect.createIndicatorSeriesTableDdl(tableName));
        log.debug("Ensured indicator table {}", tableName);
    }

    @Transactional
    public void upsertSeries(String tableName, List<LocalDateTime> times, List<Double> values) {
        if (times == null || values == null || times.isEmpty()) return;
        ensureTable(tableName);
        LocalDateTime now = LocalDateTime.now();
        int n = Math.min(times.size(), values.size());
        if (dialect.isPostgres()) {
            String sql = """
                    INSERT INTO "%s" (open_time, value, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (open_time) DO UPDATE SET
                        value = EXCLUDED.value,
                        updated_at = EXCLUDED.updated_at
                    """.formatted(tableName);
            for (int i = 0; i < n; i++) {
                Double v = values.get(i);
                if (v == null || v.isNaN()) continue;
                jdbc.update(sql, Timestamp.valueOf(times.get(i)), v, Timestamp.valueOf(now));
            }
        } else {
            String sql = """
                    INSERT OR REPLACE INTO "%s" (open_time, value, updated_at)
                    VALUES (?, ?, ?)
                    """.formatted(tableName);
            for (int i = 0; i < n; i++) {
                Double v = values.get(i);
                if (v == null || v.isNaN()) continue;
                jdbc.update(sql, times.get(i).toString(), v, now.toString());
            }
        }
    }

    public List<Double> loadSeries(String tableName, int limit) {
        if (!tableExists(tableName)) return List.of();
        String sql = """
                SELECT open_time, value FROM "%s"
                ORDER BY open_time DESC LIMIT ?
                """.formatted(tableName);
        List<SeriesPoint> points = jdbc.query(sql, (rs, row) -> {
            LocalDateTime t = dialect.isPostgres()
                    ? rs.getTimestamp("open_time").toLocalDateTime()
                    : LocalDateTime.parse(rs.getString("open_time").replace(" ", "T"));
            return new SeriesPoint(t, rs.getDouble("value"));
        }, limit);
        points.sort(Comparator.comparing(SeriesPoint::time));
        return points.stream().map(SeriesPoint::value).toList();
    }

    public boolean tableExists(String tableName) {
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM \"%s\"".formatted(tableName), Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private record SeriesPoint(LocalDateTime time, double value) {}
}
