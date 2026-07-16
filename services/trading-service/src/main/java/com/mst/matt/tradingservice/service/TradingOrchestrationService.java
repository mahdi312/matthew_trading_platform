package com.mst.matt.tradingservice.service;

import com.mst.matt.contracts.broker.registry.BrokerRegistry;
import com.mst.matt.contracts.broker.trading.TradingProvider;
import com.mst.matt.contracts.dto.PlaceOrderRequestDto;
import com.mst.matt.contracts.dto.TradeEventDto;
import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.contracts.enums.OrderSide;
import com.mst.matt.contracts.enums.OrderType;
import com.mst.matt.tradingservice.dto.TradeRequest;
import com.mst.matt.tradingservice.model.Trade;
import com.mst.matt.tradingservice.model.Trade.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestration service that wires the journal layer ({@link TradeService}) to
 * the live broker layer ({@link TradingProvider} via {@link BrokerRegistry}).
 *
 * <h3>Trade sources</h3>
 * <dl>
 *   <dt>{@link TradeSource#MANUAL}</dt>
 *   <dd>Only touches the database. No broker API is called. P&L is computed
 *       from the supplied entry/exit prices. This is the trade-journal path.</dd>
 *
 *   <dt>{@link TradeSource#BROKER_LIVE}</dt>
 *   <dd>Routes the order to the correct {@link TradingProvider} implementation
 *       via the {@link BrokerRegistry} (broker type resolved from
 *       {@link TradeRequest#getBrokerType()}). The broker's order ID is then
 *       stored in {@link Trade#getBrokerOrderId()} for reconciliation when the
 *       WS confirmation arrives.  A pending journal entry is persisted immediately
 *       so the UI can show the submitted order before broker confirmation.</dd>
 *
 *   <dt>{@link TradeSource#BROKER_IMPORT}</dt>
 *   <dd>Handled by {@link BrokerImportService} — not this class.</dd>
 * </dl>
 *
 * <h3>Routing rule</h3>
 * <p>The {@link BrokerRegistry} is used (not the {@link com.mst.matt.tradingservice.bitunix.BitUnixTradingProvider}
 * directly) so that future brokers can be added by registering new
 * {@link TradingProvider} beans without changing this class.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingOrchestrationService {

    private final TradeService    tradeService;
    private final BrokerRegistry  brokerRegistry;

    /**
     * Place a trade — either journal-only (MANUAL) or via a live broker (BROKER_LIVE).
     *
     * <p><b>Not {@code @Transactional}.</b> {@link #placeLiveBrokerTrade} calls a live
     * exchange API before writing to the database; holding a DB transaction open across
     * that network call risks connection-pool exhaustion and — worse — an order placed on
     * the exchange with no local record if the subsequent save fails. The DB write itself
     * is still transactional via {@link TradeService}'s class-level {@code @Transactional}.
     *
     * @param request populated trade request (source field controls routing)
     * @return the persisted {@link Trade} record
     */
    public Trade placeTrade(TradeRequest request, String idempotencyKey) {
        return switch (request.getSource()) {
            case MANUAL        -> placeManualTrade(request, idempotencyKey);
            case BROKER_LIVE   -> placeLiveBrokerTrade(request, idempotencyKey);
            case BROKER_IMPORT -> throw new IllegalArgumentException(
                    "BROKER_IMPORT trades must go through BrokerImportService, not placeTrade()");
        };
    }

    private Trade placeManualTrade(TradeRequest request, String idempotencyKey) {
        Trade trade = buildTradeEntity(request);
        trade.setSource(TradeSource.MANUAL);
        trade.setIdempotencyKey(idempotencyKey);
        Trade saved = tradeService.saveTrade(trade);
        log.info("Manual trade journalled: id={} userId={} symbol={}", saved.getId(), saved.getUserId(), saved.getSymbol());
        return saved;
    }

    // ── Live broker path ──────────────────────────────────────────────────────

    /**
     * Places an order on the live broker via {@link TradingProvider} and persists
     * a pending trade journal entry immediately (before WS confirmation).
     *
     * <p>The broker is resolved from {@link TradeRequest#getBrokerType()} via the
     * {@link BrokerRegistry}.  If the broker type is null or unrecognised, an
     * {@link IllegalArgumentException} is thrown before any order is submitted.</p>
     *
     * <p>Instrument routing: if the symbol ends in a crypto pair suffix
     * (USDT/BTC/ETH/etc.) and {@code assetType == CRYPTO} we default to FUTURES
     * for BitUnix (since BitUnix's main market is perpetual futures). Callers can
     * override by setting {@code request.notes} to {@code "SPOT"} for now; a
     * dedicated field will be added in the next iteration.</p>
     */
    private Trade placeLiveBrokerTrade(TradeRequest request, String idempotencyKey) {
        // 1. Resolve the broker type
        BrokerType brokerType = resolveBrokerType(request.getBrokerType());

        // 2. Look up the TradingProvider via BrokerRegistry (not directly)
        TradingProvider provider = brokerRegistry.requireTradingProvider(brokerType);

        // 3. Build the PlaceOrderRequestDto
        PlaceOrderRequestDto orderRequest = PlaceOrderRequestDto.builder()
                .userId(request.getUserId())
                .symbol(request.getSymbol())
                .side(request.getDirection() == TradeDirection.LONG ? OrderSide.BUY : OrderSide.SELL)
                .orderType(request.getEntryPrice() != null ? OrderType.LIMIT : OrderType.MARKET)
                .quantity(request.getQuantity())
                .price(request.getEntryPrice())
                .stopPrice(request.getStopLoss())
                .build();

        boolean isFutures = request.getAssetType() == AssetType.CRYPTO
                && (request.getNotes() == null || !request.getNotes().equalsIgnoreCase("SPOT"));

        log.info("Placing live {} order: userId={} symbol={} side={} qty={} broker={}",
                isFutures ? "FUTURES" : "SPOT",
                request.getUserId(), request.getSymbol(),
                orderRequest.getSide(), request.getQuantity(), brokerType);

        // ── Network call to the live exchange — deliberately OUTSIDE any DB transaction ──
        TradeEventDto event = isFutures
                ? provider.placeFuturesOrder(orderRequest)
                : provider.placeSpotOrder(orderRequest);

        // 4. Persist a pending trade journal entry immediately.
        //    If this throws, the order is already live on the exchange with no local
        //    record — that mismatch is exactly what Step 13's reconciliation job exists
        //    to catch, but log it loudly here too so it's visible immediately, not just
        //    on the next scheduled reconciliation pass.
        try {
            Trade trade = buildTradeEntity(request);
            trade.setSource(TradeSource.BROKER_LIVE);
            trade.setBrokerOrderId(event.getEventId());
            trade.setIdempotencyKey(idempotencyKey);
            trade.setStatus(TradeStatus.OPEN);
            Trade saved = tradeService.saveTrade(trade);

            log.info("Live trade journalled (pending broker confirmation): id={} brokerOrderId={}",
                    saved.getId(), saved.getBrokerOrderId());
            return saved;
        } catch (Exception persistFailure) {
            log.error("ORDER PLACED ON {} BUT NOT JOURNALLED — manual reconciliation required. " +
                            "userId={} symbol={} brokerOrderId={} side={} qty={} error={}",
                    brokerType, request.getUserId(), request.getSymbol(),
                    event.getEventId(), orderRequest.getSide(), request.getQuantity(),
                    persistFailure.getMessage(), persistFailure);
            throw persistFailure;
        }
    }


    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Maps a {@link TradeRequest} to a {@link Trade} entity, setting all
     * scalar fields but NOT source/brokerOrderId (set by the calling path).
     */
    private Trade buildTradeEntity(TradeRequest request) {
        Trade trade = new Trade();
        trade.setUserId(request.getUserId());
        trade.setSymbol(request.getSymbol());
        trade.setAssetName(request.getAssetName());
        trade.setAssetType(request.getAssetType());
        trade.setDirection(request.getDirection());
        trade.setStatus(request.getExitPrice() != null ? TradeStatus.CLOSED : TradeStatus.OPEN);
        trade.setEntryPrice(request.getEntryPrice());
        trade.setExitPrice(request.getExitPrice());
        trade.setQuantity(request.getQuantity());
        trade.setStopLoss(request.getStopLoss());
        trade.setTakeProfit(request.getTakeProfit());
        trade.setFee(request.getFee());
        trade.setEntryTime(request.getEntryTime());
        trade.setExitTime(request.getExitTime());
        trade.setNotes(request.getNotes());
        trade.setExchange(request.getExchange());
        trade.setStrategy(request.getStrategy());
        trade.setScreenshotPath(request.getScreenshotPath());
        return trade;
    }

    private BrokerType resolveBrokerType(String brokerTypeStr) {
        if (brokerTypeStr == null || brokerTypeStr.isBlank()) {
            throw new IllegalArgumentException(
                    "brokerType must be set for BROKER_LIVE trades (e.g. 'BITUNIX')");
        }
        try {
            return BrokerType.valueOf(brokerTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown broker type: '" + brokerTypeStr + "'. Registered brokers: "
                    + brokerRegistry.getRegisteredBrokers());
        }
    }
}
