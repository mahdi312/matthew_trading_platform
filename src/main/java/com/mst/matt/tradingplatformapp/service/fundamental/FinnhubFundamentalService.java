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
import java.util.*;

@Service
public class FinnhubFundamentalService implements FundamentalService {

    private final MarketApiProperties keys;
    private final HttpJsonClient http;

    public FinnhubFundamentalService(MarketApiProperties keys, HttpJsonClient http) {
        this.keys = keys;
        this.http = http;
    }

    @Override
    public FundamentalDataProvider getProviderId() {
        return FundamentalDataProvider.FINNHUB;
    }

    @Override
    public boolean isEnabled() {
        return keys.hasFinnhubKey();
    }

    @Override
    public Optional<FundamentalsReport> fetchReport(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        String token = keys.getFinnhubKey();

        String profileUrl = "https://finnhub.io/api/v1/stock/profile2?symbol=" + sym
                + "&token=" + token;
        String finUrl = "https://finnhub.io/api/v1/stock/financials-reported?symbol=" + sym
                + "&freq=annual&token=" + token;

        Optional<JsonObject> profileOpt = http.getJson(profileUrl);
        Optional<JsonObject> finOpt = http.getJson(finUrl);

        String companyName = sym;
        String sector = "";
        String industry = "";
        String country = "";
        if (profileOpt.isPresent()) {
            JsonObject p = profileOpt.get();
            if (p.has("name")) companyName = p.get("name").getAsString();
            if (p.has("finnhubIndustry")) industry = p.get("finnhubIndustry").getAsString();
            if (p.has("country")) country = p.get("country").getAsString();
            sector = industry;
        }

        List<YearlyFinancialRow> rows = new ArrayList<>();
        if (finOpt.isPresent()) {
            JsonArray data = finOpt.get().has("data")
                    ? finOpt.get().getAsJsonArray("data") : new JsonArray();
            for (JsonElement periodEl : data) {
                JsonObject period = periodEl.getAsJsonObject();
                String year = period.has("year")
                        ? String.valueOf(period.get("year").getAsInt())
                        : period.has("endDate")
                        ? period.get("endDate").getAsString().substring(0, 4) : "?";

                BigDecimal revenue = BigDecimal.ZERO;
                BigDecimal netIncome = BigDecimal.ZERO;
                if (period.has("report")) {
                    JsonObject report = period.getAsJsonObject("report");
                    if (report.has("ic")) {
                        JsonArray ic = report.getAsJsonArray("ic");
                        for (JsonElement line : ic) {
                            JsonObject item = line.getAsJsonObject();
                            String concept = item.has("concept") ? item.get("concept").getAsString() : "";
                            BigDecimal val = item.has("value")
                                    ? JsonParseUtil.asBigDecimal(item.get("value")) : BigDecimal.ZERO;
                            if (concept.contains("Revenue") && revenue.compareTo(BigDecimal.ZERO) == 0)
                                revenue = val;
                            if (concept.contains("NetIncome") || concept.contains("ProfitLoss"))
                                netIncome = val;
                        }
                    }
                }
                rows.add(YearlyFinancialRow.builder()
                        .fiscalYear(year)
                        .totalRevenue(revenue)
                        .netIncome(netIncome)
                        .currency("USD")
                        .build());
            }
        }

        rows.sort(Comparator.comparing(YearlyFinancialRow::getFiscalYear).reversed());
        if (rows.isEmpty() && profileOpt.isEmpty()) return Optional.empty();

        List<String> earningsNotes = new ArrayList<>();
        String earnUrl = "https://finnhub.io/api/v1/stock/earnings?symbol=" + sym + "&token=" + token;
        http.getJson(earnUrl).ifPresent(earn -> {
            if (earn.isJsonArray()) {
                JsonArray arr = earn.getAsJsonArray();
                for (int i = 0; i < Math.min(5, arr.size()); i++) {
                    JsonObject e = arr.get(i).getAsJsonObject();
                    earningsNotes.add(e.get("period").getAsString()
                            + " actual EPS " + e.get("actual").getAsString());
                }
            }
        });

        return Optional.of(FundamentalsReport.builder()
                .symbol(sym)
                .companyName(companyName)
                .sector(sector)
                .industry(industry)
                .country(country)
                .providerUsed(getProviderId().getLabel())
                .assetTypeLabel("Stock / Crypto / Forex")
                .yearlyRows(rows)
                .earningsNotes(earningsNotes)
                .summaryText(rows.isEmpty()
                        ? "Company profile loaded; limited annual financials from Finnhub."
                        : "Finnhub reported financials for " + sym + ".")
                .build());
    }
}
