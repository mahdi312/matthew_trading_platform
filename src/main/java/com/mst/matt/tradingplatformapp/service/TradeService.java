package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.Trade.*;
import com.mst.matt.tradingplatformapp.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Business logic layer for all trade operations.
 * Handles CRUD, P&L computation, portfolio statistics.
 */
@Service
@Transactional
public class TradeService {

    @Autowired private TradeRepository      tradeRepository;
    @Autowired private UserProfileRepository profileRepository;

    // ── CRUD ────────────────────────────────────────────────

    public Trade saveTrade(Trade trade) {
        trade.computePnL();
        return tradeRepository.save(trade);
    }

    public void deleteTrade(Long id) {
        tradeRepository.deleteById(id);
    }

    public Optional<Trade> findById(Long id) {
        return tradeRepository.findById(id);
    }

    public List<Trade> getTradesForProfile(UserProfile profile) {
        return tradeRepository.findByProfileOrderByEntryTimeDesc(profile);
    }

    public List<Trade> getOpenTrades(UserProfile profile) {
        return tradeRepository.findByProfileAndStatus(profile, TradeStatus.OPEN);
    }

    public List<Trade> getClosedTrades(UserProfile profile) {
        return tradeRepository.findByProfileAndStatus(profile, TradeStatus.CLOSED);
    }

    /**
     * Close an open trade with a given exit price.
     */
    public Trade closeTrade(Long tradeId, BigDecimal exitPrice) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new RuntimeException("Trade not found: " + tradeId));
        trade.setExitPrice(exitPrice);
        trade.setStatus(TradeStatus.CLOSED);
        trade.setExitTime(java.time.LocalDateTime.now());
        trade.computePnL();
        return tradeRepository.save(trade);
    }

    // ── Portfolio Statistics ─────────────────────────────────

    /**
     * Comprehensive stats for a given profile.
     */
    public PortfolioStats getStats(UserProfile profile) {
        List<Trade> closed = getClosedTrades(profile);
        List<Trade> open   = getOpenTrades(profile);

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
            totalPnl       = totalPnl.add(pnl);
            totalInvested  = totalInvested.add(
                    t.getTotalInvested() != null ? t.getTotalInvested() : BigDecimal.ZERO);

            if (t.getFee() != null) totalFees = totalFees.add(t.getFee());

            if (pnl.compareTo(BigDecimal.ZERO) > 0) wins++;
            else if (pnl.compareTo(BigDecimal.ZERO) < 0) losses++;

            if (bestTrade  == null || pnl.compareTo(bestTrade)  > 0) bestTrade  = pnl;
            if (worstTrade == null || pnl.compareTo(worstTrade) < 0) worstTrade = pnl;

            runningPnl = runningPnl.add(pnl);
            equityCurve.add(runningPnl);
        }

        int total   = wins + losses;
        BigDecimal winRate = total == 0 ? BigDecimal.ZERO :
                BigDecimal.valueOf(wins)
                        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        BigDecimal totalPnlPct = totalInvested.compareTo(BigDecimal.ZERO) != 0
                ? totalPnl.divide(totalInvested, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Average win and loss
        List<BigDecimal> winPnls  = closed.stream()
                .filter(t -> t.getPnlAmount() != null &&
                        t.getPnlAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(Trade::getPnlAmount).toList();
        List<BigDecimal> lossPnls = closed.stream()
                .filter(t -> t.getPnlAmount() != null &&
                        t.getPnlAmount().compareTo(BigDecimal.ZERO) < 0)
                .map(Trade::getPnlAmount).toList();

        BigDecimal avgWin  = average(winPnls);
        BigDecimal avgLoss = average(lossPnls);

        // Profit factor
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
                .bestTrade(bestTrade != null ? bestTrade : BigDecimal.ZERO)
                .worstTrade(worstTrade != null ? worstTrade : BigDecimal.ZERO)
                .avgWin(avgWin)
                .avgLoss(avgLoss)
                .profitFactor(profitFactor)
                .equityCurve(equityCurve)
                .build();
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    // ── Portfolio Stats DTO ──────────────────────────────────

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
