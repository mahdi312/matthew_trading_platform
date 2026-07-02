package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoMarketChart {
    private List<List<Object>> prices;
    @SerializedName("market_caps")
    private List<List<Object>> marketCaps;
    @SerializedName("total_volumes")
    private List<List<Object>> totalVolumes;
}
