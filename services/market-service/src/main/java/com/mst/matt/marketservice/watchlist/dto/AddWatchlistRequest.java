package com.mst.matt.marketservice.watchlist.dto;

import lombok.Data;

/**
 * Request body for {@code POST /api/market/watchlist}.
 */
@Data
public class AddWatchlistRequest {

    /** Upper-case trading symbol, e.g. "BTCUSDT" or "AAPL". Required. */
    private String symbol;

    /** Optional asset class hint: CRYPTO | STOCK | FOREX. */
    private String assetClass;
}
