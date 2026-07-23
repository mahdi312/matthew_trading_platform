package com.mst.matt.marketservice.charting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * A single anchor stored in absolute price-time coordinates.
 * POJO — not a JPA entity. Ported from desktop {@code model.ChartPoint}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartPoint {

    /** Epoch milliseconds — canonical storage field. */
    private long timeEpoch;

    private double price;

    public LocalDateTime getTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timeEpoch), ZoneOffset.UTC);
    }

    public void setTime(LocalDateTime time) {
        this.timeEpoch = time == null ? 0L : time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public static ChartPoint of(LocalDateTime time, double price) {
        ChartPoint p = new ChartPoint();
        p.setTime(time);
        p.setPrice(price);
        return p;
    }

    public static ChartPoint ofEpoch(long epochMillis, double price) {
        ChartPoint p = new ChartPoint();
        p.setTimeEpoch(epochMillis);
        p.setPrice(price);
        return p;
    }
}
