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
public class CoinGeckoCoinCategory {
    private String id;
    private String name;
    @SerializedName("market_cap")
    private BigDecimal marketCap;
    @SerializedName("volume_24h")
    private BigDecimal volume24h;
}
