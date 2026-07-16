package com.mst.matt.marketservice.bitunix.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw JSON shape of a single element in BitUnix's
 * {@code GET /api/v1/futures/market/kline} {@code data} array.
 *
 * <p>Numeric fields are declared as {@code String} even though BitUnix's
 * example response shows some of them unquoted (e.g. {@code open: 60000})
 * — Gson's {@code String} adapter accepts both quoted-string and bare-number
 * JSON tokens transparently, so this is safe either way and avoids precision
 * loss from routing through {@code double}.</p>
 *
 * <p>This is an internal wire-format DTO — never exposed outside
 * {@code BitUnixMarketDataProvider}, which maps it to the shared
 * {@link com.mst.matt.contracts.dto.OhlcvBarDto}.</p>
 */
@Data
@NoArgsConstructor
public class BitUnixKlineItem {

    /** Opening price of the candle. */
    private String open;

    /** Highest price during the candle. */
    private String high;

    /** Lowest price during the candle. */
    private String low;

    /** Closing price of the candle (equals the current price if still forming). */
    private String close;

    /** Candle open-time, unix milliseconds. */
    private long time;

    /** Quote-asset volume traded during the candle (e.g. USDT volume). */
    private String quoteVol;

    /** Base-asset volume traded during the candle (e.g. BTC volume). */
    private String baseVol;

    /** Price type this candle was built from: {@code LAST_PRICE} or {@code MARK_PRICE}. */
    private String type;
}
