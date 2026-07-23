package com.mst.matt.tradingservice.bitunix.spot.dto;

import com.mst.matt.tradingservice.bitunix.auth.BitUnixApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response for {@code POST /api/spot/v1/order/place_order}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpotPlaceOrderResult extends BitUnixApiResponse<SpotPlaceOrderResult.SpotOrderData> {

    @Data
    public static class SpotOrderData {
        /** BitUnix-assigned order id. */
        private String orderId;
        /** {@code 1}=Sell / {@code 2}=Buy (numeric, echoed from request). */
        private int side;
        /** {@code 1}=Limit / {@code 2}=Market. */
        private int type;
        /** Order quantity. */
        private String volume;
        /** Order price. */
        private String price;
        /** Symbol. */
        private String symbol;
        /**
         * Placement status: {@code 1} = success.
         * Note: same HTTP-200-≠-success caveat applies; confirm via WS if needed.
         */
        private int placeStatus;
    }
}
