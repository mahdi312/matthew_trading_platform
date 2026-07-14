package com.mst.matt.marketservice.charting.service;

import com.mst.matt.marketservice.model.OhlcvBar;
import lombok.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Detects key support and resistance levels using three methods:
 * <ol>
 *   <li>Fibonacci Retracement from detected swing high/low</li>
 *   <li>Classic Pivot Points</li>
 *   <li>Fractal Swing High/Low detection</li>
 * </ol>
 *
 * Ported from desktop {@code service.analysis.SupportResistanceService}.
 * Uses market-service's {@link OhlcvBar} instead of desktop's.
 */
@Service
public class SupportResistanceService {

    public SRResult analyze(List<OhlcvBar> bars, int lookback) {
        if (bars == null || bars.size() < 10) return SRResult.empty();

        List<OhlcvBar> window = bars.size() > lookback
                ? bars.subList(bars.size() - lookback, bars.size()) : bars;

        OhlcvBar last = bars.get(bars.size() - 1);
        double currentPrice = last.getClose().doubleValue();

        SwingPoints swings = detectSwingPoints(window, 5);

        double swingHigh = swings.highs().stream()
                .mapToDouble(Double::doubleValue).max().orElse(currentPrice * 1.1);
        double swingLow  = swings.lows().stream()
                .mapToDouble(Double::doubleValue).min().orElse(currentPrice * 0.9);

        FibonacciLevels fib    = computeFibonacci(swingHigh, swingLow);
        PivotPoints     pivots = computePivotPoints(last);

        List<SRLevel> supports    = new ArrayList<>();
        List<SRLevel> resistances = new ArrayList<>();

        addFibLevels(fib, currentPrice, supports, resistances);
        addPivotLevels(pivots, currentPrice, supports, resistances);

        swings.highs().forEach(h -> {
            SRLevel l = new SRLevel(h, SRLevel.Type.SWING_HIGH, SRLevel.Source.SWING, 0.8);
            if (h > currentPrice) resistances.add(l); else supports.add(l);
        });
        swings.lows().forEach(lo -> {
            SRLevel l = new SRLevel(lo, SRLevel.Type.SWING_LOW, SRLevel.Source.SWING, 0.8);
            if (lo < currentPrice) supports.add(l); else resistances.add(l);
        });

        supports.sort(Comparator.comparingDouble(SRLevel::price).reversed());
        resistances.sort(Comparator.comparingDouble(SRLevel::price));

        double bestBuyPrice  = supports.isEmpty()    ? currentPrice * 0.97 : supports.get(0).price();
        double bestSellPrice = resistances.isEmpty() ? currentPrice * 1.03 : resistances.get(0).price();

        return SRResult.builder()
                .currentPrice(currentPrice).swingHigh(swingHigh).swingLow(swingLow)
                .fibonacci(fib).pivots(pivots)
                .supports(supports).resistances(resistances)
                .bestBuyPrice(bestBuyPrice).bestSellPrice(bestSellPrice)
                .build();
    }

    // ── Fibonacci ─────────────────────────────────────────────────────────────

    private FibonacciLevels computeFibonacci(double high, double low) {
        double range = high - low;
        return FibonacciLevels.builder()
                .high(high).low(low)
                .level0(high)
                .level236(high - 0.236 * range)
                .level382(high - 0.382 * range)
                .level500(high - 0.500 * range)
                .level618(high - 0.618 * range)
                .level786(high - 0.786 * range)
                .level1000(low)
                .ext1272(high + 0.272 * range)
                .ext1618(high + 0.618 * range)
                .ext2618(low  - 1.618 * range)
                .build();
    }

    // ── Pivot Points (Classic) ────────────────────────────────────────────────

    private PivotPoints computePivotPoints(OhlcvBar bar) {
        double h = bar.getHigh().doubleValue();
        double l = bar.getLow().doubleValue();
        double c = bar.getClose().doubleValue();
        double pivot = (h + l + c) / 3.0;
        return PivotPoints.builder()
                .pivot(pivot)
                .r1(2 * pivot - l)   .r2(pivot + (h - l))   .r3(h + 2 * (pivot - l))
                .s1(2 * pivot - h)   .s2(pivot - (h - l))   .s3(l - 2 * (h - pivot))
                .build();
    }

    // ── Swing detection (fractal, 5-bar look-around) ──────────────────────────

    private SwingPoints detectSwingPoints(List<OhlcvBar> bars, int lookAround) {
        List<Double> highs = new ArrayList<>();
        List<Double> lows  = new ArrayList<>();
        for (int i = lookAround; i < bars.size() - lookAround; i++) {
            double h = bars.get(i).getHigh().doubleValue();
            double l = bars.get(i).getLow().doubleValue();
            boolean isHigh = true, isLow = true;
            for (int j = i - lookAround; j <= i + lookAround; j++) {
                if (j == i) continue;
                if (bars.get(j).getHigh().doubleValue() >= h) isHigh = false;
                if (bars.get(j).getLow().doubleValue()  <= l) isLow  = false;
            }
            if (isHigh) highs.add(h);
            if (isLow)  lows.add(l);
        }
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
            SRLevel level = new SRLevel(price, SRLevel.Type.FIBONACCI, SRLevel.Source.FIBONACCI, 0.9, label);
            if (price < current) supports.add(level); else resistances.add(level);
        });
    }

    private void addPivotLevels(PivotPoints p, double current,
                                 List<SRLevel> supports, List<SRLevel> resistances) {
        double[][] pairs   = {{p.getS3(),0.7},{p.getS2(),0.8},{p.getS1(),0.9},
                              {p.getPivot(),1.0},{p.getR1(),0.9},{p.getR2(),0.8},{p.getR3(),0.7}};
        String[]   labels  = {"S3","S2","S1","Pivot","R1","R2","R3"};
        for (int i = 0; i < pairs.length; i++) {
            double price = pairs[i][0]; double strength = pairs[i][1];
            SRLevel level = new SRLevel(price, SRLevel.Type.PIVOT, SRLevel.Source.PIVOT, strength, labels[i]);
            if (price < current) supports.add(level); else resistances.add(level);
        }
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    public record SwingPoints(List<Double> highs, List<Double> lows) {}

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class SRResult {
        private double currentPrice, swingHigh, swingLow;
        private FibonacciLevels fibonacci;
        private PivotPoints     pivots;
        private List<SRLevel>   supports;
        private List<SRLevel>   resistances;
        private double bestBuyPrice, bestSellPrice;

        public static SRResult empty() {
            return SRResult.builder()
                    .supports(new ArrayList<>()).resistances(new ArrayList<>())
                    .fibonacci(new FibonacciLevels()).pivots(new PivotPoints()).build();
        }
    }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class FibonacciLevels {
        private double high, low;
        private double level0, level236, level382, level500, level618, level786, level1000;
        private double ext1272, ext1618, ext2618;
    }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class PivotPoints {
        private double pivot;
        private double r1, r2, r3, s1, s2, s3;
    }

    public static class SRLevel {
        public enum Type   { FIBONACCI, PIVOT, SWING_HIGH, SWING_LOW }
        public enum Source { FIBONACCI, PIVOT, SWING }

        private final double price;
        private final Type   type;
        private final Source source;
        private final double strength;
        private final String label;

        public SRLevel(double price, Type type, Source source, double strength) {
            this(price, type, source, strength, type.name());
        }
        public SRLevel(double price, Type type, Source source, double strength, String label) {
            this.price = price; this.type = type; this.source = source;
            this.strength = strength; this.label = label;
        }
        public double price()    { return price; }
        public Type   type()     { return type; }
        public Source source()   { return source; }
        public double strength() { return strength; }
        public String label()    { return label; }
    }
}
