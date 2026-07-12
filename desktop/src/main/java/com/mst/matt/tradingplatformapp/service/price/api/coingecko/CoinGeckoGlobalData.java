package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoGlobalData {
    @SerializedName("active_cryptocurrencies")
    private Integer activeCryptocurrencies;
    @SerializedName("total_market_cap")
    private Map<String, BigDecimal> totalMarketCap;
    @SerializedName("market_cap_percentage")
    private Map<String, BigDecimal> marketCapPercentage;
    @SerializedName("total_volume")
    private Map<String, BigDecimal> totalVolume;
    @SerializedName("btc_dominance")
    private BigDecimal btcDominance;
    @SerializedName("btc_dominance_yesterday_percentage_change")
    private BigDecimal btcDominanceYesterdayPercentageChange;
    @SerializedName("last_updated")
    private Long lastUpdated;
}
