package com.mst.matt.tradingservice.service;

import com.mst.matt.tradingservice.client.FundamentalsClient;
import com.mst.matt.tradingservice.dto.FundamentalsReportDto;
import com.mst.matt.tradingservice.model.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for the yearly profit/fundamentals report view.
 *
 * <p>Ported from the desktop monolith's {@code YearlyProfitController} service calls
 * — not the JavaFX FXML binding code.  The service:</p>
 * <ul>
 *   <li>Aggregates the user's closed trades by calendar year to produce per-year P&L rows.</li>
 *   <li>Optionally enriches the report with company fundamentals context fetched from
 *       {@code reference-data-service} via the {@link FundamentalsClient} Feign client
 *       (HTTP call — no local duplication of fundamentals logic).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YearlyReportService {

    private final TradeService tradeService;
    private final FundamentalsClient fundamentalsClient;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Build a yearly P&L summary for the given user, grouped by calendar year.
     *
     * @param userId the authenticated user
     * @return list of {@link YearlyPnlRow} ordered by year descending
     */
    @Transactional(readOnly = true)
    public List<YearlyPnlRow> getYearlyPnl(Long userId) {
        List<Trade> closed = tradeService.getClosedTrades(userId);
        if (closed.isEmpty()) return List.of();

        Map<Integer, List<Trade>> byYear = closed.stream()
                .filter(t -> t.getExitTime() != null)
                .collect(Collectors.groupingBy(t -> t.getExitTime().getYear()));

        return byYear.entrySet().stream()
                .map(e -> buildYearlyRow(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(YearlyPnlRow::getYear).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Fetch fundamentals context for a symbol from reference-data-service.
     * Returns {@code null} if the symbol is not a stock or if the service is unavailable.
     *
     * <p>This is the only call to reference-data-service from trading-service; no
     * fundamentals data is duplicated or stored here.</p>
     *
     * @param symbol   the ticker symbol (e.g. "AAPL")
     * @param provider optional provider name (e.g. "FINNHUB"); null → AUTO
     */
    public FundamentalsReportDto getFundamentalsContext(String symbol, String provider) {
        if (symbol == null || symbol.isBlank()) return null;
        try {
            FundamentalsReportDto report = fundamentalsClient.getFundamentals(
                    symbol.trim().toUpperCase(), provider);
            if (report == null) {
                log.debug("No fundamentals data from reference-data-service for symbol={}", symbol);
            }
            return report;
        } catch (Exception e) {
            log.warn("Failed to fetch fundamentals for symbol={} from reference-data-service: {}",
                    symbol, e.getMessage());
            return null;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private YearlyPnlRow buildYearlyRow(int year, List<Trade> trades) {
        BigDecimal totalPnl    = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalFees   = BigDecimal.ZERO;
        int wins = 0, losses = 0;

        for (Trade t : trades) {
            BigDecimal pnl = t.getPnlAmount() != null ? t.getPnlAmount() : BigDecimal.ZERO;
            totalPnl      = totalPnl.add(pnl);
            totalInvested = totalInvested.add(
                    t.getTotalInvested() != null ? t.getTotalInvested() : BigDecimal.ZERO);
            if (t.getFee() != null) totalFees = totalFees.add(t.getFee());
            if (pnl.compareTo(BigDecimal.ZERO) > 0) wins++;
            else if (pnl.compareTo(BigDecimal.ZERO) < 0) losses++;
        }

        int total = wins + losses;
        double winRatePct = total == 0 ? 0.0
                : (double) wins / total * 100.0;
        double returnPct  = totalInvested.compareTo(BigDecimal.ZERO) != 0
                ? totalPnl.divide(totalInvested, 6, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        return YearlyPnlRow.builder()
                .year(year)
                .totalTrades(total)
                .wins(wins)
                .losses(losses)
                .winRatePct(winRatePct)
                .totalPnl(totalPnl)
                .totalInvested(totalInvested)
                .totalFees(totalFees)
                .returnPct(returnPct)
                .build();
    }

    // ── Inner DTO ──────────────────────────────────────────────────────────────

    /**
     * Per-year P&L aggregation row for the REST response.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class YearlyPnlRow {
        private int year;
        private int totalTrades;
        private int wins;
        private int losses;
        private double winRatePct;
        private BigDecimal totalPnl;
        private BigDecimal totalInvested;
        private BigDecimal totalFees;
        /** Total P&L as a percentage of total invested capital for this year. */
        private double returnPct;
    }
}
