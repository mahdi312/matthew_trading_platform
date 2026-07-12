package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoTokenInfo {
    private String name;
    private String symbol;
    private Integer decimals;
    private List<String> websites;
    private String description;
    @SerializedName("social_links")
    private Map<String, String> socialLinks;
}
