package com.mst.matt.tradingplatformapp.service.fundamental;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.fundamental.FundamentalsReport;
import com.mst.matt.tradingplatformapp.model.fundamental.YearlyFinancialRow;
import com.mst.matt.tradingplatformapp.service.price.HttpJsonClient;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;
import com.mst.matt.tradingplatformapp.service.price.SymbolNormalizer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AlphaVantageFundamentalService implements FundamentalService {

    private final MarketApiProperties keys;
    private final HttpJsonClient http;

    public AlphaVantageFundamentalService(MarketApiProperties keys, HttpJsonClient http) {
        this.keys = keys;
        this.http = http;
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
        String base = "https://www.alphavantage.co/query?symbol=" + sym
                + "&apikey=" + keys.getAlphavantageKey();

        Optional<JsonObject> incomeOpt = http.getJson(base + "&function=INCOME_STATEMENT");
        if (incomeOpt.isEmpty()) return Optional.empty();

        JsonObject income = incomeOpt.get();
        JsonArray annual = income.has("annualReports")
                ? income.getAsJsonArray("annualReports") : null;
        if (annual == null || annual.isEmpty()) return Optional.empty();

        List<YearlyFinancialRow> rows = new ArrayList<>();
        for (JsonElement el : annual) {
            JsonObject r = el.getAsJsonObject();
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

        List<String> earningsNotes = new ArrayList<>();
        http.getJson(base + "&function=EARNINGS").ifPresent(earn -> {
            JsonArray annualEarn = earn.has("annualEarnings")
                    ? earn.getAsJsonArray("annualEarnings") : null;
            if (annualEarn != null) {
                int limit = Math.min(5, annualEarn.size());
                for (int i = 0; i < limit; i++) {
                    JsonObject e = annualEarn.get(i).getAsJsonObject();
                    earningsNotes.add(e.get("fiscalDateEnding").getAsString()
                            + " EPS " + e.get("reportedEPS").getAsString());
                }
            }
        });

        String name = income.has("symbol") ? sym : sym;
        return Optional.of(FundamentalsReport.builder()
                .symbol(sym)
                .companyName(name)
                .providerUsed(getProviderId().getLabel())
                .assetTypeLabel("Equity / Multi-asset")
                .yearlyRows(rows)
                .earningsNotes(earningsNotes)
                .summaryText(buildSummary(rows))
                .build());
    }

    private static BigDecimal parse(JsonObject o, String key) {
        return JsonParseUtil.asBigDecimal(o, key);
    }

    private static String buildSummary(List<YearlyFinancialRow> rows) {
        if (rows.isEmpty()) return "No annual data.";
        YearlyFinancialRow latest = rows.get(0);
        return "Latest fiscal year " + latest.getFiscalYear()
                + " — net income " + format(latest.getNetIncome())
                + ", revenue " + format(latest.getTotalRevenue()) + ".";
    }

    private static String format(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) return "n/a";
        return v.toPlainString();
    }
}
