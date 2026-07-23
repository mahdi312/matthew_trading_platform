package com.mst.matt.marketservice.bitunix.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw JSON shape of a single element in BitUnix's
 * {@code GET /api/v1/futures/market/tickers} {@code data} array.
 *
 * <p>Internal wire-format DTO — never exposed outside
 * {@code BitUnixMarketDataProvider}, which maps it to the shared
 * {@link com.mst.matt.contracts.dto.TickerSnapshotDto}.</p>
 */
@Data
@NoArgsConstructor
public class BitUnixTickerItem {

    /** Trading symbol, e.g. {@code "BTCUSDT"}. */
    private String symbol;

    /** Current mark price (used for funding/liquidation calcs, not last trade). */
    private String markPrice;

    /** Last traded price. */
    private String lastPrice;

    /** 24h open price. Despite the field name, this is BitUnix's 24h-open, not last-trade. */
    private String open;

    /** Alias BitUnix also returns for the last traded price. */
    private String last;

    /** 24h quote-asset (e.g. USDT) trading volume. */
    private String quoteVol;

    /** 24h base-asset (e.g. BTC) trading volume. */
    private String baseVol;

    /** 24h high price. */
    private String high;

    /** 24h low price. */
    private String low;
}
