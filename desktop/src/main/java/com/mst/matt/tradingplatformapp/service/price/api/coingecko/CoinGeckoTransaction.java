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
public class CoinGeckoTransaction {
    private String date;
    @SerializedName("coin_id")
    private String coinId;
    @SerializedName("amount_change")
    private BigDecimal amountChange;
    private String type;
}
