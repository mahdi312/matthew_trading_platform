package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoNetwork {
    private String id;
    private String name;
    @SerializedName("coingecko_asset_platform_id")
    private String coingeckoAssetPlatformId;
}
