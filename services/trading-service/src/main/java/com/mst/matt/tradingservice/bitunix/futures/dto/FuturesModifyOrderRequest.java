package com.mst.matt.tradingservice.bitunix.futures.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Wire-format body for {@code POST /api/v1/futures/trade/modify_order}.
 */
@Data
@Builder
public class FuturesModifyOrderRequest {

    /** BitUnix-assigned order id of the order to modify (required). */
    private String orderId;

    /** Symbol the order was placed on (required). */
    private String symbol;

    /** New price (optional — omit to keep existing). */
    private String price;

    /** New quantity (optional — omit to keep existing). */
    private String qty;

    // ── Optional updated take-profit fields ───────────────────────────────────
    private String tpPrice;
    private String tpStopType;
    private String tpOrderType;
    private String tpOrderPrice;

    // ── Optional updated stop-loss fields ────────────────────────────────────
    private String slPrice;
    private String slStopType;
    private String slOrderType;
    private String slOrderPrice;
}
