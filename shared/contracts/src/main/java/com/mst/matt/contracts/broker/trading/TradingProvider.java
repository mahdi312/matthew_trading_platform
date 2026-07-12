package com.mst.matt.contracts.broker.trading;

import com.mst.matt.contracts.dto.BalanceDto;
import com.mst.matt.contracts.dto.OpenPositionDto;
import com.mst.matt.contracts.dto.PlaceOrderRequestDto;
import com.mst.matt.contracts.dto.TradeEventDto;
import com.mst.matt.contracts.enums.BrokerType;

import java.util.List;

/**
 * Unified contract for executing trades and querying account state on any broker.
 *
 * <h3>Design rules</h3>
 * <ul>
 *   <li>No controller or service may call a broker SDK directly; they must only
 *       call methods on a {@code TradingProvider} implementation.</li>
 *   <li>Every concrete implementation must register a
 *       {@link com.mst.matt.contracts.broker.registry.BrokerCapabilities} bean
 *       declaring what this provider supports (spot/futures? max leverage?).</li>
 *   <li>Implementations live in {@code trading-service}, not here.</li>
 *   <li>Spot-only brokers must throw {@link UnsupportedOperationException}
 *       from {@link #placeFuturesOrder} and {@link #getOpenPositions}.</li>
 * </ul>
 *
 * <h3>User credential resolution</h3>
 * <p>Implementations must retrieve per-user broker API keys from
 * {@code identity-service} (or a dedicated credential store) using the
 * {@code userId} provided in the request DTO.  Keys are never stored
 * in {@code trading-service}'s own database.</p>
 */
public interface TradingProvider {

    /**
     * Returns the {@link BrokerType} this implementation serves.
     * Used by the {@link com.mst.matt.contracts.broker.registry.BrokerRegistry}
     * to resolve the correct provider at runtime.
     */
    BrokerType brokerType();

    // ── Spot trading ──────────────────────────────────────────────────────────

    /**
     * Place a spot order on the broker.
     *
     * <p>The {@code orderType} in the request must be supported by this broker's
     * {@link com.mst.matt.contracts.broker.registry.BrokerCapabilities#supportedOrderTypes()};
     * throw {@link UnsupportedOperationException} if not.</p>
     *
     * @param request fully populated order request
     * @return a {@link TradeEventDto} representing the submitted order;
     *         status will be "PENDING" or "FILLED" depending on order type
     * @throws UnsupportedOperationException if spot trading is not supported
     */
    TradeEventDto placeSpotOrder(PlaceOrderRequestDto request);

    // ── Futures trading ───────────────────────────────────────────────────────

    /**
     * Place a futures (perpetual or dated) order on the broker.
     *
     * <p>Must throw {@link UnsupportedOperationException} if this broker does
     * not support futures trading
     * (i.e., {@link com.mst.matt.contracts.broker.registry.BrokerCapabilities#supportsFutures()}
     * returns {@code false}).</p>
     *
     * @param request order request; {@code leverage} and {@code reduceOnly}
     *                fields are used for futures-specific logic
     * @return a {@link TradeEventDto} representing the submitted futures order
     * @throws UnsupportedOperationException if futures trading is not supported
     */
    TradeEventDto placeFuturesOrder(PlaceOrderRequestDto request);

    // ── Order management ──────────────────────────────────────────────────────

    /**
     * Cancel an open order by its broker-assigned order id.
     *
     * @param userId  platform user id (to resolve credentials)
     * @param orderId broker-assigned order identifier
     * @return updated {@link TradeEventDto} with status "CANCELLED"
     */
    TradeEventDto cancelOrder(Long userId, String orderId);

    // ── Account queries ───────────────────────────────────────────────────────

    /**
     * Retrieve all open positions for a user (futures accounts).
     *
     * <p>Must throw {@link UnsupportedOperationException} for spot-only brokers.</p>
     *
     * @param userId platform user id
     * @return list of {@link OpenPositionDto}; empty list if no open positions
     * @throws UnsupportedOperationException if futures/margin is not supported
     */
    List<OpenPositionDto> getOpenPositions(Long userId);

    /**
     * Retrieve all asset balances for a user's account on this broker.
     *
     * @param userId platform user id
     * @return list of {@link BalanceDto} — one entry per non-zero asset;
     *         may include both spot and futures wallet balances if the broker
     *         exposes them separately
     */
    List<BalanceDto> getBalances(Long userId);
}
