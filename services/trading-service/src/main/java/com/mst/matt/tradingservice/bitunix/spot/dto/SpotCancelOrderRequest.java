package com.mst.matt.tradingservice.bitunix.spot.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Wire-format body for {@code POST /api/spot/v1/order/cancel}.
 *
 * <p>Supports batch cancellation in a single API call.</p>
 */
@Data
@Builder
public class SpotCancelOrderRequest {

    /** List of orders to cancel (each needs orderId + symbol). */
    private List<OrderRef> orderIdList;

    @Data
    @Builder
    public static class OrderRef {
        /** BitUnix-assigned order id. */
        private String orderId;
        /** Trading symbol (required alongside orderId). */
        private String symbol;
    }
}
