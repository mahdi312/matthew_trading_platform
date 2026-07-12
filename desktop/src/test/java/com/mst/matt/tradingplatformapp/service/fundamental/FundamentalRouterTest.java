package com.mst.matt.tradingplatformapp.service.fundamental;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.fundamental.FundamentalsReport;
import com.mst.matt.tradingplatformapp.model.fundamental.YearlyFinancialRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T-09: verify that {@link FundamentalRouter} respects profile preference and falls back
 * to the next provider when the first one yields no rows.
 */
class FundamentalRouterTest {

    @Test
    void fetch_prefersProfileSelectedProvider() {
        StubService alpha = new StubService(FundamentalDataProvider.ALPHA_VANTAGE, true);
        StubService finnhub = new StubService(FundamentalDataProvider.FINNHUB, true);
        FundamentalRouter router = new FundamentalRouter(List.of(alpha, finnhub));

        UserProfile profile = new UserProfile();
        profile.setFundamentalProvider("FINNHUB");

        Optional<FundamentalsReport> report = router.fetch("AAPL", profile);

        assertTrue(report.isPresent());
        assertEquals("FINNHUB", report.get().getProviderUsed());
    }

    @Test
    void fetch_fallsBackWhenFirstProviderReturnsEmpty() {
        StubService alpha = new StubService(FundamentalDataProvider.ALPHA_VANTAGE, false);
        StubService finnhub = new StubService(FundamentalDataProvider.FINNHUB, true);
        FundamentalRouter router = new FundamentalRouter(List.of(alpha, finnhub));

        Optional<FundamentalsReport> report = router.fetch("AAPL", null);

        assertTrue(report.isPresent());
        assertEquals("FINNHUB", report.get().getProviderUsed());
    }

    @Test
    void enabledProviders_includesAutoAndConfiguredOnes() {
        StubService alpha = new StubService(FundamentalDataProvider.ALPHA_VANTAGE, true);
        FundamentalRouter router = new FundamentalRouter(List.of(alpha));

        List<FundamentalDataProvider> providers = router.enabledProviders();

        assertEquals(FundamentalDataProvider.AUTO, providers.get(0));
        assertTrue(providers.contains(FundamentalDataProvider.ALPHA_VANTAGE));
    }

    /** Minimal stub returning a deterministic report (or empty). */
    private static class StubService implements FundamentalService {
        private final FundamentalDataProvider id;
        private final boolean returnData;

        StubService(FundamentalDataProvider id, boolean returnData) {
            this.id = id;
            this.returnData = returnData;
        }

        @Override public FundamentalDataProvider getProviderId() { return id; }
        @Override public boolean isEnabled() { return true; }

        @Override public Optional<FundamentalsReport> fetchReport(String symbol) {
            if (!returnData) return Optional.empty();
            YearlyFinancialRow row = YearlyFinancialRow.builder()
                    .fiscalYear("2024")
                    .totalRevenue(new BigDecimal("100000000"))
                    .build();
            FundamentalsReport report = FundamentalsReport.builder()
                    .symbol(symbol)
                    .companyName("Stub Inc")
                    .providerUsed(id.name())
                    .yearlyRows(new java.util.ArrayList<>(List.of(row)))
                    .build();
            return Optional.of(report);
        }
    }
}
