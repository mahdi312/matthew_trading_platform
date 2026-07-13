package com.mst.matt.tradingservice.config;

import com.google.gson.Gson;
import com.mst.matt.contracts.broker.market.MarketDataProvider;
import com.mst.matt.contracts.broker.registry.BrokerCapabilities;
import com.mst.matt.contracts.broker.registry.BrokerRegistry;
import com.mst.matt.contracts.broker.registry.DefaultBrokerRegistry;
import com.mst.matt.contracts.broker.trading.TradingProvider;
import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.contracts.enums.InstrumentType;
import com.mst.matt.contracts.enums.OrderType;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spring configuration for trading-service infrastructure beans (Step 6.6).
 *
 * <h3>OkHttp clients</h3>
 * <p>Two {@link OkHttpClient} beans — one for REST (short timeouts, fail-fast)
 * and one for WebSocket (no read timeout, OkHttp-level pings) — mirror the
 * pattern established in market-service's {@code BitUnixHttpConfig}.  The
 * qualifier names use the prefix {@code trading} to avoid conflicts if both
 * services were ever co-located in the same context:</p>
 * <ul>
 *   <li>{@code tradingBitUnixHttpClient} — REST short-timeout client</li>
 *   <li>{@code tradingBitUnixWsHttpClient} — long-lived WS client</li>
 * </ul>
 *
 * <h3>BrokerRegistry</h3>
 * <p>{@link DefaultBrokerRegistry} is intentionally <em>not</em> annotated
 * with {@code @Component} in {@code shared/contracts}; each service that
 * needs broker routing declares it as a {@code @Bean} here.  trading-service
 * has no {@link MarketDataProvider} beans — Spring will inject an empty list,
 * which is the correct behaviour.</p>
 *
 * <h3>BrokerCapabilities</h3>
 * <p>Declares BitUnix's full trading capabilities: spot + futures, LIMIT +
 * MARKET order types, and a configurable max leverage read from
 * {@link TradingProperties}.</p>
 */
@Configuration
public class TradingConfig {

    // ── OkHttp REST client ────────────────────────────────────────────────────

    /**
     * Short-timeout REST client used by {@link com.mst.matt.tradingservice.bitunix.auth.BitUnixHttpClient}
     * for all signed REST calls (order placement, account queries, etc.).
     *
     * <p>Retry-on-failure is intentionally disabled: for order placement,
     * a retry after a network error could result in a duplicate order.
     * The caller is responsible for idempotency (e.g., using {@code clientId}).</p>
     */
    @Bean(name = "tradingBitUnixHttpClient")
    public OkHttpClient tradingBitUnixHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)   // never auto-retry order submissions
                .build();
    }

    // ── OkHttp WebSocket client ───────────────────────────────────────────────

    /**
     * Long-lived WebSocket client used by the futures and spot WS clients.
     *
     * <p>No read timeout (WS connections are persistent) and an OkHttp-level
     * 25 s ping interval as belt-and-suspenders alongside the app-level
     * {@code {"op":"ping"}} frames that BitUnix expects every 20-30 s.
     * Retry-on-failure is enabled here because WS reconnection is desirable.</p>
     */
    @Bean(name = "tradingBitUnixWsHttpClient")
    public OkHttpClient tradingBitUnixWsHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)    // no read timeout — long-lived connection
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(25, TimeUnit.SECONDS)  // OkHttp-level ping; supplements app-level ping
                .retryOnConnectionFailure(true)
                .build();
    }

    // ── Gson ──────────────────────────────────────────────────────────────────

    /**
     * Single shared {@link Gson} instance for serialising/deserialising BitUnix
     * JSON payloads.  Registered under the qualifier {@code tradingGson} to
     * avoid conflicts with any other Gson bean in the application context.
     */
    @Bean(name = "tradingGson")
    public Gson tradingGson() {
        return new Gson();
    }

    // ── BrokerCapabilities ────────────────────────────────────────────────────

    /**
     * Declares BitUnix's trading capabilities as seen by trading-service.
     *
     * <ul>
     *   <li>{@code supportsSpot = true} — {@link com.mst.matt.tradingservice.bitunix.spot.BitUnixSpotOrderClient}</li>
     *   <li>{@code supportsFutures = true} — {@link com.mst.matt.tradingservice.bitunix.futures.BitUnixFuturesOrderClient}</li>
     *   <li>{@code supportedOrderTypes} — LIMIT and MARKET (both markets)</li>
     *   <li>{@code supportsLiveStream = false} — market-data streaming lives in market-service</li>
     *   <li>{@code maxLeverage} — from {@link TradingProperties#getMaxLeverage()}</li>
     * </ul>
     */
    @Bean
    public BrokerCapabilities bitUnixTradingCapabilities(TradingProperties props) {
        return BrokerCapabilities.builder()
                .brokerType(BrokerType.BITUNIX)
                .displayName("BitUnix")
                .supportsLiveStream(false)   // market-data is market-service's domain
                .supportsSpot(true)
                .supportsFutures(true)
                .supportedInstrument(InstrumentType.CRYPTO_SPOT)
                .supportedInstrument(InstrumentType.CRYPTO_FUTURES)
                .supportedOrderType(OrderType.LIMIT)
                .supportedOrderType(OrderType.MARKET)
                .maxLeverage(props.getMaxLeverage())
                .build();
    }

    // ── BrokerRegistry ────────────────────────────────────────────────────────

    /**
     * Wires all {@link MarketDataProvider} and {@link TradingProvider} beans
     * known to this service's application context into a {@link BrokerRegistry}.
     *
     * <p>trading-service has no {@link MarketDataProvider} beans of its own;
     * Spring will inject an empty list, which is fine — the registry handles it.</p>
     */
    @Bean
    public BrokerRegistry brokerRegistry(
            List<MarketDataProvider> marketProviders,
            List<TradingProvider> tradingProviders,
            List<BrokerCapabilities> capabilities) {
        return new DefaultBrokerRegistry(marketProviders, tradingProviders, capabilities);
    }
}
