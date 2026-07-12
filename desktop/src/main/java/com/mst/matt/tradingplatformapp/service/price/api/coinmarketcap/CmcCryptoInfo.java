package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Response for {@code GET /v2/cryptocurrency/info} — static metadata.
 */
public final class CmcCryptoInfo {

    private CmcCryptoInfo() {}

    public record Urls(
            @SerializedName("website")        List<String> website,
            @SerializedName("technical_doc")  List<String> technicalDoc,
            @SerializedName("twitter")        List<String> twitter,
            @SerializedName("reddit")         List<String> reddit,
            @SerializedName("message_board")  List<String> messageBoard,
            @SerializedName("announcement")   List<String> announcement,
            @SerializedName("chat")           List<String> chat,
            @SerializedName("explorer")       List<String> explorer,
            @SerializedName("source_code")    List<String> sourceCode
    ) {}

    public record CryptoInfoEntry(
            @SerializedName("id")          int     id,
            @SerializedName("name")        String  name,
            @SerializedName("symbol")      String  symbol,
            @SerializedName("slug")        String  slug,
            @SerializedName("category")    String  category,
            @SerializedName("description") String  description,
            @SerializedName("logo")        String  logo,
            @SerializedName("subreddit")   String  subreddit,
            @SerializedName("date_added")  String  dateAdded,
            @SerializedName("tags")        List<String> tags,
            @SerializedName("urls")        Urls    urls
    ) {}

    /** Data map keyed by coin ID (String). */
    public record CryptoInfoResponse(
            @SerializedName("data")   Map<String, CryptoInfoEntry> data,
            @SerializedName("status") CmcStatus                    status
    ) {}
}
