package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Response models for DEX endpoints:
 * {@code GET /v4/dex/spot-pairs/latest},
 * {@code GET /v1/dex/spot-pairs/latest},
 * {@code GET /v1/dex/search},
 * {@code GET /v1/dex/tokens/trending/list},
 * {@code GET /v1/dex/new/list},
 * {@code GET /v1/dex/meme/list},
 * {@code GET /v1/dex/gainer-loser/list}.
 */
public final class CmcDex {

    private CmcDex() {}

    public record DexPair(
            @SerializedName("pairAddress")       String pairAddress,
            @SerializedName("pairName")          String pairName,
            @SerializedName("baseTokenAddress")  String baseTokenAddress,
            @SerializedName("baseTokenSymbol")   String baseTokenSymbol,
            @SerializedName("quoteTokenAddress") String quoteTokenAddress,
            @SerializedName("quoteTokenSymbol")  String quoteTokenSymbol,
            @SerializedName("platformId")        String platformId,
            @SerializedName("platformName")      String platformName,
            @SerializedName("price")             Double price,
            @SerializedName("priceUsd")          Double priceUsd,
            @SerializedName("volume24h")         Double volume24h,
            @SerializedName("priceChange24h")    Double priceChange24h,
            @SerializedName("liquidity")         Double liquidity
    ) {}

    public record DexPairsResponse(
            @SerializedName("data")   List<DexPair> data,
            @SerializedName("status") CmcStatus     status
    ) {}

    public record DexToken(
            @SerializedName("address")          String address,
            @SerializedName("symbol")           String symbol,
            @SerializedName("name")             String name,
            @SerializedName("platformId")       String platformId,
            @SerializedName("price")            Double price,
            @SerializedName("priceChange1h")    Double priceChange1h,
            @SerializedName("priceChange24h")   Double priceChange24h,
            @SerializedName("volume24h")        Double volume24h,
            @SerializedName("liquidity")        Double liquidity,
            @SerializedName("fdv")              Double fdv,
            @SerializedName("marketCap")        Double marketCap
    ) {}

    public record DexTokensResponse(
            @SerializedName("data")   List<DexToken> data,
            @SerializedName("status") CmcStatus      status
    ) {}

    public record DexSearchResult(
            @SerializedName("pairs")  List<DexPair>  pairs,
            @SerializedName("tokens") List<DexToken> tokens
    ) {}

    public record DexSearchResponse(
            @SerializedName("data")   DexSearchResult data,
            @SerializedName("status") CmcStatus       status
    ) {}

    public record DexPlatform(
            @SerializedName("id")          String id,
            @SerializedName("name")        String name,
            @SerializedName("chain")       String chain,
            @SerializedName("chainId")     String chainId,
            @SerializedName("router")      String router,
            @SerializedName("factory")     String factory
    ) {}

    public record DexPlatformListResponse(
            @SerializedName("data")   List<DexPlatform> data,
            @SerializedName("status") CmcStatus         status
    ) {}

    public record DexSecurityDetail(
            @SerializedName("address")           String  address,
            @SerializedName("platformId")        String  platformId,
            @SerializedName("isHoneypot")        Boolean isHoneypot,
            @SerializedName("isMintable")        Boolean isMintable,
            @SerializedName("isBlacklist")       Boolean isBlacklist,
            @SerializedName("holderCount")       Integer holderCount,
            @SerializedName("lpHolderCount")     Integer lpHolderCount,
            @SerializedName("buyTax")            Double  buyTax,
            @SerializedName("sellTax")           Double  sellTax,
            @SerializedName("creator")           String  creator,
            @SerializedName("createdAt")         String  createdAt
    ) {}

    public record DexSecurityResponse(
            @SerializedName("data")   DexSecurityDetail data,
            @SerializedName("status") CmcStatus         status
    ) {}
}
