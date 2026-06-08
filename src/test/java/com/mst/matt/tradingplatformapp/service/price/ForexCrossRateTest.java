package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ForexCrossRateTest {

  @Test
  void currencyLayer_usdToEur() {
    JsonObject quotes = new JsonObject();
    quotes.addProperty("USDEUR", "0.93");
    BigDecimal cross = CurrencyLayerForexService.crossRateFromUsdQuotes(quotes, "USD", "EUR");
    assertNotNull(cross);
    assertEquals(0, new BigDecimal("0.93").compareTo(cross));
  }

  @Test
  void currencyLayer_eurToUsd() {
    JsonObject quotes = new JsonObject();
    quotes.addProperty("USDEUR", "0.93");
    BigDecimal cross = CurrencyLayerForexService.crossRateFromUsdQuotes(quotes, "EUR", "USD");
    assertNotNull(cross);
    assertTrue(cross.compareTo(BigDecimal.ONE) > 0);
  }

  @Test
  void openExchangeRates_usdToGbp() {
    JsonObject rates = new JsonObject();
    rates.addProperty("GBP", "0.78");
    BigDecimal cross = OpenExchangeRatesForexService.usdBaseCrossRate(rates, "USD", "GBP");
    assertEquals(0, new BigDecimal("0.78").compareTo(cross));
  }
}
