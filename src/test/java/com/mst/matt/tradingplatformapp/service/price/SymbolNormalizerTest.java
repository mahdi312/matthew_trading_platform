package com.mst.matt.tradingplatformapp.service.price;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SymbolNormalizerTest {

    @Test
    void forBinance_appendsUsdtToBareTicker() {
        assertEquals("BTCUSDT", SymbolNormalizer.forBinance("btc"));
        assertEquals("ETHUSDT", SymbolNormalizer.forBinance("ETH"));
    }

    @Test
    void forBinance_keepsExistingPairSuffix() {
        assertEquals("BTCUSDT", SymbolNormalizer.forBinance("BTCUSDT"));
        assertEquals("ETHBTC", SymbolNormalizer.forBinance("ETHBTC"));
    }

    @Test
    void forYahoo_addsForexSuffix() {
        assertEquals("EURUSD=X", SymbolNormalizer.forYahoo("EURUSD"));
        assertEquals("AAPL", SymbolNormalizer.forYahoo("AAPL"));
    }

    @Test
    void normalize_stripsSeparators() {
        assertEquals("EURUSD", SymbolNormalizer.normalize("EUR/USD"));
        assertEquals("EURUSD", SymbolNormalizer.normalize("eur-usd"));
    }
}
