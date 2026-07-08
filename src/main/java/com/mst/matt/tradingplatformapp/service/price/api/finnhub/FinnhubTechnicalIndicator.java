package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub Technical Indicator response — used by {@code GET /indicator/sma},
 * {@code /indicator/ema}, {@code /indicator/rsi}, {@code /indicator/macd},
 * {@code /indicator/stoch}, {@code /indicator/bb}, etc.
 * Free-tier endpoint.
 *
 * <p>The response keys depend on the indicator type. Common ones:
 * <ul>
 *   <li>SMA/EMA/RSI: {@code s} (status), {@code t} (timestamps), and the indicator array</li>
 *   <li>MACD: {@code macd}, {@code macdSignal}, {@code macdHist}</li>
 *   <li>STOCH: {@code slowK}, {@code slowD}</li>
 *   <li>BB: {@code upperBand}, {@code middleBand}, {@code lowerBand}</li>
 * </ul>
 */
public record FinnhubTechnicalIndicator(
        @SerializedName("s")           String status,
        @SerializedName("t")           List<Long> timestamps,
        // SMA / EMA / RSI
        @SerializedName("sma")         List<Double> sma,
        @SerializedName("ema")         List<Double> ema,
        @SerializedName("rsi")         List<Double> rsi,
        // MACD
        @SerializedName("macd")        List<Double> macd,
        @SerializedName("macdSignal")  List<Double> macdSignal,
        @SerializedName("macdHist")    List<Double> macdHist,
        // Stochastic
        @SerializedName("slowK")       List<Double> slowK,
        @SerializedName("slowD")       List<Double> slowD,
        // Bollinger Bands
        @SerializedName("upperBand")   List<Double> upperBand,
        @SerializedName("middleBand")  List<Double> middleBand,
        @SerializedName("lowerBand")   List<Double> lowerBand
) {
    /** Returns true if the API returned status "ok". */
    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }
}
