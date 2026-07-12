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
public class CoinGeckoDerivativeExchange {
    private String id;
    private String name;
    @SerializedName("open_interest_btc")
    private BigDecimal openInterestBtc;
    @SerializedName("year_established")
    private Integer yearEstablished;
    private String country;
    private String image;
}
