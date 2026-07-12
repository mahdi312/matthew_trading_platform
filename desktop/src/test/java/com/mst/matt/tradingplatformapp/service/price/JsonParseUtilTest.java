package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class JsonParseUtilTest {

    @Test
    void asPercent_stripsTrailingPercentSign() {
        JsonObject q = new JsonObject();
        q.addProperty("10. change percent", "0.3126%");
        assertEquals(0, new BigDecimal("0.3126").compareTo(
                JsonParseUtil.asPercent(q, "10. change percent")));
    }

    @Test
    void asBigDecimal_parsesCommaSeparatedNumbers() {
        JsonObject o = new JsonObject();
        o.addProperty("v", "1,234.56");
        assertEquals(0, new BigDecimal("1234.56").compareTo(JsonParseUtil.asBigDecimal(o, "v")));
    }
}
