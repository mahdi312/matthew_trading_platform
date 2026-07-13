package com.mst.matt.tradingservice.bitunix.ws.dto;

import lombok.Data;

/**
 * Payload for the BitUnix Futures private WebSocket {@code balance} channel
 * (Step 6 — Futures WS push model).
 *
 * <h3>Wire format (example)</h3>
 * <pre>{@code
 * {
 *   "ch":  "balance",
 *   "ts":  1712345678901,
 *   "data": {
 *     "coin":              "USDT",
 *     "available":         "4900.00",
 *     "frozen":            "100.00",
 *     "isolationFrozen":   "80.00",
 *     "crossFrozen":       "20.00",
 *     "margin":            "500.00",
 *     "isolationMargin":   "300.00",
 *     "crossMargin":       "200.00",
 *     "expMoney":          "4560.00"
 *   }
 * }
 * }</pre>
 *
 * <p>All monetary fields are serialised as strings to preserve precision.</p>
 */
@Data
public class FuturesWsBalanceData {

    /**
     * Asset / coin ticker (e.g., {@code "USDT"}).
     */
    private String coin;

    /**
     * Available (free) balance — can be used to open new positions.
     */
    private String available;

    /**
     * Total frozen balance (sum of isolation- and cross-frozen).
     */
    private String frozen;

    /**
     * Frozen balance in isolation-margin positions.
     */
    private String isolationFrozen;

    /**
     * Frozen balance in cross-margin positions.
     */
    private String crossFrozen;

    /**
     * Total margin locked as collateral (sum of isolation- and cross-margin).
     */
    private String margin;

    /**
     * Collateral locked for isolation-margin positions.
     */
    private String isolationMargin;

    /**
     * Collateral locked for cross-margin positions.
     */
    private String crossMargin;

    /**
     * Estimated equity (total account value including unrealised PnL).
     * BitUnix field name: {@code expMoney}.
     */
    private String expMoney;
}
