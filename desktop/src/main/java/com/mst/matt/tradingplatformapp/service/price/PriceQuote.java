package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.Trade;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Universal price quote returned by ANY price service implementation.
 * Abstracts away differences between Binance, Yahoo, CoinGecko, Frankfurter.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceQuote {

    private String symbol;
    private String assetName;
    private Trade.AssetType assetType;

    private BigDecimal price;
    private BigDecimal open24h;
    private BigDecimal high24h;
    private BigDecimal low24h;
    private BigDecimal change24h;          // absolute change
    private BigDecimal changePct24h;       // % change
    private BigDecimal volume24h;
    private BigDecimal marketCap;

    private String currency;               // quote currency: "USDT", "USD", "EUR"
    private String exchange;

    private LocalDateTime timestamp;
    private boolean isUp;                  // true = price ↑ vs previous close
}