package com.mst.matt.marketservice.bitunix.ws;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code data} payload shape for BitUnix WS kline pushes
 * ({@code ch: "market_kline_{interval}"}), per the migration guide's
 * documented push-message example:
 * <pre>{"o":"68581.4","h":"68590","l":"68579.5","c":"68583.4","b":"5.2395","q":"359348.14078"}</pre>
 */
@Data
@NoArgsConstructor
public class BitUnixWsKlineData {

    /** Open price of the (possibly still-forming) candle. */
    private String o;

    /** High price so far in the candle. */
    private String h;

    /** Low price so far in the candle. */
    private String l;

    /** Current close price of the candle (last trade if still forming). */
    private String c;

    /** Base-asset volume so far in the candle. */
    private String b;

    /** Quote-asset volume so far in the candle. */
    private String q;
}
