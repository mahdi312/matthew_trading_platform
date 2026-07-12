package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.DataFetchMode;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.AppSettingsService;
import com.mst.matt.tradingplatformapp.service.price.api.binance.BinanceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PriceRouterTest {

    private static PriceQuote quote(String symbol, String price) {
        return PriceQuote.builder()
                .symbol(symbol)
                .price(new BigDecimal(price))
                .build();
    }

    private static PriceService provider(MarketDataProvider id, Optional<PriceQuote> quote, List<OhlcvBar> bars) {
        PriceService service = mock(PriceService.class);
        when(service.getProviderId()).thenReturn(id);
        when(service.getProviderName()).thenReturn(id.getLabel());
        when(service.getQuote(org.mockito.ArgumentMatchers.anyString())).thenReturn(quote);
        when(service.getOhlcv(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(bars);
        return service;
    }

    @Test
    void getQuoteSkipsFailuresAndInvalidPricesThenCachesSuccessfulProvider() {
        PriceProviderRegistry registry = mock(PriceProviderRegistry.class);
        PriceCacheService cache = new PriceCacheService();
        PriceService throwing = provider(MarketDataProvider.YAHOO, Optional.empty(), List.of());
        when(throwing.getQuote("BTCUSDT")).thenThrow(new RuntimeException("down"));
        PriceService invalid = provider(MarketDataProvider.COINGECKO,
                Optional.of(quote("BTCUSDT", "0")), List.of());
        PriceService valid = provider(MarketDataProvider.BINANCE,
                Optional.of(quote("BTCUSDT", "65000.12")), List.of());
        when(registry.chainFor("BTCUSDT", null)).thenReturn(List.of(throwing, invalid, valid));
        PriceRouter router = new PriceRouter(registry, mock(BinanceService.class), cache);

        Optional<PriceQuote> result = router.getQuote("btc/usdt");

        assertThat(result).isPresent();
        assertThat(result.get().getPrice()).isEqualByComparingTo("65000.12");
        assertThat(cache.getLastKnown("BTCUSDT")).isPresent();
        assertThat(PriceRouter.getLastProviderName()).isEqualTo("Binance");
    }

    @Test
    void getQuoteFallsBackToStaleCacheWhenEveryProviderFails() {
        PriceProviderRegistry registry = mock(PriceProviderRegistry.class);
        PriceCacheService cache = new PriceCacheService();
        cache.update("AAPL", quote("AAPL", "210.50"));
        PriceService failing = provider(MarketDataProvider.YAHOO, Optional.empty(), List.of());
        when(failing.getQuote("AAPL")).thenThrow(new RuntimeException("timeout"));
        when(registry.chainFor("AAPL", null)).thenReturn(List.of(failing));
        PriceRouter router = new PriceRouter(registry, mock(BinanceService.class), cache);

        Optional<PriceQuote> result = router.getQuote("aapl");

        assertThat(result).isPresent();
        assertThat(result.get().getPrice()).isEqualByComparingTo("210.50");
        assertThat(PriceRouter.getLastProviderName()).isEqualTo("cache (stale)");
    }

    @Test
    void getOhlcvReturnsEmptyInOfflineOnlyModeWithoutCallingProviders() {
        PriceProviderRegistry registry = mock(PriceProviderRegistry.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        when(settings.getDataFetchMode()).thenReturn(DataFetchMode.OFFLINE_ONLY);
        PriceRouter router = new PriceRouter(registry, mock(BinanceService.class), new PriceCacheService());
        ReflectionTestUtils.setField(router, "appSettings", settings);

        List<OhlcvBar> bars = router.getOhlcv("AAPL", "1d", 50);

        assertThat(bars).isEmpty();
        verifyNoInteractions(registry);
    }

    @Test
    void getOhlcvFallsBackToNextProviderAfterExceptionOrEmptyResult() {
        PriceProviderRegistry registry = mock(PriceProviderRegistry.class);
        OhlcvBar bar = new OhlcvBar();
        PriceService throwing = provider(MarketDataProvider.YAHOO, Optional.empty(), List.of());
        when(throwing.getOhlcv("AAPL", "1d", 10)).thenThrow(new RuntimeException("down"));
        PriceService empty = provider(MarketDataProvider.FINNHUB, Optional.empty(), List.of());
        PriceService valid = provider(MarketDataProvider.ALPHA_VANTAGE, Optional.empty(), List.of(bar));
        when(registry.chainFor("AAPL", null)).thenReturn(List.of(throwing, empty, valid));
        PriceRouter router = new PriceRouter(registry, mock(BinanceService.class), new PriceCacheService());

        List<OhlcvBar> bars = router.getOhlcv("aapl", "1d", 10);

        assertThat(bars).containsExactly(bar);
        assertThat(PriceRouter.getLastProviderName()).isEqualTo("Alpha Vantage");
    }

    @Test
    void liveTickerMethodsDelegateToBinanceService() {
        BinanceService binance = mock(BinanceService.class);
        PriceRouter router = new PriceRouter(mock(PriceProviderRegistry.class), binance, new PriceCacheService());
        java.util.function.Consumer<PriceQuote> listener = quote -> {
        };

        router.subscribeToLiveTicker(List.of("BTCUSDT", "ETHUSDT"));
        router.addLiveListener(listener);

        verify(binance).subscribeToMultiTicker(List.of("BTCUSDT", "ETHUSDT"));
        verify(binance).addLiveListener(listener);
    }
}
