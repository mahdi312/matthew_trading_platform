package com.mst.matt.marketservice.model;

/**
 * Broad asset-class classification used across market-service models.
 *
 * <p>Mirrors {@code Trade.AssetType} from the desktop monolith and
 * {@code trading-service}, but defined here independently to avoid
 * cross-service entity dependencies.
 */
public enum AssetType {
    CRYPTO,
    STOCK,
    FOREX,
    COMMODITY,
    INDEX
}
