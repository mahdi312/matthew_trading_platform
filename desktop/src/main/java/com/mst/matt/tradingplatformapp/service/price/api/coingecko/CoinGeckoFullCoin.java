package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoFullCoin {
    private String id;
    private String symbol;
    private String name;
    private Map<String, Object> links;
    @SerializedName("market_data")
    private Map<String, Object> marketData;
    @SerializedName("community_data")
    private Map<String, Object> communityData;
    @SerializedName("developer_data")
    private Map<String, Object> developerData;
}
