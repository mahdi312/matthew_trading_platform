package com.mst.matt.tradingplatformapp.service.price;

import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class PriceServiceTestSupport {

    private PriceServiceTestSupport() {}

    static String fixture(String name) throws IOException {
        try (var in = PriceServiceTestSupport.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) throw new IOException("Missing fixture: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void setBaseUrl(Object service, String fieldName, String url) {
        ReflectionTestUtils.setField(service, fieldName, url);
    }
}
