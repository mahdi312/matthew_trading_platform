package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Typed POJO for the TwelveData {@code GET /macd} indicator response.
 * MACD returns three values per data point: macd, signal, histogram.
 */
public record TwelveDataMacdValue(
        @SerializedName("datetime")  String datetime,
        @SerializedName("macd")      String macd,
        @SerializedName("macd_signal")  String macdSignal,
        @SerializedName("macd_hist")    String macdHist
) {}
