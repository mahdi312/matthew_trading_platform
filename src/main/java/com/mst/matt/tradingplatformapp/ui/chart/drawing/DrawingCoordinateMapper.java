package com.mst.matt.tradingplatformapp.ui.chart.drawing;

import com.mst.matt.tradingplatformapp.model.ChartPoint;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;

import java.time.LocalDateTime;
import java.util.List;

/** Converts between screen coordinates and absolute price-time units. */
public final class DrawingCoordinateMapper {

    private DrawingCoordinateMapper() {}

    public static int timeToBarIndex(List<OhlcvBar> bars, LocalDateTime time) {
        if (bars == null || bars.isEmpty() || time == null) return 0;
        int lo = 0, hi = bars.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            LocalDateTime t = bars.get(mid).getOpenTime();
            if (t.isBefore(time)) lo = mid + 1;
            else hi = mid;
        }
        if (lo > 0) {
            long dLo = Math.abs(java.time.Duration.between(bars.get(lo).getOpenTime(), time).toMillis());
            long dPrev = Math.abs(java.time.Duration.between(bars.get(lo - 1).getOpenTime(), time).toMillis());
            if (dPrev < dLo) return lo - 1;
        }
        return lo;
    }

    public static LocalDateTime barIndexToTime(List<OhlcvBar> bars, int index) {
        if (bars == null || bars.isEmpty()) return LocalDateTime.now();
        index = Math.max(0, Math.min(bars.size() - 1, index));
        return bars.get(index).getOpenTime();
    }

    public static double yToPrice(double y, double priceTop, double priceH,
                                  double maxPrice, double minPrice) {
        double range = Math.max(maxPrice - minPrice, 1e-10);
        return maxPrice - (y - priceTop) / priceH * range;
    }

    /**
     * Fix 2: Convert screen x-coordinate to an absolute bar index.
     * The returned index may be negative (empty space before data) or beyond bars.size()-1
     * (empty space after data) when the user has panned into the extended margin.
     */
    public static int xToBarIndex(double x, double left, double plotWidth,
                                  int startBarIndex, int visibleBars) {
        double barW = plotWidth / Math.max(1, visibleBars);
        int rel = (int) ((x - left) / barW);
        // Clamp rel to the visible slot range, then offset by startBarIndex
        rel = Math.max(0, Math.min(visibleBars - 1, rel));
        return rel + startBarIndex;
    }

    /** Snap to nearest OHLC of the bar at the given index. */
    public static double snapPrice(OhlcvBar bar, double price) {
        if (bar == null) return price;
        double[] levels = {
                bar.getOpen().doubleValue(),
                bar.getHigh().doubleValue(),
                bar.getLow().doubleValue(),
                bar.getClose().doubleValue()
        };
        double best = levels[0];
        double bestDist = Math.abs(price - best);
        for (double level : levels) {
            double d = Math.abs(price - level);
            if (d < bestDist) { bestDist = d; best = level; }
        }
        return best;
    }

    public static ChartPoint snapPoint(List<OhlcvBar> bars, ChartPoint pt) {
        if (bars == null || bars.isEmpty() || pt == null) return pt;
        int idx = timeToBarIndex(bars, pt.getTime());
        OhlcvBar bar = bars.get(Math.max(0, Math.min(bars.size() - 1, idx)));
        return ChartPoint.of(bar.getOpenTime(), snapPrice(bar, pt.getPrice()));
    }
}
