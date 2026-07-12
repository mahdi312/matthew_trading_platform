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
public class CoinGeckoExchangeBasic {
    private String id;
    private String name;
    @SerializedName("trade_volume_24h_btc")
    private BigDecimal tradeVolume24hBtc;
    @SerializedName("trust_score")
    private Integer trustScore;
}
