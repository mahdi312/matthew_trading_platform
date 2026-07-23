package com.mst.matt.marketservice.bitunix.ws;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code data} payload shape for BitUnix WS ticker pushes
 * ({@code ch: "ticker"}), per the migration guide's documented push-message
 * example:
 * <pre>{"s":"BTCUSDT","la":"68650.9","o":"69141.6","h":"70319.9","l":"68241.9","b":"26295.3977","q":"1823374525.0193","r":"-0.7097029863"}</pre>
 */
@Data
@NoArgsConstructor
public class BitUnixWsTickerData {

    /** Symbol (duplicated from the envelope's top-level {@code symbol} field). */
    private String s;

    /** Last traded price — use this as the live tick's price. */
    private String la;

    /** 24h open price. */
    private String o;

    /** 24h high price. */
    private String h;

    /** 24h low price. */
    private String l;

    /** 24h base-asset volume. */
    private String b;

    /** 24h quote-asset volume. */
    private String q;

    /** 24h price-change percentage, as a decimal fraction (e.g. {@code "-0.7097029863"} = -0.71%). */
    private String r;
}
