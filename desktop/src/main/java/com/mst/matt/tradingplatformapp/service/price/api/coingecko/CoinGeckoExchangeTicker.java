package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoExchangeTicker {
    private String base;
    private String target;
    private Map<String, String> market;
    private BigDecimal last;
    private BigDecimal volume;
    @SerializedName("bid_ask_spread_percentage")
    private BigDecimal bidAskSpreadPercentage;
    @SerializedName("trust_score")
    private String trustScore;
    @SerializedName("trade_url")
    private String tradeUrl;
}
