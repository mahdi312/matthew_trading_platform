package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO representing a single instrument from TwelveData reference endpoints
 * ({@code /stocks}, {@code /etfs}, {@code /indices}, {@code /cryptocurrencies},
 * {@code /forex_pairs}).
 *
 * All list endpoints share the same core fields, with minor additions.
 */
public record TwelveDataInstrument(
        @SerializedName("symbol")            String symbol,
        @SerializedName("name")              String name,
        @SerializedName("currency")          String currency,
        @SerializedName("exchange")          String exchange,
        @SerializedName("mic_code")          String micCode,
        @SerializedName("country")           String country,
        @SerializedName("type")              String type,
        // Crypto-specific fields
        @SerializedName("currency_base")     String currencyBase,
        @SerializedName("currency_quote")    String currencyQuote,
        // Forex-specific
        @SerializedName("available_exchanges") List<String> availableExchanges
) {}
