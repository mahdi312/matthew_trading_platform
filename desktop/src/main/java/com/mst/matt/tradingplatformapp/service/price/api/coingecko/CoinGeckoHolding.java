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
public class CoinGeckoHolding {
    @SerializedName("coin_id")
    private String coinId;
    private BigDecimal amount;
    @SerializedName("percentage_of_supply")
    private BigDecimal percentageOfSupply;
}
