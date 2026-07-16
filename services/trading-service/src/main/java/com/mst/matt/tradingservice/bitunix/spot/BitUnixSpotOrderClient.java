package com.mst.matt.tradingservice.bitunix.spot;

import com.mst.matt.contracts.enums.OrderSide;
import com.mst.matt.contracts.enums.OrderType;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixApiResponse;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixCredential;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixHttpClient;
import com.mst.matt.tradingservice.bitunix.spot.dto.SpotAccountResponse;
import com.mst.matt.tradingservice.bitunix.spot.dto.SpotCancelOrderRequest;
import com.mst.matt.tradingservice.bitunix.spot.dto.SpotPlaceOrderRequest;
import com.mst.matt.tradingservice.bitunix.spot.dto.SpotPlaceOrderResult;
import com.mst.matt.tradingservice.exception.TradingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Thin HTTP wrapper for BitUnix's <em>Spot</em> private trading endpoints
 * (Steps 6.4 – 6.5).
 *
 * <h3>Covered endpoints</h3>
 * <ul>
 *   <li>6.4 — {@code POST /api/spot/v1/order/place_order}</li>
 *   <li>6.4 — {@code POST /api/spot/v1/order/cancel}</li>
 *   <li>6.5 — {@code GET  /api/spot/v1/user/account}</li>
 * </ul>
 *
 * <h3>Numeric enum translation</h3>
 * <p>BitUnix's spot API encodes side and type as integers (1/2), unlike the
 * futures API which uses strings ("BUY"/"SELL", "LIMIT"/"MARKET"). This
 * translation is handled here so that callers work uniformly with the
 * domain enums from {@code shared/contracts}.</p>
 *
 * <pre>
 * side: OrderSide.SELL → 1,  OrderSide.BUY → 2
 * type: OrderType.LIMIT → 1, OrderType.MARKET → 2
 * </pre>
 *
 * <h3>WebSocket vs REST</h3>
 * <p>Spot WS is optional — it mirrors the REST API via a request/reply RPC
 * model over the same socket. This client uses REST for simplicity; if
 * lower-latency fills are needed, swap to the WS RPC path via
 * {@link com.mst.matt.tradingservice.bitunix.ws.BitUnixSpotWsClient}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BitUnixSpotOrderClient {

    private static final String PLACE_ORDER_PATH  = "/api/spot/v1/order/place_order";
    private static final String CANCEL_ORDER_PATH = "/api/spot/v1/order/cancel";
    private static final String ACCOUNT_PATH      = "/api/spot/v1/user/account";

    private final BitUnixHttpClient httpClient;

    @Value("${bitunix.spot-base-url:https://openapi.bitunix.com}")
    private String spotBaseUrl;

    // ── 6.4: Place order ────────────────────────────────────────────────────

    /**
     * Place a spot order.
     *
     * @param symbol     trading symbol (e.g., {@code "BTCUSDT"})
     * @param side       order direction (translated to numeric: SELL=1, BUY=2)
     * @param orderType  order execution type (translated to numeric: LIMIT=1, MARKET=2)
     * @param volume     quantity as string
     * @param price      limit price string; pass {@code null} for market orders
     * @param credential per-user API credential
     * @return spot order result with BitUnix orderId
     */
    public SpotPlaceOrderResult placeOrder(String symbol, OrderSide side, OrderType orderType,
                                            String volume, String price,
                                            BitUnixCredential credential) {
        int sideCode = toSpotSideCode(side);
        int typeCode = toSpotTypeCode(orderType);

        SpotPlaceOrderRequest request = SpotPlaceOrderRequest.builder()
                .symbol(symbol)
                .side(sideCode)
                .type(typeCode)
                .volume(volume)
                .price(orderType == OrderType.MARKET ? null : price)
                .build();

        log.info("Spot place_order symbol={} side={} type={} volume={}",
                symbol, sideCode, typeCode, volume);
        return httpClient.post(spotBaseUrl + PLACE_ORDER_PATH,
                request, credential, SpotPlaceOrderResult.class);
    }

    // ── 6.4: Cancel order(s) ────────────────────────────────────────────────

    /**
     * Cancel a single spot order by orderId and symbol.
     *
     * @param orderId    BitUnix-assigned order id
     * @param symbol     trading symbol
     * @param credential per-user API credential
     */
    @SuppressWarnings("unchecked")
    public BitUnixApiResponse<Void> cancelOrder(String orderId, String symbol,
                                                 BitUnixCredential credential) {
        log.info("Spot cancel_order orderId={} symbol={}", orderId, symbol);
        SpotCancelOrderRequest request = SpotCancelOrderRequest.builder()
                .orderIdList(List.of(SpotCancelOrderRequest.OrderRef.builder()
                        .orderId(orderId)
                        .symbol(symbol)
                        .build()))
                .build();
        return httpClient.post(spotBaseUrl + CANCEL_ORDER_PATH,
                request, credential, (Class<BitUnixApiResponse<Void>>)(Class<?>)BitUnixApiResponse.class);
    }

    /**
     * Cancel multiple spot orders in one API call (batch cancel).
     *
     * @param orderRefs  list of (orderId, symbol) pairs
     * @param credential per-user API credential
     */
    @SuppressWarnings("unchecked")
    public BitUnixApiResponse<Void> cancelOrders(
            List<SpotCancelOrderRequest.OrderRef> orderRefs,
            BitUnixCredential credential) {
        log.info("Spot batch cancel_orders count={}", orderRefs.size());
        SpotCancelOrderRequest request = SpotCancelOrderRequest.builder()
                .orderIdList(orderRefs)
                .build();
        return httpClient.post(spotBaseUrl + CANCEL_ORDER_PATH,
                request, credential, (Class<BitUnixApiResponse<Void>>)(Class<?>)BitUnixApiResponse.class);
    }

    // ── 6.5: Account (balance) ──────────────────────────────────────────────

    /**
     * Fetch all spot asset balances for the user (no query parameters required).
     *
     * @param credential per-user API credential
     * @return list of coin balances (total + locked per asset)
     */
    public SpotAccountResponse getBalances(BitUnixCredential credential) {
        log.debug("Spot get account balances");
        return httpClient.get(spotBaseUrl + ACCOUNT_PATH,
                Collections.emptyMap(), credential, SpotAccountResponse.class);
    }

    // ── Numeric enum translation ────────────────────────────────────────────

    /**
     * Translate the platform's {@link OrderSide} to BitUnix spot's numeric side code.
     * <ul><li>SELL → 1</li><li>BUY → 2</li></ul>
     */
    private int toSpotSideCode(OrderSide side) {
        return switch (side) {
            case SELL -> 1;
            case BUY  -> 2;
        };
    }

    /**
     * Translate the platform's {@link OrderType} to BitUnix spot's numeric type code.
     * <ul><li>LIMIT → 1</li><li>MARKET → 2</li></ul>
     *
     * @throws TradingException if the order type is not supported by BitUnix spot
     */
    private int toSpotTypeCode(OrderType orderType) {
        return switch (orderType) {
            case LIMIT  -> 1;
            case MARKET -> 2;
            default -> throw new TradingException(
                    "Order type " + orderType + " is not supported for BitUnix spot orders. "
                    + "Supported: LIMIT, MARKET");
        };
    }
}
