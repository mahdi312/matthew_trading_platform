package com.mst.matt.tradingplatformapp.model;

import com.mst.matt.tradingplatformapp.model.Trade.TradeDirection;

import java.math.BigDecimal;

/** Pre-filled trade data extracted from a Long/Short position drawing. */
public record TradeDrawingDraft(
        String symbol,
        TradeDirection direction,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        Trade.AssetType assetType,
        /** Absolute path to the auto-captured chart screenshot PNG, or {@code null} if not captured. */
        String screenshotPath
) {
    /** Backward-compatible constructor without screenshot. */
    public TradeDrawingDraft(String symbol, TradeDirection direction,
                              BigDecimal entryPrice, BigDecimal stopLoss,
                              BigDecimal takeProfit, Trade.AssetType assetType) {
        this(symbol, direction, entryPrice, stopLoss, takeProfit, assetType, null);
    }
}
