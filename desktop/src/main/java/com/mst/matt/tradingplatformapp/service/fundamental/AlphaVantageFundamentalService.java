package com.mst.matt.tradingplatformapp.service.fundamental;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.fundamental.FundamentalsReport;
import com.mst.matt.tradingplatformapp.model.fundamental.YearlyFinancialRow;
import com.mst.matt.tradingplatformapp.service.price.HttpJsonClient;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;
import com.mst.matt.tradingplatformapp.service.price.SymbolNormalizer;
import com.mst.matt.tradingplatformapp.service.price.api.alphavantage.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Alpha Vantage fundamental data service.
 *
 * <h3>Endpoints used:</h3>
 * <ul>
 *   <li>{@code OVERVIEW}          — company name, sector, P/E, EPS, market cap, etc.</li>
 *   <li>{@code INCOME_STATEMENT}  — annual revenue, gross profit, net income, EBITDA</li>
 *   <li>{@code EARNINGS}          — annual and quarterly EPS history</li>
 *   <li>{@code BALANCE_SHEET}     — (enrichment via AlphaVantageMarketService)</li>
 *   <li>{@code CASH_FLOW}         — (enrichment via AlphaVantageMarketService)</li>
 * </ul>
 *
 * <p>All calls are routed through the shared {@code "alphavantage"} throttle bucket
 * registered by {@link AlphaVantagePriceService}
 * (5 requests/minute free tier).
 */
@Service
public class AlphaVantageFundamentalService implements FundamentalService {

    private final MarketApiProperties       keys;
    private final HttpJsonClient            http;
    private final AlphaVantageMarketService marketService;

    @Autowired
    public AlphaVantageFundamentalService(MarketApiProperties keys,
                                          HttpJsonClient http,
                                          AlphaVantageMarketService marketService) {
        this.keys           = keys;
        this.http           = http;
        this.marketService  = marketService;
    }

    @Override
    public FundamentalDataProvider getProviderId() {
        return FundamentalDataProvider.ALPHA_VANTAGE;
    }

    @Override
    public boolean isEnabled() {
        return keys.hasAlphavantageKey();
    }

    @Override
    public Optional<FundamentalsReport> fetchReport(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);

        // ── 1. Company overview (name, sector, P/E, EPS, market cap) ──────────
        Optional<AlphaVantageCompanyOverview> overviewOpt = marketService.getCompanyOverview(sym);
        String companyName = overviewOpt.map(AlphaVantageCompanyOverview::name).orElse(sym);
        String assetTypeLabel = overviewOpt.map(o -> {
            String sector = o.sector() != null && !o.sector().isBlank() ? o.sector() : "";
            String industry = o.industry() != null && !o.industry().isBlank() ? o.industry() : "";
            return sector.isBlank() ? "Equity / Multi-asset"
                    : sector + (industry.isBlank() ? "" : " — " + industry);
        }).orElse("Equity / Multi-asset");

        // ── 2. Income statement (annual rows) ─────────────────────────────────
        Optional<AlphaVantageFinancialStatement> incomeOpt = marketService.getIncomeStatement(sym);
        if (incomeOpt.isEmpty()) {
            // Fallback: direct HTTP call (preserves existing behaviour if market service is not ready)
            incomeOpt = fetchIncomeStatementDirect(sym);
        }
        if (incomeOpt.isEmpty()) return Optional.empty();

        List<YearlyFinancialRow> rows = buildYearlyRows(incomeOpt.get());
        if (rows.isEmpty()) return Optional.empty();

        // ── 3. Earnings (EPS notes) ────────────────────────────────────────────
        List<String> earningsNotes = buildEarningsNotes(sym);

        // ── 4. Enrich summary with overview data ───────────────────────────────
        String summaryText = buildSummary(rows, overviewOpt.orElse(null));

        return Optional.of(FundamentalsReport.builder()
                .symbol(sym)
                .companyName(companyName)
                .providerUsed(getProviderId().getLabel())
                .assetTypeLabel(assetTypeLabel)
                .yearlyRows(rows)
                .earningsNotes(earningsNotes)
                .summaryText(summaryText)
                .build());
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Optional<AlphaVantageFinancialStatement> fetchIncomeStatementDirect(String sym) {
        String url = "https://www.alphavantage.co/query?function=INCOME_STATEMENT&symbol=" + sym
                + "&apikey=" + keys.getAlphavantageKey();
        return http.getJson(url, null, "alphavantage")
                .map(root -> {
                    com.google.gson.Gson g = new com.google.gson.Gson();
                    return g.fromJson(root, AlphaVantageFinancialStatement.class);
                })
                .filter(s -> s.symbol() != null);
    }

    private List<YearlyFinancialRow> buildYearlyRows(AlphaVantageFinancialStatement stmt) {
        if (stmt.annualReports() == null) return List.of();
        List<YearlyFinancialRow> rows = new ArrayList<>();
        for (JsonObject r : stmt.annualReports()) {
            String year = r.has("fiscalDateEnding")
                    ? r.get("fiscalDateEnding").getAsString().substring(0, 4) : "?";
            rows.add(YearlyFinancialRow.builder()
                    .fiscalYear(year)
                    .totalRevenue(parse(r, "totalRevenue"))
                    .grossProfit(parse(r, "grossProfit"))
                    .operatingIncome(parse(r, "operatingIncome"))
                    .netIncome(parse(r, "netIncome"))
                    .ebitda(parse(r, "ebitda"))
                    .currency(r.has("reportedCurrency")
                            ? r.get("reportedCurrency").getAsString() : "USD")
                    .build());
        }
        rows.sort(Comparator.comparing(YearlyFinancialRow::getFiscalYear).reversed());
        return rows;
    }

    private List<String> buildEarningsNotes(String sym) {
        List<String> notes = new ArrayList<>();

        // Try typed earnings via market service first
        marketService.getEarnings(sym).ifPresent(earnings -> {
            if (earnings.annualEarnings() != null) {
                int limit = Math.min(5, earnings.annualEarnings().size());
                for (int i = 0; i < limit; i++) {
                    AlphaVantageEarnings.AnnualEarning e = earnings.annualEarnings().get(i);
                    notes.add(e.fiscalDateEnding() + " EPS " + e.reportedEPS());
                }
            }
            // Add latest quarterly EPS if available
            if (earnings.quarterlyEarnings() != null && !earnings.quarterlyEarnings().isEmpty()) {
                AlphaVantageEarnings.QuarterlyEarning q = earnings.quarterlyEarnings().get(0);
                if (q.surprise() != null && !q.surprise().isBlank()
                        && !"None".equalsIgnoreCase(q.surprise())) {
                    notes.add("Q" + q.fiscalDateEnding() + " EPS surprise: " + q.surprise()
                            + " (" + q.surprisePercentage() + "%)");
                }
            }
        });

        if (notes.isEmpty()) {
            // Fallback: direct HTTP call
            String url = "https://www.alphavantage.co/query?function=EARNINGS&symbol=" + sym
                    + "&apikey=" + keys.getAlphavantageKey();
            http.getJson(url, null, "alphavantage").ifPresent(earn -> {
                JsonArray annualEarn = earn.has("annualEarnings")
                        ? earn.getAsJsonArray("annualEarnings") : null;
                if (annualEarn != null) {
                    int limit = Math.min(5, annualEarn.size());
                    for (int i = 0; i < limit; i++) {
                        JsonObject e = annualEarn.get(i).getAsJsonObject();
                        notes.add(e.get("fiscalDateEnding").getAsString()
                                + " EPS " + e.get("reportedEPS").getAsString());
                    }
                }
            });
        }

        return notes;
    }

    private static BigDecimal parse(JsonObject o, String key) {
        return JsonParseUtil.asBigDecimal(o, key);
    }

    private static String buildSummary(List<YearlyFinancialRow> rows,
                                        AlphaVantageCompanyOverview overview) {
        if (rows.isEmpty()) return "No annual data.";
        YearlyFinancialRow latest = rows.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("Latest fiscal year ").append(latest.getFiscalYear())
          .append(" — net income ").append(format(latest.getNetIncome()))
          .append(", revenue ").append(format(latest.getTotalRevenue())).append(".");

        if (overview != null) {
            if (overview.peRatio() != null && !overview.peRatio().isBlank()
                    && !"None".equalsIgnoreCase(overview.peRatio())) {
                sb.append(" P/E: ").append(overview.peRatio()).append(".");
            }
            if (overview.eps() != null && !overview.eps().isBlank()
                    && !"None".equalsIgnoreCase(overview.eps())) {
                sb.append(" EPS: ").append(overview.eps()).append(".");
            }
            if (overview.dividendYield() != null && !overview.dividendYield().isBlank()
                    && !"None".equalsIgnoreCase(overview.dividendYield())
                    && !"0".equals(overview.dividendYield())) {
                sb.append(" Div yield: ").append(overview.dividendYield()).append("%.");
            }
        }
        return sb.toString();
    }

    private static String format(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) return "n/a";
        return v.toPlainString();
    }
}
