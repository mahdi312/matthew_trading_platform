package com.mst.matt.marketservice.bitunix;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Shared HTTP + JSON client beans for BitUnix integration (Step 5.1/5.2).
 *
 * <p>Two distinct {@link OkHttpClient} beans, mirroring the precedent set by
 * the legacy JavaFX desktop client's {@code PriceHttpConfig}
 * ({@code desktop/pom.xml} module):</p>
 * <ul>
 *   <li>{@code bitUnixRestHttpClient} — short timeouts, fails fast; used for
 *       the public Kline/Tickers REST calls (Step 5.2).</li>
 *   <li>{@code bitUnixWsHttpClient} — no read timeout (WebSocket connections
 *       are long-lived) and a built-in OkHttp ping interval as a belt-and-
 *       suspenders keepalive alongside the app-level ping/pong protocol
 *       messages BitUnix expects (Step 5.4).</li>
 * </ul>
 */
@Configuration
public class BitUnixHttpConfig {

    @Bean(name = "bitUnixRestHttpClient")
    public OkHttpClient bitUnixRestHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    @Bean(name = "bitUnixWsHttpClient")
    public OkHttpClient bitUnixWsHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)   // no read timeout — long-lived WS connection
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(25, TimeUnit.SECONDS) // OkHttp-level ping, in addition to BitUnix's own {"op":"ping"} frames
                .retryOnConnectionFailure(true)
                .build();
    }

    @Bean
    public Gson bitUnixGson() {
        return new Gson();
    }
}
