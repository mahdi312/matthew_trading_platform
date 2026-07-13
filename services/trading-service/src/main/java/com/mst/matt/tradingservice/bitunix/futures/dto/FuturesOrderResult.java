package com.mst.matt.tradingservice.bitunix.futures.dto;

import com.mst.matt.tradingservice.bitunix.auth.BitUnixApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Response for {@code POST /api/v1/futures/trade/place_order} and
 * {@code POST /api/v1/futures/trade/modify_order}.
 *
 * <p>Extends {@link BitUnixApiResponse} with the typed {@code data} payload.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FuturesOrderResult extends BitUnixApiResponse<FuturesOrderResult.OrderData> {

    @Data
    public static class OrderData {
        /** BitUnix-assigned order id. */
        private String orderId;
        /** Echoed client-generated order id. */
        private String clientId;
    }
}
