package com.mst.matt.tradingplatformapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * A single anchor stored in absolute price-time coordinates.
 *
 * <p>Time is stored as {@code long} epoch-milliseconds to avoid reflection
 * issues with Gson on newer JVMs ({@code InaccessibleObjectException} for
 * {@code java.time.LocalDateTime}).  The helper methods {@link #getTime()} /
 * {@link #setTime(LocalDateTime)} provide backward-compatible access so
 * existing code that uses {@code LocalDateTime} continues to compile.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartPoint {

    /** Epoch milliseconds – the canonical storage field. */
    private long timeEpoch;

    private double price;

    // ── Backward-compatible helpers ──────────────────────────────────────────

    /** Returns the time as a {@link LocalDateTime} (UTC). */
    public LocalDateTime getTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timeEpoch), ZoneOffset.UTC);
    }

    /** Sets the time from a {@link LocalDateTime} (interpreted as UTC). */
    public void setTime(LocalDateTime time) {
        this.timeEpoch = time == null ? 0L :
                time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /** Convenience factory: build from a {@link LocalDateTime}. */
    public static ChartPoint of(LocalDateTime time, double price) {
        ChartPoint p = new ChartPoint();
        p.setTime(time);
        p.setPrice(price);
        return p;
    }

    /** Convenience factory: build from epoch millis. */
    public static ChartPoint ofEpoch(long epochMillis, double price) {
        ChartPoint p = new ChartPoint();
        p.setTimeEpoch(epochMillis);
        p.setPrice(price);
        return p;
    }
}
