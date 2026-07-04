package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response models for content/news endpoints:
 * {@code GET /v1/content/latest},
 * {@code GET /v1/content/posts/top},
 * {@code GET /v1/content/posts/latest}.
 */
public final class CmcContent {

    private CmcContent() {}

    public record ContentSource(
            @SerializedName("id")   String id,
            @SerializedName("name") String name
    ) {}

    public record ContentEntry(
            @SerializedName("id")             String        id,
            @SerializedName("cover")          String        cover,
            @SerializedName("title")          String        title,
            @SerializedName("subtitle")       String        subtitle,
            @SerializedName("type")           String        type,
            @SerializedName("url")            String        url,
            @SerializedName("created_at")     String        createdAt,
            @SerializedName("released_at")    String        releasedAt,
            @SerializedName("source_name")    String        sourceName,
            @SerializedName("source_url")     String        sourceUrl,
            @SerializedName("assets")         List<Object>  assets
    ) {}

    public record ContentListResponse(
            @SerializedName("data")   ContentListData data,
            @SerializedName("status") CmcStatus       status
    ) {}

    public record ContentListData(
            @SerializedName("list") List<ContentEntry> list
    ) {}

    public record CommunityPost(
            @SerializedName("id")         String  id,
            @SerializedName("title")      String  title,
            @SerializedName("body")       String  body,
            @SerializedName("author")     String  author,
            @SerializedName("created_at") String  createdAt,
            @SerializedName("likes")      Integer likes,
            @SerializedName("comments")   Integer comments
    ) {}

    public record CommunityPostsResponse(
            @SerializedName("data")   List<CommunityPost> data,
            @SerializedName("status") CmcStatus           status
    ) {}

    public record CommunityTrendingToken(
            @SerializedName("id")      int    id,
            @SerializedName("name")    String name,
            @SerializedName("symbol")  String symbol,
            @SerializedName("rank")    Integer rank
    ) {}

    public record CommunityTrendingResponse(
            @SerializedName("data")   List<CommunityTrendingToken> data,
            @SerializedName("status") CmcStatus                    status
    ) {}
}
