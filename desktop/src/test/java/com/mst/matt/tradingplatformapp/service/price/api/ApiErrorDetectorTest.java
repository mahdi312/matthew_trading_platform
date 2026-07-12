package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorDetectorTest {

    private static boolean isError(String json) {
        return ApiErrorDetector.isErrorPayload(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void treatsNullAndCommonProviderErrorShapesAsErrors() {
        assertThat(ApiErrorDetector.isErrorPayload(null)).isTrue();
        assertThat(isError("{\"Note\":\"rate limit\"}")).isTrue();
        assertThat(isError("{\"success\":false,\"error\":{\"code\":101}}")).isTrue();
        assertThat(isError("{\"error\":\"invalid api key\"}")).isTrue();
        assertThat(isError("{\"status\":\"ERROR\"}")).isTrue();
        assertThat(isError("{\"code\":429,\"message\":\"too many requests\"}")).isTrue();
        assertThat(isError("{\"quandl_error\":{\"code\":\"QELx01\"}}")).isTrue();
        assertThat(isError("{\"Response\":\"Error\"}")).isTrue();
        assertThat(isError("{\"status\":{\"error_code\":1006}}")).isTrue();
    }

    @Test
    void allowsSuccessfulPayloadsIncludingCoinMarketCapStatusZero() {
        assertThat(isError("{\"price\":123.45,\"symbol\":\"AAPL\"}")).isFalse();
        assertThat(isError("{\"status\":{\"error_code\":0},\"data\":{\"BTC\":{}}}")).isFalse();
        assertThat(isError("{\"code\":200,\"message\":\"ok\",\"meta\":{\"symbol\":\"AAPL\"}}")).isFalse();
    }
}
