package com.mst.matt.marketservice.controller;

import com.mst.matt.marketservice.bitunix.ws.BitUnixWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * Inbound STOMP endpoints (Step 5.4) letting frontend chart/ticker
 * components request that market-service subscribe/unsubscribe BitUnix WS
 * channels on their behalf, before they in turn subscribe (over the same
 * STOMP connection) to the corresponding {@code /topic/ohlcv/{symbol}/{interval}}
 * or {@code /topic/price/{symbol}} broadcast destination.
 *
 * <p>Multiple frontend clients may request the same symbol/interval — the
 * underlying {@link BitUnixWebSocketClient} is idempotent per channel key,
 * so duplicate subscribe requests are harmless no-ops.</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MarketStreamController {

    private final BitUnixWebSocketClient webSocketClient;

    /** Client → {@code /app/market/kline/subscribe}: start streaming OHLCV updates for symbol/interval. */
    @MessageMapping("/market/kline/subscribe")
    public void subscribeKline(MarketStreamSubscribeRequest request) {
        log.debug("STOMP subscribe request: kline {}/{}", request.getSymbol(), request.getInterval());
        webSocketClient.subscribeKline(request.getSymbol(), request.getInterval());
    }

    /** Client → {@code /app/market/kline/unsubscribe}: stop streaming OHLCV updates for symbol/interval. */
    @MessageMapping("/market/kline/unsubscribe")
    public void unsubscribeKline(MarketStreamSubscribeRequest request) {
        log.debug("STOMP unsubscribe request: kline {}/{}", request.getSymbol(), request.getInterval());
        webSocketClient.unsubscribeKline(request.getSymbol(), request.getInterval());
    }

    /** Client → {@code /app/market/ticker/subscribe}: start streaming live price ticks for symbol. */
    @MessageMapping("/market/ticker/subscribe")
    public void subscribeTicker(MarketStreamSubscribeRequest request) {
        log.debug("STOMP subscribe request: ticker {}", request.getSymbol());
        webSocketClient.subscribeTicker(request.getSymbol());
    }

    /** Client → {@code /app/market/ticker/unsubscribe}: stop streaming live price ticks for symbol. */
    @MessageMapping("/market/ticker/unsubscribe")
    public void unsubscribeTicker(MarketStreamSubscribeRequest request) {
        log.debug("STOMP unsubscribe request: ticker {}", request.getSymbol());
        webSocketClient.unsubscribeTicker(request.getSymbol());
    }
}
