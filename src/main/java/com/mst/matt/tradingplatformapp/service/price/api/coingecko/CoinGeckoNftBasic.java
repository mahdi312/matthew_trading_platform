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
public class CoinGeckoNftBasic {
    private String id;
    @SerializedName("contract_address")
    private String contractAddress;
    private String name;
    private String symbol;
    @SerializedName("asset_platform_id")
    private String assetPlatformId;
}
