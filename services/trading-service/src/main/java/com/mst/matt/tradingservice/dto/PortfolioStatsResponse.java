package com.mst.matt.tradingservice.dto;

import com.mst.matt.tradingservice.service.TradeService.PortfolioStats;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outbound DTO for portfolio-level statistics.
 *
 * <p>Maps from the service-layer {@link PortfolioStats} inner class.
 * Expressed as a flat JSON object for easy consumption by the frontend dashboard.
 */
@Data
@Builder
public class PortfolioStatsResponse {

    private int totalTrades;
    private int openTrades;
    private int wins;
    private int losses;

    /** Win rate as a percentage (0–100). */
    private BigDecimal winRate;

    /** Total realised P&L across all closed trades (quote currency). */
    private BigDecimal totalPnl;

    /** Total P&L as a percentage of total invested capital. */
    private BigDecimal totalPnlPercent;

    /** Sum of entryPrice × quantity for all closed trades. */
    private BigDecimal totalInvested;

    /** Total fees paid across all closed trades. */
    private BigDecimal totalFees;

    /** Single best trade P&L. */
    private BigDecimal bestTrade;

    /** Single worst trade P&L (most negative). */
    private BigDecimal worstTrade;

    /** Average P&L of winning trades. */
    private BigDecimal avgWin;

    /** Average P&L of losing trades (negative). */
    private BigDecimal avgLoss;

    /**
     * Profit factor = gross profit / |gross loss|.
     * Values &gt; 1 indicate a net-profitable strategy.
     * Returns 999 if gross loss is zero and there is profit.
     */
    private BigDecimal profitFactor;

    /**
     * Running cumulative P&L after each closed trade, in chronological order.
     * Useful for rendering an equity curve chart.
     */
    private List<BigDecimal> equityCurve;

    /**
     * Factory method — maps the service-layer {@link PortfolioStats} to this DTO.
     */
    public static PortfolioStatsResponse from(PortfolioStats stats) {
        return PortfolioStatsResponse.builder()
                .totalTrades(stats.getTotalTrades())
                .openTrades(stats.getOpenTrades())
                .wins(stats.getWins())
                .losses(stats.getLosses())
                .winRate(stats.getWinRate())
                .totalPnl(stats.getTotalPnl())
                .totalPnlPercent(stats.getTotalPnlPercent())
                .totalInvested(stats.getTotalInvested())
                .totalFees(stats.getTotalFees())
                .bestTrade(stats.getBestTrade())
                .worstTrade(stats.getWorstTrade())
                .avgWin(stats.getAvgWin())
                .avgLoss(stats.getAvgLoss())
                .profitFactor(stats.getProfitFactor())
                .equityCurve(stats.getEquityCurve())
                .build();
    }
}
