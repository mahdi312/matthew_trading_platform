package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.service.price.LiveTickerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Runs after Spring context is fully up.
 *
 * <p><b>Live-stream deferred to login</b>: External API calls (Binance WebSocket,
 * Yahoo Finance, CoinGecko) must NOT start before a user logs in.  This service
 * intentionally does <em>not</em> call {@link LiveTickerService#startLiveStreams()}
 * at application-ready time.
 *
 * <p>Instead, {@link LiveTickerService#startLiveStreams()} is called by
 * {@code LoginController.onLoginSuccess()} after a successful authentication,
 * and stopped by {@code AuthService.logout()}.  The {@code LiveTickerService}
 * itself still creates its scheduler thread at construction time (harmless), but
 * the scheduler fires a no-op poll until {@code startLiveStreams()} is called.
 */
@Service
@ConditionalOnProperty(name = "app.live.startup-enabled", havingValue = "true", matchIfMissing = true)
public class AppStartupService {

    private static final Logger log = LoggerFactory.getLogger(AppStartupService.class);

    @Autowired private LiveTickerService liveTickerService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready — live market streams deferred until user login.");
        // Do NOT call liveTickerService.startLiveStreams() here.
        // Streams are started in LoginController.onLoginSuccess() after authentication.
    }
}
