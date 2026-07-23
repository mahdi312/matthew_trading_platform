package com.mst.matt.marketservice.charting.model;

import java.math.BigDecimal;

/**
 * Pre-filled trade data extracted from a Long/Short position drawing.
 * Not a JPA entity — transient value object.
 *
 * <p>Ported from desktop {@code model.TradeDrawingDraft}.
 * {@code Trade.TradeDirection} and {@code Trade.AssetType} are defined
 * as inner enums here to avoid a desktop dependency.
 */
public record TradeDrawingDraft(
        String      symbol,
        Direction   direction,
        BigDecimal  entryPrice,
        BigDecimal  stopLoss,
        BigDecimal  takeProfit,
        AssetType   assetType,
        String      screenshotPath
) {
    /** Backward-compatible constructor without screenshot. */
    public TradeDrawingDraft(String symbol, Direction direction,
                              BigDecimal entryPrice, BigDecimal stopLoss,
                              BigDecimal takeProfit, AssetType assetType) {
        this(symbol, direction, entryPrice, stopLoss, takeProfit, assetType, null);
    }

    // ── Inner enums (ported from desktop Trade.* inner types) ─────────────────

    public enum Direction { LONG, SHORT }

    public enum AssetType { CRYPTO, STOCK, FOREX, FUTURES, OPTIONS, OTHER }
}
