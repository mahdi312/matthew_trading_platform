package com.mst.matt.tradingservice.dto;

import com.mst.matt.tradingservice.model.Trade;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outbound DTO for a single trade.
 *
 * <p>Mirrors the {@link Trade} entity fields but is decoupled from JPA annotations
 * so we can safely evolve the wire format without touching persistence mappings.
 * The controller maps {@link Trade} → {@link TradeResponse} via
 * {@link #from(Trade)}.
 */
@Data
@Builder
public class TradeResponse {

    private Long id;
    private Long userId;
    private String symbol;
    private String assetName;
    private Trade.AssetType assetType;
    private Trade.TradeDirection direction;
    private Trade.TradeStatus status;
    private Trade.TradeSource source;

    /** Broker order ID (non-null for BROKER_LIVE trades). */
    private String brokerOrderId;

    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private BigDecimal quantity;
    private BigDecimal stopLoss;
    private BigDecimal takeProfit;
    private BigDecimal fee;

    private LocalDateTime entryTime;
    private LocalDateTime exitTime;

    private String notes;
    private String exchange;
    private String strategy;
    private String screenshotPath;

    // Computed / derived
    private BigDecimal pnlAmount;
    private BigDecimal pnlPercent;
    private BigDecimal totalInvested;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Factory method — maps a {@link Trade} entity to this DTO.
     */
    public static TradeResponse from(Trade t) {
        return TradeResponse.builder()
                .id(t.getId())
                .userId(t.getUserId())
                .symbol(t.getSymbol())
                .assetName(t.getAssetName())
                .assetType(t.getAssetType())
                .direction(t.getDirection())
                .status(t.getStatus())
                .source(t.getSource())
                .brokerOrderId(t.getBrokerOrderId())
                .entryPrice(t.getEntryPrice())
                .exitPrice(t.getExitPrice())
                .quantity(t.getQuantity())
                .stopLoss(t.getStopLoss())
                .takeProfit(t.getTakeProfit())
                .fee(t.getFee())
                .entryTime(t.getEntryTime())
                .exitTime(t.getExitTime())
                .notes(t.getNotes())
                .exchange(t.getExchange())
                .strategy(t.getStrategy())
                .screenshotPath(t.getScreenshotPath())
                .pnlAmount(t.getPnlAmount())
                .pnlPercent(t.getPnlPercent())
                .totalInvested(t.getTotalInvested())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
