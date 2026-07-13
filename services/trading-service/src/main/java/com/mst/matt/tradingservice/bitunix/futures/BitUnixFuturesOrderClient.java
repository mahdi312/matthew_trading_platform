package com.mst.matt.tradingservice.bitunix.futures;

import com.google.gson.reflect.TypeToken;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixApiResponse;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixCredential;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixHttpClient;
import com.mst.matt.tradingservice.bitunix.futures.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP wrapper for BitUnix's <em>Futures</em> private trading endpoints
 * (Steps 6.1 – 6.3).
 *
 * <h3>Covered endpoints</h3>
 * <ul>
 *   <li>6.1 — {@code POST /api/v1/futures/trade/place_order}</li>
 *   <li>6.1 — {@code POST /api/v1/futures/trade/modify_order}</li>
 *   <li>6.2 — {@code POST /api/v1/futures/trade/cancel_orders}</li>
 *   <li>6.2 — {@code POST /api/v1/futures/trade/cancel_all_orders}</li>
 *   <li>6.3 — {@code GET  /api/v1/futures/account}</li>
 *   <li>6.3 — {@code GET  /api/v1/futures/position/get_pending_positions}</li>
 * </ul>
 *
 * <h3>Design rules</h3>
 * <ul>
 *   <li>No business logic here — only wire-format translation + HTTP delegation
 *       to {@link BitUnixHttpClient}.</li>
 *   <li>Cancellation confirmation must come from the WebSocket order channel,
 *       not from the HTTP 200 response (per BitUnix's own caveat).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BitUnixFuturesOrderClient {

    private static final String PLACE_ORDER_PATH   = "/api/v1/futures/trade/place_order";
    private static final String MODIFY_ORDER_PATH  = "/api/v1/futures/trade/modify_order";
    private static final String CANCEL_ORDERS_PATH = "/api/v1/futures/trade/cancel_orders";
    private static final String CANCEL_ALL_PATH    = "/api/v1/futures/trade/cancel_all_orders";
    private static final String ACCOUNT_PATH       = "/api/v1/futures/account";
    private static final String POSITIONS_PATH     = "/api/v1/futures/position/get_pending_positions";

    private final BitUnixHttpClient httpClient;

    @Value("${bitunix.futures-base-url:https://fapi.bitunix.com}")
    private String futuresBaseUrl;

    // ── 6.1: Place order ────────────────────────────────────────────────────

    /**
     * Place a new futures order.
     *
     * <p>Rate limit: 10 req/sec/uid. HTTP 200 indicates the order was <em>accepted</em>
     * by BitUnix — actual fill/cancellation state arrives via the WebSocket
     * order channel (see {@link com.mst.matt.tradingservice.bitunix.ws.BitUnixFuturesWsClient}).</p>
     *
     * @param request    fully-populated order request
     * @param credential per-user API credential
     * @return order result containing BitUnix orderId + echoed clientId
     */
    public FuturesOrderResult placeOrder(FuturesPlaceOrderRequest request,
                                         BitUnixCredential credential) {
        log.info("Futures place_order symbol={} side={} qty={} type={}",
                request.getSymbol(), request.getSide(), request.getQty(), request.getOrderType());
        return httpClient.post(futuresBaseUrl + PLACE_ORDER_PATH,
                request, credential, FuturesOrderResult.class);
    }

    // ── 6.1: Modify order ───────────────────────────────────────────────────

    /**
     * Modify an existing open futures order (price, quantity, or TP/SL).
     *
     * @param request    modification request
     * @param credential per-user API credential
     * @return updated order result
     */
    public FuturesOrderResult modifyOrder(FuturesModifyOrderRequest request,
                                           BitUnixCredential credential) {
        log.info("Futures modify_order orderId={} symbol={}", request.getOrderId(), request.getSymbol());
        return httpClient.post(futuresBaseUrl + MODIFY_ORDER_PATH,
                request, credential, FuturesOrderResult.class);
    }

    // ── 6.2: Cancel order(s) ────────────────────────────────────────────────

    /**
     * Cancel one or more futures orders in a single API call.
     *
     * <p>⚠️ Per BitUnix: HTTP 200 ≠ guaranteed cancellation.
     * Confirm final state via the WebSocket {@code order} channel.</p>
     *
     * @param request    cancellation request with symbol + list of orderId/clientId pairs
     * @param credential per-user API credential
     * @return split result: successList + failureList (with error codes for failures)
     */
    public FuturesCancelOrderResult cancelOrders(FuturesCancelOrderRequest request,
                                                  BitUnixCredential credential) {
        log.info("Futures cancel_orders symbol={} count={}",
                request.getSymbol(), request.getOrderList() != null ? request.getOrderList().size() : 0);
        return httpClient.post(futuresBaseUrl + CANCEL_ORDERS_PATH,
                request, credential, FuturesCancelOrderResult.class);
    }

    /**
     * Cancel <em>all</em> open futures orders, optionally for a specific symbol.
     *
     * @param symbol     trading symbol to cancel (may be null to cancel all symbols)
     * @param credential per-user API credential
     * @return generic API response (data field may be null on success)
     */
    @SuppressWarnings("unchecked")
    public BitUnixApiResponse<Void> cancelAllOrders(String symbol, BitUnixCredential credential) {
        log.info("Futures cancel_all_orders symbol={}", symbol);
        Map<String, String> body = new HashMap<>();
        if (symbol != null && !symbol.isBlank()) body.put("symbol", symbol);
        return httpClient.post(futuresBaseUrl + CANCEL_ALL_PATH,
                body, credential, (Class<BitUnixApiResponse<Void>>)(Class<?>)BitUnixApiResponse.class);
    }

    // ── 6.3: Account (balance) ──────────────────────────────────────────────

    /**
     * Fetch futures account balance for a given margin coin.
     *
     * @param marginCoin margin denomination (typically {@code "USDT"})
     * @param credential per-user API credential
     * @return account data list (one entry per margin coin)
     */
    public FuturesAccountResponse getAccount(String marginCoin, BitUnixCredential credential) {
        log.debug("Futures get account marginCoin={}", marginCoin);
        Map<String, String> params = new HashMap<>();
        if (marginCoin != null) params.put("marginCoin", marginCoin);
        String paramStr  = buildUrl(ACCOUNT_PATH, params);
        return httpClient.get(paramStr, params, credential, FuturesAccountResponse.class);
    }

    // ── 6.3: Pending positions ──────────────────────────────────────────────

    /**
     * Fetch all open (pending) futures positions, optionally filtered by symbol.
     *
     * @param symbol     optional symbol filter; null/blank fetches all symbols
     * @param credential per-user API credential
     * @return list of open position records
     */
    public FuturesPendingPositionsResponse getPendingPositions(String symbol,
                                                                BitUnixCredential credential) {
        log.debug("Futures get_pending_positions symbol={}", symbol);
        Map<String, String> params = new HashMap<>();
        if (symbol != null && !symbol.isBlank()) params.put("symbol", symbol);
        String url = buildUrl(POSITIONS_PATH, params);
        return httpClient.get(url, params, credential, FuturesPendingPositionsResponse.class);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String buildUrl(String path, Map<String, String> params) {
        if (params == null || params.isEmpty()) return futuresBaseUrl + path;
        StringBuilder sb = new StringBuilder(futuresBaseUrl).append(path).append("?");
        params.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
        return sb.substring(0, sb.length() - 1);
    }
}
