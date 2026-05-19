package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class HttpJsonClient {

    private static final Logger log = LoggerFactory.getLogger(HttpJsonClient.class);
    private final OkHttpClient http;
    private final Gson gson = new Gson();

    public HttpJsonClient(@Autowired @Qualifier("priceHttpClient") OkHttpClient http) {
        this.http = http;
    }

    public Optional<JsonObject> getJson(String url) {
        return getJson(url, null);
    }

    public Optional<JsonObject> getJson(String url, String userAgent) {
        Request.Builder builder = new Request.Builder().url(url)
                .addHeader("Accept", "application/json");
        if (userAgent != null) {
            builder.addHeader("User-Agent", userAgent);
        }
        try (Response response = http.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("HTTP {} for {}", response.code(), abbreviate(url));
                return Optional.empty();
            }
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null) return Optional.empty();
            if (root.has("Note") || root.has("Information") || root.has("Error Message")) {
                log.warn("API message for {}: {}", abbreviate(url),
                        root.has("Note") ? root.get("Note").getAsString()
                                : root.has("Information") ? root.get("Information").getAsString()
                                : root.get("Error Message").getAsString());
                return Optional.empty();
            }
            return Optional.of(root);
        } catch (IOException e) {
            log.warn("Request failed for {}: {}", abbreviate(url), e.getMessage());
            return Optional.empty();
        }
    }

    private static String abbreviate(String url) {
        int q = url.indexOf('?');
        return q > 0 ? url.substring(0, Math.min(q, 80)) + "..." : url;
    }
}
