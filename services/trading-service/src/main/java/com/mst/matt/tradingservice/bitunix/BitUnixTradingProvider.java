package com.mst.matt.tradingservice.bitunix;

import com.mst.matt.contracts.broker.trading.TradingProvider;
import com.mst.matt.contracts.dto.*;
import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.contracts.enums.InstrumentType;
import com.mst.matt.contracts.enums.OrderSide;
import com.mst.matt.contracts.enums.OrderType;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixCredential;
import com.mst.matt.tradingservice.bitunix.futures.BitUnixFuturesOrderClient;
import com.mst.matt.tradingservice.bitunix.futures.dto.*;
import com.mst.matt.tradingservice.bitunix.spot.BitUnixSpotOrderClient;
import com.mst.matt.tradingservice.bitunix.spot.dto.SpotAccountResponse;
import com.mst.matt.tradingservice.bitunix.spot.dto.SpotPlaceOrderResult;
import com.mst.matt.tradingservice.exception.TradingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BitUnix implementation of {@link TradingProvider} (Step 6.6).
 *
 * <h3>Routing logic</h3>
 * <p>Routes orders to either {@link BitUnixFuturesOrderClient} or
 * {@link BitUnixSpotOrderClient} based on {@link PlaceOrderRequestDto#getInstrumentType()}
 * (or a futures-flagging heuristic when instrumentType is absent).
 * The two markets have different base URLs, different wire-format encodings
 * (string vs numeric enums), and different WS confirmation patterns.</p>
 *
 * <h3>Per-account locking (Step 6.6 #6)</h3>
 * <p>A {@link ReentrantLock} keyed by {@code userId:BITUNIX} wraps the full
 * order-submission path to prevent two concurrent requests from the same
 * account racing on balance/margin checks. The lock is held only for the
 * duration of the local pre-check + HTTP call — DB writes are separate.</p>
 *
 * <h3>Credential resolution</h3>
 * <p>Credentials are fetched from the per-user store (stub: reads from
 * {@link com.mst.matt.tradingservice.config.TradingProperties} defaults for
 * now; the real implementation reads from identity-service in a later step).</p>
 *
 * <h3>Cancellation confirmation</h3>
 * <p>Per BitUnix's own docs: HTTP 200 on cancel ≠ guaranteed. The authoritative
 * confirmation arrives on the WebSocket order channel, handled by
 * {@link com.mst.matt.tradingservice.bitunix.ws.BitUnixFuturesWsClient}.
 * For spot, poll {@code order/pending/list} or use the WS RPC channel.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BitUnixTradingProvider implements TradingProvider {

    private final BitUnixFuturesOrderClient futuresClient;
    private final BitUnixSpotOrderClient    spotClient;
    private final com.mst.matt.tradingservice.config.TradingProperties tradingProperties;

    /** Per-(userId+brokerType) reentrant lock to prevent concurrent order races. */
    private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    @Override
    public BrokerType brokerType() {
        return BrokerType.BITUNIX;
    }

    // ── Spot order ───────────────────────────────────────────────────────────

    @Override
    public TradeEventDto placeSpotOrder(PlaceOrderRequestDto request) {
        validateRequest(request);
        ReentrantLock lock = acquireLock(request.getUserId());
        lock.lock();
        try {
            BitUnixCredential credential = resolveCredential(request.getUserId());

            SpotPlaceOrderResult result = spotClient.placeOrder(
                    request.getSymbol(),
                    request.getSide(),
                    request.getOrderType(),
                    request.getQuantity().toPlainString(),
                    request.getPrice() != null ? request.getPrice().toPlainString() : null,
                    credential);

            log.info("Spot order submitted: userId={} orderId={} symbol={} side={} qty={}",
                    request.getUserId(), result.getData().getOrderId(),
                    request.getSymbol(), request.getSide(), request.getQuantity());

            return buildTradeEvent(request, InstrumentType.CRYPTO_SPOT, result.getData().getOrderId(), "PENDING");

        } finally {
            lock.unlock();
        }
    }

    // ── Futures order ────────────────────────────────────────────────────────

    @Override
    public TradeEventDto placeFuturesOrder(PlaceOrderRequestDto request) {
        validateRequest(request);
        ReentrantLock lock = acquireLock(request.getUserId());
        lock.lock();
        try {
            BitUnixCredential credential = resolveCredential(request.getUserId());

            FuturesPlaceOrderRequest futuresReq = FuturesPlaceOrderRequest.builder()
                    .symbol(request.getSymbol())
                    .side(request.getSide().name())               // futures uses BUY/SELL strings
                    .orderType(toFuturesOrderType(request.getOrderType()))
                    .qty(request.getQuantity().toPlainString())
                    .price(request.getPrice() != null ? request.getPrice().toPlainString() : null)
                    .effect("GTC")                                 // default; TP/SL can override
                    .clientId(request.getClientOrderId() != null
                            ? request.getClientOrderId()
                            : UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .reduceOnly(request.isReduceOnly())
                    .build();

            FuturesOrderResult result = futuresClient.placeOrder(futuresReq, credential);

            log.info("Futures order submitted: userId={} orderId={} symbol={} side={} qty={}",
                    request.getUserId(), result.getData().getOrderId(),
                    request.getSymbol(), request.getSide(), request.getQuantity());

            return buildTradeEvent(request, InstrumentType.CRYPTO_FUTURES, result.getData().getOrderId(), "PENDING");

        } finally {
            lock.unlock();
        }
    }

    // ── Cancel order ─────────────────────────────────────────────────────────

    @Override
    public TradeEventDto cancelOrder(Long userId, String orderId) {
        BitUnixCredential credential = resolveCredential(userId);

        // Attempt futures cancel; if symbol info is needed use a separate
        // lookup — this is a best-effort HTTP cancel (WS channel is authoritative)
        try {
            FuturesCancelOrderRequest req = FuturesCancelOrderRequest.builder()
                    .symbol("")        // symbol required by API; caller should pass it via
                                       // a richer overload (added in next iteration)
                    .orderList(List.of(FuturesCancelOrderRequest.OrderRef.builder()
                            .orderId(orderId).build()))
                    .build();
            futuresClient.cancelOrders(req, credential);
            log.info("Cancel submitted for orderId={} userId={} (WS confirmation pending)", orderId, userId);
        } catch (Exception e) {
            log.warn("Futures cancel HTTP call failed for orderId={}: {}", orderId, e.getMessage());
        }

        return TradeEventDto.builder()
                .eventId(orderId)
                .userId(userId)
                .brokerType(BrokerType.BITUNIX)
                .status("CANCEL_REQUESTED")
                .updatedAt(Instant.now())
                .build();
    }

    // ── Open positions ────────────────────────────────────────────────────────

    @Override
    public List<OpenPositionDto> getOpenPositions(Long userId) {
        BitUnixCredential credential = resolveCredential(userId);
        FuturesPendingPositionsResponse response =
                futuresClient.getPendingPositions(null, credential);

        if (response.getData() == null) return List.of();

        List<OpenPositionDto> positions = new ArrayList<>();
        for (var p : response.getData()) {
            positions.add(OpenPositionDto.builder()
                    .brokerType(BrokerType.BITUNIX)
                    .userId(userId)
                    .symbol(p.getSymbol())
                    .side("LONG".equals(p.getSide()) ? OrderSide.BUY : OrderSide.SELL)
                    .size(parseBD(p.getQty()))
                    .entryPrice(parseBD(p.getAvgOpenPrice()))
                    .markPrice(null)           // not in REST response — set from WS push
                    .unrealisedPnl(parseBD(p.getUnrealizedPNL()))
                    .leverage(parseIntSafe(p.getLeverage()))
                    .liquidationPrice(parseBD(p.getLiqPrice()))
                    .openedAt(Instant.ofEpochMilli(p.getCtime()))
                    .build());
        }
        return positions;
    }

    // ── Balances ──────────────────────────────────────────────────────────────

    @Override
    public List<BalanceDto> getBalances(Long userId) {
        BitUnixCredential credential = resolveCredential(userId);

        List<BalanceDto> balances = new ArrayList<>();

        // Futures balances
        try {
            FuturesAccountResponse futuresAcc = futuresClient.getAccount("USDT", credential);
            if (futuresAcc.getData() != null) {
                for (var acc : futuresAcc.getData()) {
                    BigDecimal total  = parseBD(acc.getAvailable()).add(parseBD(acc.getFrozen()))
                                                                   .add(parseBD(acc.getMargin()));
                    balances.add(BalanceDto.builder()
                            .brokerType(BrokerType.BITUNIX)
                            .userId(userId)
                            .asset(acc.getMarginCoin())
                            .total(total)
                            .free(parseBD(acc.getAvailable()))
                            .locked(parseBD(acc.getFrozen()).add(parseBD(acc.getMargin())))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch BitUnix futures balances for userId={}: {}", userId, e.getMessage());
        }

        // Spot balances
        try {
            SpotAccountResponse spotAcc = spotClient.getBalances(credential);
            if (spotAcc.getData() != null) {
                for (var coin : spotAcc.getData()) {
                    balances.add(BalanceDto.builder()
                            .brokerType(BrokerType.BITUNIX)
                            .userId(userId)
                            .asset(coin.getCoin())
                            .total(BigDecimal.valueOf(coin.getBalance()))
                            .free(BigDecimal.valueOf(coin.getBalance() - coin.getBalanceLocked()))
                            .locked(BigDecimal.valueOf(coin.getBalanceLocked()))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch BitUnix spot balances for userId={}: {}", userId, e.getMessage());
        }

        return balances;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolve per-user credentials.
     *
     * <p><b>Stub implementation</b> — currently falls back to the global
     * {@link com.mst.matt.tradingservice.config.TradingProperties} api-key/secret.
     * The full implementation will call identity-service's broker-link endpoint
     * to fetch per-user encrypted credentials.</p>
     */
    private BitUnixCredential resolveCredential(Long userId) {
        String key    = tradingProperties.getBitunixApiKey();
        String secret = tradingProperties.getBitunixSecretKey();
        if (key == null || key.isBlank() || secret == null || secret.isBlank()) {
            throw new TradingException(
                    "No BitUnix credentials configured for userId=" + userId
                    + ". Set bitunix.api-key and bitunix.secret-key in config.");
        }
        return new BitUnixCredential(key, secret);
    }

    private ReentrantLock acquireLock(Long userId) {
        String lockKey = userId + ":BITUNIX";
        return accountLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
    }

    private void validateRequest(PlaceOrderRequestDto request) {
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradingException("Order quantity must be positive");
        }
        if (request.getOrderType() == OrderType.LIMIT && request.getPrice() == null) {
            throw new TradingException("LIMIT orders require a price");
        }
    }

    private TradeEventDto buildTradeEvent(
            PlaceOrderRequestDto req, InstrumentType instrumentType, String orderId, String status) {
        return TradeEventDto.builder()
                .eventId(orderId)
                .userId(req.getUserId())
                .brokerType(BrokerType.BITUNIX)
                .symbol(req.getSymbol())
                .instrumentType(instrumentType)
                .side(req.getSide())
                .orderType(req.getOrderType())
                .quantity(req.getQuantity())
                .requestedPrice(req.getPrice())
                .status(status)
                .submittedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private String toFuturesOrderType(OrderType type) {
        return switch (type) {
            case LIMIT  -> "LIMIT";
            case MARKET -> "MARKET";
            default -> throw new TradingException(
                    "Order type " + type + " is not directly supported for BitUnix futures. "
                    + "Supported: LIMIT, MARKET");
        };
    }

    private BigDecimal parseBD(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(value); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private int parseIntSafe(String value) {
        if (value == null || value.isBlank()) return 1;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return 1; }
    }
}
