package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 (LOG-FIX): smoke test for the last-known-good cache.
 */
class PriceCacheServiceTest {

    @Test
    void update_andGetLastKnown_roundTrip() {
        PriceCacheService cache = new PriceCacheService();
        PriceQuote quote = PriceQuote.builder()
                .symbol("BTCUSDT")
                .assetType(AssetType.CRYPTO)
                .price(new BigDecimal("95000.00"))
                .timestamp(LocalDateTime.now())
                .build();

        cache.update("BTCUSDT", quote);

        Optional<PriceQuote> hit = cache.getLastKnown("BTCUSDT");
        assertTrue(hit.isPresent());
        assertEquals(0, hit.get().getPrice().compareTo(new BigDecimal("95000.00")));
    }

    @Test
    void getLastKnown_normalisesSymbolFormat() {
        PriceCacheService cache = new PriceCacheService();
        cache.update("BTCUSDT", PriceQuote.builder().symbol("BTCUSDT")
                .price(new BigDecimal("1")).build());
        // Slash/dash forms collapse to the same normalised key.
        assertTrue(cache.getLastKnown("btc/usdt").isPresent());
        assertTrue(cache.getLastKnown("BTC-USDT").isPresent());
    }

    @Test
    void getLastKnown_missingSymbolReturnsEmpty() {
        PriceCacheService cache = new PriceCacheService();
        assertTrue(cache.getLastKnown("ETHUSDT").isEmpty());
    }

    @Test
    void update_ignoresNullInputs() {
        PriceCacheService cache = new PriceCacheService();
        cache.update(null, PriceQuote.builder().build());
        cache.update("ANY", null);
        assertTrue(cache.getLastKnown("ANY").isEmpty());
    }
}
