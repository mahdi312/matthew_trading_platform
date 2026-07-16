package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Response models for utility/tools endpoints:
 * {@code GET /v2/tools/price-conversion},
 * {@code GET /v1/fiat/map},
 * {@code GET /v1/key/info}.
 */
public final class CmcTools {

    private CmcTools() {}

    public record ConversionQuote(
            @SerializedName("price")        double price,
            @SerializedName("last_updated") String lastUpdated
    ) {}

    public record ConversionResult(
            @SerializedName("id")     int    id,
            @SerializedName("symbol") String symbol,
            @SerializedName("name")   String name,
            @SerializedName("amount") double amount,
            @SerializedName("last_updated") String lastUpdated,
            @SerializedName("quote")  Map<String, ConversionQuote> quote
    ) {}

    public record PriceConversionResponse(
            @SerializedName("data")   ConversionResult data,
            @SerializedName("status") CmcStatus        status
    ) {}

    public record FiatEntry(
            @SerializedName("id")     int    id,
            @SerializedName("name")   String name,
            @SerializedName("sign")   String sign,
            @SerializedName("symbol") String symbol
    ) {}

    public record FiatMapResponse(
            @SerializedName("data")   List<FiatEntry> data,
            @SerializedName("status") CmcStatus       status
    ) {}

    public record KeyUsage(
            @SerializedName("current_minute") UsageStats currentMinute,
            @SerializedName("current_day")    UsageStats currentDay,
            @SerializedName("current_month")  UsageStats currentMonth
    ) {}

    public record UsageStats(
            @SerializedName("credits_used")      int credits_used,
            @SerializedName("credits_left")      int credits_left
    ) {}

    public record KeyPlan(
            @SerializedName("credit_limit_monthly")      Integer creditLimitMonthly,
            @SerializedName("credit_limit_monthly_reset") String creditLimitMonthlyReset,
            @SerializedName("rate_limit_minute")          Integer rateLimitMinute,
            @SerializedName("plan")                       String  plan
    ) {}

    public record KeyInfo(
            @SerializedName("usage")  KeyUsage usage,
            @SerializedName("plan")   KeyPlan  plan
    ) {}

    public record KeyInfoResponse(
            @SerializedName("data")   KeyInfo   data,
            @SerializedName("status") CmcStatus status
    ) {}
}
