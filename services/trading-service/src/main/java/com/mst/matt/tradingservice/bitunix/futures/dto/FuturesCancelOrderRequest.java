package com.mst.matt.tradingservice.bitunix.futures.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Wire-format body for {@code POST /api/v1/futures/trade/cancel_orders}.
 *
 * <p>Supports batch cancel: up to the broker's documented limit per request.
 * {@code orderId} takes priority over {@code clientId} when both are present.</p>
 */
@Data
@Builder
public class FuturesCancelOrderRequest {

    /** Symbol for all orders in this cancel batch. */
    private String symbol;

    /** List of orders to cancel. Each entry has either orderId or clientId. */
    private List<OrderRef> orderList;

    @Data
    @Builder
    public static class OrderRef {
        /** BitUnix-assigned order id. Takes priority over {@code clientId}. */
        private String orderId;
        /** Client-assigned idempotency key (fallback if {@code orderId} absent). */
        private String clientId;
    }
}
