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
public class CoinGeckoCoinSearchItem {
    private String id;
    private String name;
    private String symbol;
    @SerializedName("market_cap_rank")
    private Integer marketCapRank;
    private String thumb;
}
