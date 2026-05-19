package com.mst.matt.tradingplatformapp.service.analysis;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Detects key support and resistance levels using three methods:
 *
 *   1. Fibonacci Retracement / Extension from detected swing high/low
 *   2. Pivot Points (Classic, Woodie, Camarilla)
 *   3. Swing High/Low detection (fractal-based price action levels)
 */
@Service
public class SupportResistanceService {

    /**
     * Full S/R analysis result.
     */
    public SRResult analyze(List<OhlcvBar> bars, int lookback) {
        if (bars == null || bars.size() < 10) return SRResult.empty();

        // Trim to lookback window
        List<OhlcvBar> window = bars.size() > lookback
                ? bars.subList(bars.size() - lookback, bars.size())
                : bars;

        OhlcvBar lastBar = bars.get(bars.size() - 1);
        double currentPrice = lastBar.getClose().doubleValue();

        // ── Swing High / Low ─────────────────────────────────
        SwingPoints swings = detectSwingPoints(window, 5);

        // Most recent swing high and low for Fibonacci
        double swingHigh = swings.highs().stream()
                .mapToDouble(Double::doubleValue).max().orElse(currentPrice * 1.1);
        double swingLow  = swings.lows().stream()
                .mapToDouble(Double::doubleValue).min().orElse(currentPrice * 0.9);

        // ── Fibonacci Levels ─────────────────────────────────
        FibonacciLevels fib = computeFibonacci(swingHigh, swingLow);

        // ── Pivot Points ─────────────────────────────────────
        PivotPoints pivots = computePivotPoints(lastBar);

        // ── Classify Levels as Support or Resistance ─────────
        List<SRLevel> supports   = new ArrayList<>();
        List<SRLevel> resistances = new ArrayList<>();

        // Fibonacci
        addFibLevels(fib, currentPrice, supports, resistances);

        // Pivots
        addPivotLevels(pivots, currentPrice, supports, resistances);

        // Swing points
        swings.highs().forEach(h -> {
            SRLevel l = new SRLevel(h, SRLevel.Type.SWING_HIGH, SRLevel.Source.SWING, 0.8);
            if (h > currentPrice) resistances.add(l); else supports.add(l);
        });
        swings.lows().forEach(lo -> {
            SRLevel l = new SRLevel(lo, SRLevel.Type.SWING_LOW, SRLevel.Source.SWING, 0.8);
            if (lo < currentPrice) supports.add(l); else resistances.add(l);
        });

        // Sort: supports desc (closest above current = first), resistances asc
        supports.sort(Comparator.comparingDouble(SRLevel::price).reversed());
        resistances.sort(Comparator.comparingDouble(SRLevel::price));

        // Best buy and sell suggestions from S/R
        double bestBuyPrice  = supports.isEmpty()    ? currentPrice * 0.97
                : supports.get(0).price();
        double bestSellPrice = resistances.isEmpty() ? currentPrice * 1.03
                : resistances.get(0).price();

        return SRResult.builder()
                .currentPrice(currentPrice)
                .swingHigh(swingHigh)
                .swingLow(swingLow)
                .fibonacci(fib)
                .pivots(pivots)
                .supports(supports)
                .resistances(resistances)
                .bestBuyPrice(bestBuyPrice)
                .bestSellPrice(bestSellPrice)
                .build();
    }

    // ── Fibonacci Retracement ────────────────────────────────

    private FibonacciLevels computeFibonacci(double high, double low) {
        double range = high - low;

        return FibonacciLevels.builder()
                .high(high)
                .low(low)
                .level0(high)
                .level236(high - 0.236 * range)
                .level382(high - 0.382 * range)
                .level500(high - 0.500 * range)
                .level618(high - 0.618 * range)
                .level786(high - 0.786 * range)
                .level1000(low)
                // Extensions
                .ext1272(high + 0.272 * range)
                .ext1618(high + 0.618 * range)
                .ext2618(low  - 1.618 * range)
                .build();
    }

    // ── Pivot Points (Classic) ───────────────────────────────

    private PivotPoints computePivotPoints(OhlcvBar lastBar) {
        double h = lastBar.getHigh().doubleValue();
        double l = lastBar.getLow().doubleValue();
        double c = lastBar.getClose().doubleValue();

        double pivot = (h + l + c) / 3.0;
        double r1    = 2 * pivot - l;
        double r2    = pivot + (h - l);
        double r3    = h + 2 * (pivot - l);
        double s1    = 2 * pivot - h;
        double s2    = pivot - (h - l);
        double s3    = l - 2 * (h - pivot);

        return PivotPoints.builder()
                .pivot(pivot)
                .r1(r1).r2(r2).r3(r3)
                .s1(s1).s2(s2).s3(s3)
                .build();
    }

    // ── Swing High / Low Detection (Fractal Method) ──────────

    private SwingPoints detectSwingPoints(List<OhlcvBar> bars, int lookAround) {
        List<Double> highs = new ArrayList<>();
        List<Double> lows  = new ArrayList<>();

        for (int i = lookAround; i < bars.size() - lookAround; i++) {
            double h = bars.get(i).getHigh().doubleValue();
            double l = bars.get(i).getLow().doubleValue();

            boolean isSwingHigh = true;
            boolean isSwingLow  = true;

            for (int j = i - lookAround; j <= i + lookAround; j++) {
                if (j == i) continue;
                if (bars.get(j).getHigh().doubleValue() >= h) isSwingHigh = false;
                if (bars.get(j).getLow().doubleValue()  <= l) isSwingLow  = false;
            }

            if (isSwingHigh) highs.add(h);
            if (isSwingLow)  lows.add(l);
        }

        // Keep the 5 most recent swing points
        if (highs.size() > 5) highs = new ArrayList<>(highs.subList(highs.size() - 5, highs.size()));
        if (lows.size()  > 5) lows  = new ArrayList<>(lows.subList(lows.size()   - 5, lows.size()));

        return new SwingPoints(highs, lows);
    }

    private void addFibLevels(FibonacciLevels fib, double current,
                              List<SRLevel> supports, List<SRLevel> resistances) {
        Map<String, Double> fibMap = Map.of(
                "Fib 0.0%",   fib.getLevel0(),
                "Fib 23.6%",  fib.getLevel236(),
                "Fib 38.2%",  fib.getLevel382(),
                "Fib 50.0%",  fib.getLevel500(),
                "Fib 61.8%",  fib.getLevel618(),
                "Fib 78.6%",  fib.getLevel786(),
                "Fib 100.0%", fib.getLevel1000()
        );
        fibMap.forEach((label, price) -> {
            SRLevel level = new SRLevel(price, SRLevel.Type.FIBONACCI,
                    SRLevel.Source.FIBONACCI, 0.9, label);
            if (price < current) supports.add(level);
            else                 resistances.add(level);
        });
    }

    private void addPivotLevels(PivotPoints p, double current,
                                List<SRLevel> supports, List<SRLevel> resistances) {
        List<double[]> pivotLevels = List.of(
                new double[]{p.getS3(), 0.7}, new double[]{p.getS2(), 0.8},
                new double[]{p.getS1(), 0.9}, new double[]{p.getPivot(), 1.0},
                new double[]{p.getR1(), 0.9}, new double[]{p.getR2(), 0.8},
                new double[]{p.getR3(), 0.7}
        );
        String[] labels = {"S3","S2","S1","Pivot","R1","R2","R3"};
        for (int i = 0; i < pivotLevels.size(); i++) {
            double price    = pivotLevels.get(i)[0];
            double strength = pivotLevels.get(i)[1];
            SRLevel level   = new SRLevel(price, SRLevel.Type.PIVOT,
                    SRLevel.Source.PIVOT, strength, labels[i]);
            if (price < current) supports.add(level);
            else                 resistances.add(level);
        }
    }

    // ── DTOs ─────────────────────────────────────────────────

    public record SwingPoints(List<Double> highs, List<Double> lows) {}

    @lombok.Data @lombok.Builder
    @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class SRResult {
        private double currentPrice;
        private double swingHigh;
        private double swingLow;
        private FibonacciLevels fibonacci;
        private PivotPoints pivots;
        private List<SRLevel> supports;
        private List<SRLevel> resistances;
        private double bestBuyPrice;
        private double bestSellPrice;

        public static SRResult empty() {
            return SRResult.builder()
                    .supports(new ArrayList<>())
                    .resistances(new ArrayList<>())
                    .fibonacci(new FibonacciLevels())
                    .pivots(new PivotPoints())
                    .build();
        }
    }

    @lombok.Data @lombok.Builder
    @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class FibonacciLevels {
        private double high, low;
        private double level0, level236, level382, level500,
                level618, level786, level1000;
        private double ext1272, ext1618, ext2618;
    }

    @lombok.Data @lombok.Builder
    @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class PivotPoints {
        private double pivot;
        private double r1, r2, r3;
        private double s1, s2, s3;
    }

    public static class SRLevel {
        public enum Type   { FIBONACCI, PIVOT, SWING_HIGH, SWING_LOW }
        public enum Source { FIBONACCI, PIVOT, SWING }

        private final double price;
        private final Type   type;
        private final Source source;
        private final double strength;  // 0.0 – 1.0
        private final String label;

        public SRLevel(double price, Type type, Source source, double strength) {
            this(price, type, source, strength, type.name());
        }
        public SRLevel(double price, Type type, Source source,
                       double strength, String label) {
            this.price = price; this.type = type;
            this.source = source; this.strength = strength; this.label = label;
        }

        public double price()    { return price; }
        public Type   type()     { return type; }
        public Source source()   { return source; }
        public double strength() { return strength; }
        public String label()    { return label; }
    }
}