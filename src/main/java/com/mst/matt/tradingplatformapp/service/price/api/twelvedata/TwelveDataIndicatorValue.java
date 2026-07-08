package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

/**
 * Single-value indicator data point (RSI, SMA, EMA, WMA, ADX, ATR, CCI, MOM, ROC, etc.).
 * Includes extra fields that are null for indicators that don't provide them.
 */
public record TwelveDataIndicatorValue(
        @SerializedName("datetime")  String datetime,

        // --- Single-value indicators ---
        @SerializedName("rsi")       String rsi,
        @SerializedName("sma")       String sma,
        @SerializedName("ema")       String ema,
        @SerializedName("wma")       String wma,
        @SerializedName("dema")      String dema,
        @SerializedName("tema")      String tema,
        @SerializedName("trima")     String trima,
        @SerializedName("kama")      String kama,
        @SerializedName("t3")        String t3,
        @SerializedName("adx")       String adx,
        @SerializedName("adxr")      String adxr,
        @SerializedName("atr")       String atr,
        @SerializedName("cci")       String cci,
        @SerializedName("mom")       String mom,
        @SerializedName("roc")       String roc,
        @SerializedName("rocr")      String rocr,
        @SerializedName("willr")     String willr,
        @SerializedName("dx")        String dx,
        @SerializedName("trix")      String trix,
        @SerializedName("stddev")    String stddev,
        @SerializedName("var")       String var,
        @SerializedName("apo")       String apo,
        @SerializedName("ppo")       String ppo,

        // --- Parabolic SAR ---
        @SerializedName("sar")       String sar,

        // --- Bollinger Bands ---
        @SerializedName("upper_band")  String upperBand,
        @SerializedName("middle_band") String middleBand,
        @SerializedName("lower_band")  String lowerBand,

        // --- STOCH ---
        @SerializedName("slow_k")    String slowK,
        @SerializedName("slow_d")    String slowD,

        // --- STOCHF ---
        @SerializedName("fast_k")    String fastK,
        @SerializedName("fast_d")    String fastD,

        // --- STOCHRSI ---
        @SerializedName("stochrsi_k") String stochrsiK,
        @SerializedName("stochrsi_d") String stochrsiD,

        // --- Ultimate Oscillator ---
        @SerializedName("ultosc")    String ultosc,

        // --- +/- DI/DM ---
        @SerializedName("plus_di")   String plusDi,
        @SerializedName("minus_di")  String minusDi,
        @SerializedName("plus_dm")   String plusDm,
        @SerializedName("minus_dm")  String minusDm,

        // --- Hilbert Transform ---
        @SerializedName("ht_trendline")   String htTrendline,
        @SerializedName("ht_sine")        String htSine,
        @SerializedName("ht_leadsine")    String htLeadsine,
        @SerializedName("ht_dcperiod")    String htDcperiod,
        @SerializedName("ht_dcphase")     String htDcphase,
        @SerializedName("inphase")        String inphase,
        @SerializedName("quadrature")     String quadrature,

        // --- Math functions ---
        @SerializedName("ceil")      String ceil,
        @SerializedName("floor")     String floor,
        @SerializedName("round")     String round,
        @SerializedName("exp")       String exp,
        @SerializedName("max")       String max,
        @SerializedName("min")       String min,
        @SerializedName("avg")       String avg,
        @SerializedName("maxindex")  String maxindex,
        @SerializedName("minindex")  String minindex
) {}
