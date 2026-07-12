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
public class CoinGeckoExchange {
    private String id;
    private String name;
    @SerializedName("year_established")
    private Integer yearEstablished;
    private String country;
    private String url;
    private String image;
    @SerializedName("trust_score")
    private Integer trustScore;
    @SerializedName("trade_volume_24h_btc")
    private BigDecimal tradeVolume24hBtc;
    @SerializedName("trade_volume_24h_btc_normalized")
    private BigDecimal tradeVolume24hBtcNormalized;
}
