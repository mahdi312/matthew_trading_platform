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
public class CoinGeckoDerivative {
    private String market;
    private String symbol;
    private BigDecimal price;
    @SerializedName("open_interest")
    private BigDecimal openInterest;
    @SerializedName("funding_rate")
    private BigDecimal fundingRate;
}
