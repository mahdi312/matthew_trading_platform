package com.mst.matt.tradingplatformapp.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TradeComputePnLTest {

    @Test
    void longClosedTrade_includesFeesInPnl() {
        Trade t = Trade.builder()
                .direction(Trade.TradeDirection.LONG)
                .entryPrice(new BigDecimal("100"))
                .exitPrice(new BigDecimal("110"))
                .quantity(new BigDecimal("2"))
                .fee(new BigDecimal("1"))
                .build();

        t.computePnL();

        assertEquals(0, new BigDecimal("19").compareTo(t.getPnlAmount()));
        assertEquals(0, new BigDecimal("10").compareTo(t.getPnlPercent()));
        assertEquals(0, new BigDecimal("200").compareTo(t.getTotalInvested()));
    }

    @Test
    void shortClosedTrade_computesInvertedPriceDiff() {
        Trade t = Trade.builder()
                .direction(Trade.TradeDirection.SHORT)
                .entryPrice(new BigDecimal("100"))
                .exitPrice(new BigDecimal("90"))
                .quantity(BigDecimal.ONE)
                .build();

        t.computePnL();

        assertEquals(0, new BigDecimal("10").compareTo(t.getPnlAmount()));
    }

    @Test
    void openTrade_hasNoRealizedPnl() {
        Trade t = Trade.builder()
                .direction(Trade.TradeDirection.LONG)
                .entryPrice(new BigDecimal("50"))
                .quantity(BigDecimal.ONE)
                .build();

        t.computePnL();

        assertNull(t.getPnlAmount());
        assertNull(t.getPnlPercent());
        assertEquals(0, new BigDecimal("50").compareTo(t.getTotalInvested()));
    }
}
