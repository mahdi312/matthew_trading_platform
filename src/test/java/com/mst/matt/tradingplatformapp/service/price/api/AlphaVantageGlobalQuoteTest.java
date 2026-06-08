package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AlphaVantageGlobalQuoteTest {

  private final Gson gson = new Gson();

  @Test
  void fromRoot_parsesPercentChangeFromReportShape() throws Exception {
    Path fixture = Path.of("src/test/resources/fixtures/alphavantage-global-quote.json");
    JsonObject root = gson.fromJson(
        Files.readString(fixture, StandardCharsets.UTF_8),
        JsonObject.class);
    var quote = AlphaVantageGlobalQuote.fromRoot(root);
    assertTrue(quote.isPresent());
    var pq = quote.get().toPriceQuote("AAPL", AssetType.STOCK);
    assertEquals(0, new java.math.BigDecimal("1.0145").compareTo(pq.getChangePct24h()));
    assertTrue(pq.isUp());
  }
}
