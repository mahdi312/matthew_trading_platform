package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoDefiData {
    @SerializedName("defi_market_cap")
    private BigDecimal defiMarketCap;
    @SerializedName("trading_volume_24h")
    private BigDecimal tradingVolume24h;
    @SerializedName("defi_dominance")
    private BigDecimal defiDominance;
    @SerializedName("top_coin_name")
    private String topCoinName;
    @SerializedName("top_coin_dominance")
    private BigDecimal topCoinDominance;
}
