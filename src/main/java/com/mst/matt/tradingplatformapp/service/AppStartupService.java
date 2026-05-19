package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.service.price.LiveTickerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Runs after Spring context is fully up.
 * Starts WebSocket streams and other background services.
 */
@Service
public class AppStartupService {

    private static final Logger log = LoggerFactory.getLogger(AppStartupService.class);

    @Autowired private LiveTickerService liveTickerService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready — starting live market streams...");
        liveTickerService.startLiveStreams();
        log.info("Live streams started.");
    }
}