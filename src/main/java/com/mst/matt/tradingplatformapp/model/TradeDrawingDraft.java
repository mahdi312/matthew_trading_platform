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
        Trade.AssetType assetType
) {}
