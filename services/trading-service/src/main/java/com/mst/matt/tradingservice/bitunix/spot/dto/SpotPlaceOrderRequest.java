package com.mst.matt.tradingservice.bitunix.spot.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Wire-format body for {@code POST /api/spot/v1/order/place_order}.
 *
 * <h3>Encoding mismatch vs futures</h3>
 * <p>BitUnix's spot REST API uses <em>numeric</em> enums while futures uses
 * <em>string</em> enums — the translation layer lives in
 * {@link com.mst.matt.tradingservice.bitunix.spot.BitUnixSpotOrderClient}.</p>
 *
 * <pre>
 * side:  1 = Sell, 2 = Buy
 * type:  1 = Limit, 2 = Market
 * </pre>
 */
@Data
@Builder
public class SpotPlaceOrderRequest {

    /** Trading symbol, e.g. {@code "BTCUSDT"}. */
    private String symbol;

    /**
     * Order direction: {@code 1} = Sell, {@code 2} = Buy.
     * Translated from {@link com.mst.matt.contracts.enums.OrderSide} by the client.
     */
    private int side;

    /**
     * Order type: {@code 1} = Limit, {@code 2} = Market.
     * Translated from {@link com.mst.matt.contracts.enums.OrderType} by the client.
     */
    private int type;

    /** Order quantity (base-asset units). */
    private String volume;

    /** Order price — required for limit orders, omit for market. */
    private String price;
}
