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
public class CoinGeckoNftCollection {
    private String id;
    @SerializedName("contract_address")
    private String contractAddress;
    private String name;
    private String symbol;
    @SerializedName("asset_platform_id")
    private String assetPlatformId;
    @SerializedName("floor_price")
    private Map<String, BigDecimal> floorPrice;
    @SerializedName("market_cap")
    private Map<String, BigDecimal> marketCap;
    @SerializedName("volume_24h")
    private Map<String, BigDecimal> volume24h;
    private String image;
}
