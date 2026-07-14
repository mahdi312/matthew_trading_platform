package com.mst.matt.tradingservice.service;

import com.mst.matt.tradingservice.kafka.TradeEventPublisher;
import com.mst.matt.tradingservice.model.Trade;
import com.mst.matt.tradingservice.model.Trade.*;
import com.mst.matt.tradingservice.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Business logic layer for all trade journal operations.
 *
 * <p>Ported from the desktop monolith's {@code TradeService}, adapted to:
 * <ul>
 *   <li>Use constructor injection (not field {@code @Autowired}) per current Spring conventions.</li>
 *   <li>Operate on {@code Long userId} instead of a {@code UserProfile} entity, since
 *       user identity is managed by identity-service.</li>
 *   <li>Expose a {@link PortfolioStats} inner class for portfolio-level aggregations.</li>
 * </ul>
 *
 * <p>This service covers <strong>journal-only</strong> CRUD.
 * Live broker order placement is handled separately — see Step 6 wiring in
 * {@link com.mst.matt.tradingservice.service.TradingOrchestrationService}, which
 * delegates to {@code TradingProvider} (BitUnixTradingProvider) and then persists
 * the resulting trade here.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TradeService {

    private final TradeRepository tradeRepository;

    /**
     * Optional — Kafka producer for {@code trades.executed} / {@code trades.closed}.
     * {@code @Autowired(required = false)} so the service starts cleanly when
     * Kafka is not available in local dev without a broker.
     */
    @Autowired(required = false)
    private TradeEventPublisher tradeEventPublisher;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /**
     * Persist a new trade or update an existing one.
     * P&L is recomputed via {@link Trade#computePnL()} in the {@code @PrePersist}/{@code @PreUpdate}
     * JPA lifecycle callbacks, but we call it explicitly here for safety.
     */
    public Trade saveTrade(Trade trade) {
        trade.computePnL();
        Trade saved = tradeRepository.save(trade);
        log.debug("Trade saved: id={} userId={} symbol={} status={}",
                saved.getId(), saved.getUserId(), saved.getSymbol(), saved.getStatus());
        // Publish trades.executed so notification-service fans out trade notifications (Step 8)
        if (tradeEventPublisher != null && saved.getStatus() == TradeStatus.OPEN) {
            tradeEventPublisher.publishExecuted(saved);
        }
        return saved;
    }

    public void deleteTrade(Long id) {
        tradeRepository.deleteById(id);
        log.debug("Trade deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public Optional<Trade> findById(Long id) {
        return tradeRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Trade> getTradesForUser(Long userId) {
        return tradeRepository.findByUserIdOrderByEntryTimeDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Trade> getOpenTrades(Long userId) {
        return tradeRepository.findByUserIdAndStatus(userId, TradeStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<Trade> getClosedTrades(Long userId) {
        return tradeRepository.findByUserIdAndStatus(userId, TradeStatus.CLOSED);
    }

    /**
     * Close an open trade with a given exit price.
     *
     * @param tradeId   ID of the trade to close
     * @param exitPrice the realised exit price (quote currency)
     * @return the updated, closed trade
     * @throws NoSuchElementException if no trade exists with that ID
     */
    public Trade closeTrade(Long tradeId, BigDecimal exitPrice) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new NoSuchElementException("Trade not found: " + tradeId));
        trade.setExitPrice(exitPrice);
        trade.setStatus(TradeStatus.CLOSED);
        trade.setExitTime(LocalDateTime.now());
        trade.computePnL();
        Trade closed = tradeRepository.save(trade);
        log.info("Trade closed: id={} userId={} symbol={} pnl={}",
                closed.getId(), closed.getUserId(), closed.getSymbol(), closed.getPnlAmount());
        // Publish trades.closed so notification-service fans out trade notifications (Step 8)
        if (tradeEventPublisher != null) {
            tradeEventPublisher.publishClosed(closed);
        }
        return closed;
    }

    // ── Portfolio Statistics ──────────────────────────────────────────────────

    /**
     * Computes comprehensive portfolio statistics for the given user.
     *
     * <p>Only closed trades contribute to realized P&L aggregations; open trades
     * are counted separately. Equity curve is a running-total list in chronological order.
     *
     * @param userId the user whose trades to aggregate
     * @return a {@link PortfolioStats} snapshot (never {@code null})
     */
    @Transactional(readOnly = true)
    public PortfolioStats getStats(Long userId) {
        List<Trade> closed = getClosedTrades(userId);
        List<Trade> open   = getOpenTrades(userId);

        if (closed.isEmpty() && open.isEmpty()) return PortfolioStats.empty();

        BigDecimal totalPnl      = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalFees     = BigDecimal.ZERO;
        BigDecimal bestTrade     = null;
        BigDecimal worstTrade    = null;
        int wins = 0, losses = 0;
        List<BigDecimal> equityCurve = new ArrayList<>();
        BigDecimal runningPnl = BigDecimal.ZERO;

        for (Trade t : closed) {
            BigDecimal pnl = t.getPnlAmount() != null ? t.getPnlAmount() : BigDecimal.ZERO;
            totalPnl      = totalPnl.add(pnl);
            totalInvested = totalInvested.add(
                    t.getTotalInvested() != null ? t.getTotalInvested() : BigDecimal.ZERO);

            if (t.getFee() != null) totalFees = totalFees.add(t.getFee());

            if (pnl.compareTo(BigDecimal.ZERO) > 0) wins++;
            else if (pnl.compareTo(BigDecimal.ZERO) < 0) losses++;

            if (bestTrade  == null || pnl.compareTo(bestTrade)  > 0) bestTrade  = pnl;
            if (worstTrade == null || pnl.compareTo(worstTrade) < 0) worstTrade = pnl;

            runningPnl = runningPnl.add(pnl);
            equityCurve.add(runningPnl);
        }

        int total = wins + losses;
        BigDecimal winRate = total == 0 ? BigDecimal.ZERO :
                BigDecimal.valueOf(wins)
                        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        BigDecimal totalPnlPct = totalInvested.compareTo(BigDecimal.ZERO) != 0
                ? totalPnl.divide(totalInvested, 6, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Average win / loss
        List<BigDecimal> winPnls = closed.stream()
                .filter(t -> t.getPnlAmount() != null
                        && t.getPnlAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(Trade::getPnlAmount).toList();
        List<BigDecimal> lossPnls = closed.stream()
                .filter(t -> t.getPnlAmount() != null
                        && t.getPnlAmount().compareTo(BigDecimal.ZERO) < 0)
                .map(Trade::getPnlAmount).toList();

        BigDecimal avgWin  = average(winPnls);
        BigDecimal avgLoss = average(lossPnls);

        // Profit factor = gross profit / |gross loss|
        BigDecimal grossProfit = winPnls.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss   = lossPnls.stream()
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitFactor = grossLoss.compareTo(BigDecimal.ZERO) != 0
                ? grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP)
                : grossProfit.compareTo(BigDecimal.ZERO) > 0
                        ? BigDecimal.valueOf(999) : BigDecimal.ZERO;

        return PortfolioStats.builder()
                .totalTrades(total)
                .openTrades(open.size())
                .wins(wins)
                .losses(losses)
                .winRate(winRate)
                .totalPnl(totalPnl)
                .totalPnlPercent(totalPnlPct)
                .totalInvested(totalInvested)
                .totalFees(totalFees)
                .bestTrade(bestTrade  != null ? bestTrade  : BigDecimal.ZERO)
                .worstTrade(worstTrade != null ? worstTrade : BigDecimal.ZERO)
                .avgWin(avgWin)
                .avgLoss(avgLoss)
                .profitFactor(profitFactor)
                .equityCurve(equityCurve)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    // ── Portfolio Stats inner DTO ─────────────────────────────────────────────

    /**
     * Snapshot of portfolio-level aggregated statistics.
     * Ported from the monolith's {@code TradeService.PortfolioStats}.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PortfolioStats {
        private int totalTrades;
        private int openTrades;
        private int wins;
        private int losses;
        private BigDecimal winRate;
        private BigDecimal totalPnl;
        private BigDecimal totalPnlPercent;
        private BigDecimal totalInvested;
        private BigDecimal totalFees;
        private BigDecimal bestTrade;
        private BigDecimal worstTrade;
        private BigDecimal avgWin;
        private BigDecimal avgLoss;
        private BigDecimal profitFactor;
        private List<BigDecimal> equityCurve;

        public static PortfolioStats empty() {
            return PortfolioStats.builder()
                    .equityCurve(new ArrayList<>())
                    .totalPnl(BigDecimal.ZERO)
                    .totalPnlPercent(BigDecimal.ZERO)
                    .totalInvested(BigDecimal.ZERO)
                    .totalFees(BigDecimal.ZERO)
                    .bestTrade(BigDecimal.ZERO)
                    .worstTrade(BigDecimal.ZERO)
                    .avgWin(BigDecimal.ZERO)
                    .avgLoss(BigDecimal.ZERO)
                    .profitFactor(BigDecimal.ZERO)
                    .winRate(BigDecimal.ZERO)
                    .build();
        }
    }
}
