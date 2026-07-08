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
public class CoinGeckoPoolTrade {
    @SerializedName("tx_hash")
    private String txHash;
    @SerializedName("from_token_amount")
    private BigDecimal fromTokenAmount;
    @SerializedName("from_token_symbol")
    private String fromTokenSymbol;
    @SerializedName("to_token_amount")
    private BigDecimal toTokenAmount;
    @SerializedName("to_token_symbol")
    private String toTokenSymbol;
    private String kind;
    @SerializedName("block_timestamp")
    private String blockTimestamp;
    @SerializedName("volume_usd")
    private BigDecimal volumeUsd;
}
