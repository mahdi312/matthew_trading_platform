package com.mst.matt.referencedataservice.provider.fundamentals;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.CompanyFundamentalsDto;
import com.mst.matt.contracts.provider.dto.CryptoTokenomicsDto;
import com.mst.matt.contracts.provider.dto.ForexMacroIndicatorsDto;
import com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider;
import com.mst.matt.referencedataservice.client.JsonUtil;
import com.mst.matt.referencedataservice.client.RefDataHttpClient;
import com.mst.matt.referencedataservice.config.RefDataProviderProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Finnhub implementation of {@link FundamentalsProvider} for equities.
 *
 * <h3>Endpoints used</h3>
 * <ul>
 *   <li>{@code GET /stock/profile2}          — company name, exchange, country, currency</li>
 *   <li>{@code GET /stock/metric?metric=all} — P/E, EPS, beta, market cap, dividend yield</li>
 *   <li>{@code GET /stock/price-target}      — analyst consensus target price</li>
 *   <li>{@code GET /stock/recommendation}    — analyst recommendation trend</li>
 *   <li>{@code GET /stock/financials-reported?freq=annual} — income, balance, cash flow</li>
 * </ul>
 *
 * <p>Rate limit: 60 req/min — throttle key {@code "finnhub"}.</p>
 */
@Component
public class FinnhubFundamentalsProvider implements FundamentalsProvider {

    public static final String PROVIDER_NAME = "FINNHUB";
    private static final String BASE_URL     = "https://finnhub.io/api/v1";
    static final String         THROTTLE     = "finnhub";

    private final RefDataProviderProperties keys;
    private final RefDataHttpClient http;
    private final Gson gson = new Gson();

    public FinnhubFundamentalsProvider(RefDataProviderProperties keys,
                                        RefDataHttpClient http) {
        this.keys = keys;
        this.http = http;
    }

    @PostConstruct
    void init() {
        http.throttle(THROTTLE, 60, Duration.ofMinutes(1));
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK);
    }

    // ── FundamentalsProvider ──────────────────────────────────────────────────

    @Override
    public Optional<CompanyFundamentalsDto> getCompanyFundamentals(String symbol) {
        if (!keys.hasFinnhubKey()) return Optional.empty();
        String sym = symbol.toUpperCase().trim();

        // 1. Profile
        Optional<JsonObject> profileOpt = fhGet("/stock/profile2", "symbol=" + sym);
        if (profileOpt.isEmpty()) return Optional.empty();
        JsonObject profile = profileOpt.get();

        String companyName = JsonUtil.str(profile, "name");
        if (companyName == null) return Optional.empty();

        String exchange  = JsonUtil.str(profile, "exchange");
        String country   = JsonUtil.str(profile, "country");
        String currency  = JsonUtil.str(profile, "currency");
        String industry  = JsonUtil.str(profile, "finnhubIndustry");
        BigDecimal mktCap = JsonUtil.bd(profile, "marketCapitalization");
        // Finnhub returns market cap in millions
        if (mktCap != null) mktCap = mktCap.multiply(BigDecimal.valueOf(1_000_000L));

        // 2. Basic financials / metrics
        BigDecimal peRatio       = null;
        BigDecimal eps           = null;
        BigDecimal priceToBook   = null;
        BigDecimal divYield      = null;
        BigDecimal evEbitda      = null;
        BigDecimal priceToSales  = null;

        Optional<JsonObject> metricsOpt = fhGet("/stock/metric", "symbol=" + sym + "&metric=all");
        if (metricsOpt.isPresent()) {
            JsonObject metrics = metricsOpt.get();
            JsonObject m = metrics.has("metric") ? metrics.getAsJsonObject("metric") : metrics;
            peRatio      = JsonUtil.bd(m, "peExclExtraTTM");
            eps          = JsonUtil.bd(m, "epsBasicExclExtraItemsTTM");
            priceToBook  = JsonUtil.bd(m, "pbAnnual");
            divYield     = JsonUtil.bd(m, "dividendYieldIndicatedAnnual");
            evEbitda     = JsonUtil.bd(m, "enterpriseValueOverEBITDA");
            priceToSales = JsonUtil.bd(m, "psAnnual");
        }

        // 3. Price target
        BigDecimal targetPrice = null;
        Optional<JsonObject> targetOpt = fhGet("/stock/price-target", "symbol=" + sym);
        if (targetOpt.isPresent()) {
            targetPrice = JsonUtil.bd(targetOpt.get(), "targetMean");
        }

        // 4. Analyst recommendation — use latest trend entry
        String analystRec  = null;
        Optional<JsonObject> recOpt = fhGet("/stock/recommendation", "symbol=" + sym);
        if (recOpt.isPresent() && recOpt.get().has("result")) {
            // endpoint can return array wrapped or bare
        }
        // Finnhub recommendation endpoint returns JSON array at root — use element fetch
        analystRec = fetchRecommendationLabel(sym);

        // 5. Reported financials — extract revenue, net income, debt, cash
        BigDecimal revenueAnnual   = null;
        BigDecimal grossMargin     = null;
        BigDecimal operatingMargin = null;
        BigDecimal netMargin       = null;
        BigDecimal totalAssets     = null;
        BigDecimal totalDebt       = null;
        BigDecimal cashAndEq       = null;
        BigDecimal freeCashFlow    = null;

        Optional<JsonObject> finOpt = fhGet("/stock/financials-reported",
                "symbol=" + sym + "&freq=annual");
        if (finOpt.isPresent()) {
            JsonObject fin = finOpt.get();
            JsonArray data = fin.has("data") ? fin.getAsJsonArray("data") : new JsonArray();
            if (!data.isEmpty()) {
                JsonObject period = data.get(0).getAsJsonObject();
                if (period.has("report")) {
                    JsonObject report = period.getAsJsonObject("report");
                    // Income statement
                    if (report.has("ic")) {
                        BigDecimal rev = null, netInc = null, gp = null, opInc = null;
                        for (JsonElement lineEl : report.getAsJsonArray("ic")) {
                            if (!lineEl.isJsonObject()) continue;
                            JsonObject item = lineEl.getAsJsonObject();
                            String concept = JsonUtil.str(item, "concept");
                            BigDecimal val = JsonUtil.bd(item, "value");
                            if (concept == null || val == null) continue;
                            if (concept.contains("Revenue") && rev == null) rev = val;
                            if ((concept.contains("NetIncome") || concept.contains("ProfitLoss"))
                                    && netInc == null) netInc = val;
                            if (concept.contains("GrossProfit") && gp == null) gp = val;
                            if (concept.contains("OperatingIncome") && opInc == null) opInc = val;
                        }
                        revenueAnnual = rev;
                        if (rev != null && rev.compareTo(BigDecimal.ZERO) != 0) {
                            if (gp != null) grossMargin = gp.divide(rev, 4, RoundingMode.HALF_UP);
                            if (opInc != null) operatingMargin = opInc.divide(rev, 4, RoundingMode.HALF_UP);
                            if (netInc != null) netMargin = netInc.divide(rev, 4, RoundingMode.HALF_UP);
                        }
                    }
                    // Balance sheet
                    if (report.has("bs")) {
                        for (JsonElement lineEl : report.getAsJsonArray("bs")) {
                            if (!lineEl.isJsonObject()) continue;
                            JsonObject item = lineEl.getAsJsonObject();
                            String concept = JsonUtil.str(item, "concept");
                            BigDecimal val = JsonUtil.bd(item, "value");
                            if (concept == null || val == null) continue;
                            if (concept.contains("Assets") && totalAssets == null) totalAssets = val;
                            if (concept.contains("LongTermDebt") && totalDebt == null) totalDebt = val;
                            if (concept.contains("CashAndCash") && cashAndEq == null) cashAndEq = val;
                        }
                    }
                    // Cash flow
                    if (report.has("cf")) {
                        BigDecimal opCF = null, capEx = null;
                        for (JsonElement lineEl : report.getAsJsonArray("cf")) {
                            if (!lineEl.isJsonObject()) continue;
                            JsonObject item = lineEl.getAsJsonObject();
                            String concept = JsonUtil.str(item, "concept");
                            BigDecimal val = JsonUtil.bd(item, "value");
                            if (concept == null || val == null) continue;
                            if (concept.contains("OperatingActivities") && opCF == null) opCF = val;
                            if (concept.contains("CapitalExpenditure") && capEx == null) capEx = val;
                        }
                        if (opCF != null && capEx != null) freeCashFlow = opCF.subtract(capEx);
                    }
                }
            }
        }

        return Optional.of(CompanyFundamentalsDto.builder()
                .symbol(sym)
                .companyName(companyName)
                .exchange(exchange)
                .sector(industry)
                .industry(industry)
                .country(country)
                .currency(currency)
                .providerName(PROVIDER_NAME)
                .fetchDate(LocalDate.now())
                .marketCap(mktCap)
                .peRatioTtm(peRatio)
                .priceToBook(priceToBook)
                .priceToSalesTtm(priceToSales)
                .evToEbitda(evEbitda)
                .epsDilutedTtm(eps)
                .revenueAnnual(revenueAnnual)
                .grossMargin(grossMargin)
                .operatingMargin(operatingMargin)
                .netMargin(netMargin)
                .totalAssets(totalAssets)
                .totalDebt(totalDebt)
                .cashAndEquivalents(cashAndEq)
                .freeCashFlowTtm(freeCashFlow)
                .dividendYield(divYield)
                .targetPriceMean(targetPrice)
                .analystRecommendation(analystRec)
                .build());
    }

    @Override
    public Optional<CryptoTokenomicsDto> getCryptoTokenomics(String symbol) {
        return Optional.empty(); // Finnhub crypto fundamentals not on free tier
    }

    @Override
    public Optional<ForexMacroIndicatorsDto> getForexMacroIndicators(String symbol) {
        return Optional.empty(); // Finnhub forex macro not on free tier
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    Optional<JsonObject> fhGet(String path, String params) {
        String url = BASE_URL + path + "?token=" + keys.getFinnhubKey()
                + (params != null && !params.isBlank() ? "&" + params : "");
        return http.getJson(url, null, THROTTLE);
    }

    private String fetchRecommendationLabel(String sym) {
        String url = BASE_URL + "/stock/recommendation?symbol=" + sym
                + "&token=" + keys.getFinnhubKey();
        return http.getJsonElement(url, null, THROTTLE)
                .map(el -> {
                    if (!el.isJsonArray()) return null;
                    JsonArray arr = el.getAsJsonArray();
                    if (arr.isEmpty()) return null;
                    JsonObject latest = arr.get(0).getAsJsonObject();
                    // strongest recommendation wins
                    int strongBuy  = latest.has("strongBuy")  ? latest.get("strongBuy").getAsInt() : 0;
                    int buy        = latest.has("buy")         ? latest.get("buy").getAsInt() : 0;
                    int hold       = latest.has("hold")        ? latest.get("hold").getAsInt() : 0;
                    int sell       = latest.has("sell")        ? latest.get("sell").getAsInt() : 0;
                    int strongSell = latest.has("strongSell")  ? latest.get("strongSell").getAsInt() : 0;
                    int bullish    = strongBuy + buy;
                    int bearish    = sell + strongSell;
                    if (bullish > bearish && bullish > hold) return "BUY";
                    if (bearish > bullish && bearish > hold) return "SELL";
                    return "HOLD";
                })
                .orElse(null);
    }
}
