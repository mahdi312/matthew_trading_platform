package com.mst.matt.aiservice.client;

import com.mst.matt.contracts.provider.dto.NewsArticleDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for {@code reference-data-service}.
 * <p>
 * Resolves the service URL via Eureka using the logical name
 * {@code reference-data-service}, or the hard-coded fallback URL
 * from {@code services.reference-data.url} when Eureka is not running.
 */
@FeignClient(
        name  = "reference-data-service",
        url   = "${services.reference-data.url:}",
        path  = "/api/reference"
)
public interface ReferenceDataClient {

    /**
     * Fetch recent news articles for a symbol from reference-data-service.
     *
     * @param symbol   canonical platform symbol (e.g. "AAPL", "BTCUSDT")
     * @param limit    max articles to return (default 10 on the server side)
     * @return list of normalized {@link NewsArticleDto}
     */
    @GetMapping("/news")
    List<NewsArticleDto> getNews(
            @RequestParam("symbol") String symbol,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    );
}
