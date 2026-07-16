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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Alpha Vantage implementation of {@link FundamentalsProvider}.
 *
 * <h3>Endpoints used</h3>
 * <ul>
 *   <li>{@code OVERVIEW}         — company name, sector, P/E, market cap, EPS, dividends</li>
 *   <li>{@code INCOME_STATEMENT} — annual revenue, gross profit, operating income, net income</li>
 *   <li>{@code BALANCE_SHEET}    — total assets, total debt, cash, current ratio</li>
 *   <li>{@code CASH_FLOW}        — free cash flow</li>
 *   <li>{@code EARNINGS}         — EPS history</li>
 *   <li>{@code REAL_GDP}         — macro indicator for FOREX</li>
 *   <li>{@code CPI}              — inflation macro indicator</li>
 *   <li>{@code FEDERAL_FUNDS_RATE} — US rate indicator</li>
 *   <li>{@code UNEMPLOYMENT}     — US unemployment macro</li>
 * </ul>
 *
 * <p>Rate limit: 5 req/min on free tier — shared throttle key {@code "alphavantage"}.</p>
 */
@Component
public class AlphaVantageFundamentalsProvider implements FundamentalsProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageFundamentalsProvider.class);

    public static final String PROVIDER_NAME = "ALPHA_VANTAGE";
    private static final String BASE_URL     = "https://www.alphavantage.co/query";
    static final String         THROTTLE     = "alphavantage";

    private final RefDataProviderProperties keys;
    private final RefDataHttpClient http;
    private final Gson gson = new Gson();

    public AlphaVantageFundamentalsProvider(RefDataProviderProperties keys,
                                             RefDataHttpClient http) {
        this.keys = keys;
        this.http = http;
    }

    @PostConstruct
    void init() {
        http.throttle(THROTTLE, 5, Duration.ofMinutes(1));
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK, AssetClass.FOREX);
    }

    // ── FundamentalsProvider ──────────────────────────────────────────────────

    @Override
    public Optional<CompanyFundamentalsDto> getCompanyFundamentals(String symbol) {
        if (!keys.hasAlphavantageKey()) return Optional.empty();
        String sym = symbol.toUpperCase().trim();

        Optional<JsonObject> overviewOpt = avRequest("OVERVIEW", sym);
        if (overviewOpt.isEmpty()) return Optional.empty();
        JsonObject ov = overviewOpt.get();

        // Income statement — latest annual report
        BigDecimal revenueAnnual   = null;
        BigDecimal revenueGrowth   = null;
        BigDecimal grossMargin     = null;
        BigDecimal operatingMargin = null;
        BigDecimal netMargin       = null;
        BigDecimal freeCashFlow    = null;
        BigDecimal totalAssets     = null;
        BigDecimal totalDebt       = null;
        BigDecimal cashAndEq       = null;
        BigDecimal currentRatio    = null;
        BigDecimal debtEquity      = null;

        Optional<JsonObject> incomeOpt = avRequest("INCOME_STATEMENT", sym);
        if (incomeOpt.isPresent()) {
            JsonObject income = incomeOpt.get();
            JsonArray annualReports = income.has("annualReports")
                    ? income.getAsJsonArray("annualReports") : null;
            if (annualReports != null && annualReports.size() >= 1) {
                JsonObject r0 = annualReports.get(0).getAsJsonObject();
                JsonObject r1 = annualReports.size() >= 2
                        ? annualReports.get(1).getAsJsonObject() : null;

                revenueAnnual = JsonUtil.bd(r0, "totalRevenue");
                BigDecimal prevRevenue = r1 != null ? JsonUtil.bd(r1, "totalRevenue") : null;
                if (revenueAnnual != null && prevRevenue != null
                        && prevRevenue.compareTo(BigDecimal.ZERO) != 0) {
                    revenueGrowth = revenueAnnual.subtract(prevRevenue)
                            .divide(prevRevenue.abs(), 4, RoundingMode.HALF_UP);
                }
                BigDecimal grossProfit = JsonUtil.bd(r0, "grossProfit");
                BigDecimal opIncome    = JsonUtil.bd(r0, "operatingIncome");
                BigDecimal netIncome   = JsonUtil.bd(r0, "netIncome");

                if (revenueAnnual != null && revenueAnnual.compareTo(BigDecimal.ZERO) != 0) {
                    if (grossProfit != null)
                        grossMargin = grossProfit.divide(revenueAnnual, 4, RoundingMode.HALF_UP);
                    if (opIncome != null)
                        operatingMargin = opIncome.divide(revenueAnnual, 4, RoundingMode.HALF_UP);
                    if (netIncome != null)
                        netMargin = netIncome.divide(revenueAnnual, 4, RoundingMode.HALF_UP);
                }
            }
        }

        Optional<JsonObject> balanceOpt = avRequest("BALANCE_SHEET", sym);
        if (balanceOpt.isPresent()) {
            JsonObject bs = balanceOpt.get();
            JsonArray annualBS = bs.has("annualReports") ? bs.getAsJsonArray("annualReports") : null;
            if (annualBS != null && !annualBS.isEmpty()) {
                JsonObject b0 = annualBS.get(0).getAsJsonObject();
                totalAssets = JsonUtil.bd(b0, "totalAssets");
                totalDebt   = nullCoalesce(JsonUtil.bd(b0, "longTermDebt"),
                                           JsonUtil.bd(b0, "totalLiabilities"));
                cashAndEq   = JsonUtil.bd(b0, "cashAndCashEquivalentsAtCarryingValue");
                BigDecimal curAssets = JsonUtil.bd(b0, "totalCurrentAssets");
                BigDecimal curLiab   = JsonUtil.bd(b0, "totalCurrentLiabilities");
                if (curAssets != null && curLiab != null && curLiab.compareTo(BigDecimal.ZERO) != 0)
                    currentRatio = curAssets.divide(curLiab, 4, RoundingMode.HALF_UP);
                BigDecimal equity = JsonUtil.bd(b0, "totalShareholderEquity");
                if (totalDebt != null && equity != null && equity.compareTo(BigDecimal.ZERO) != 0)
                    debtEquity = totalDebt.divide(equity, 4, RoundingMode.HALF_UP);
            }
        }

        Optional<JsonObject> cfOpt = avRequest("CASH_FLOW", sym);
        if (cfOpt.isPresent()) {
            JsonObject cf = cfOpt.get();
            JsonArray annualCF = cf.has("annualReports") ? cf.getAsJsonArray("annualReports") : null;
            if (annualCF != null && !annualCF.isEmpty()) {
                JsonObject c0 = annualCF.get(0).getAsJsonObject();
                BigDecimal opCF  = JsonUtil.bd(c0, "operatingCashflow");
                BigDecimal capEx = JsonUtil.bd(c0, "capitalExpenditures");
                if (opCF != null && capEx != null)
                    freeCashFlow = opCF.subtract(capEx);
            }
        }

        CompanyFundamentalsDto dto = CompanyFundamentalsDto.builder()
                .symbol(sym)
                .companyName(JsonUtil.str(ov, "Name"))
                .exchange(JsonUtil.str(ov, "Exchange"))
                .sector(JsonUtil.str(ov, "Sector"))
                .industry(JsonUtil.str(ov, "Industry"))
                .country(JsonUtil.str(ov, "Country"))
                .currency(JsonUtil.str(ov, "Currency"))
                .providerName(PROVIDER_NAME)
                .fetchDate(LocalDate.now())
                .marketCap(JsonUtil.bd(ov, "MarketCapitalization"))
                .peRatioTtm(JsonUtil.bd(ov, "PERatio"))
                .peRatioForward(JsonUtil.bd(ov, "ForwardPE"))
                .priceToBook(JsonUtil.bd(ov, "PriceToBookRatio"))
                .priceToSalesTtm(JsonUtil.bd(ov, "PriceToSalesRatioTTM"))
                .evToEbitda(JsonUtil.bd(ov, "EVToEBITDA"))
                .epsDilutedTtm(JsonUtil.bd(ov, "EPS"))
                .revenueAnnual(revenueAnnual)
                .revenueGrowthYoy(revenueGrowth)
                .grossMargin(grossMargin)
                .operatingMargin(operatingMargin)
                .netMargin(netMargin)
                .totalAssets(totalAssets)
                .totalDebt(totalDebt)
                .cashAndEquivalents(cashAndEq)
                .debtToEquity(debtEquity)
                .currentRatio(currentRatio)
                .freeCashFlowTtm(freeCashFlow)
                .dividendYield(JsonUtil.bd(ov, "DividendYield"))
                .dividendPerShare(JsonUtil.bd(ov, "DividendPerShare"))
                .analystCount(JsonUtil.integer(ov, "AnalystTargetPrice") != null ? null
                        : null) // AV doesn't expose analyst count in overview
                .targetPriceMean(JsonUtil.bd(ov, "AnalystTargetPrice"))
                .analystRecommendation(JsonUtil.str(ov, "RecommendationKey"))
                .build();

        return dto.getCompanyName() == null ? Optional.empty() : Optional.of(dto);
    }

    @Override
    public Optional<CryptoTokenomicsDto> getCryptoTokenomics(String symbol) {
        // AlphaVantage has limited crypto fundamental data — delegate to CoinGecko
        return Optional.empty();
    }

    @Override
    public Optional<ForexMacroIndicatorsDto> getForexMacroIndicators(String symbol) {
        if (!keys.hasAlphavantageKey()) return Optional.empty();
        // Parse pair like EUR/USD or just USD
        String[] parts = symbol.contains("/") ? symbol.split("/") : new String[]{symbol, "USD"};
        String base  = parts[0].toUpperCase().trim();
        String quote = parts.length > 1 ? parts[1].toUpperCase().trim() : "USD";

        // For US-centric data, fetch GDP, CPI, unemployment, Fed funds rate
        BigDecimal gdpGrowth  = fetchLatestMacroValue("REAL_GDP");
        BigDecimal cpi        = fetchLatestMacroValue("CPI");
        BigDecimal fedRate    = fetchLatestMacroValue("FEDERAL_FUNDS_RATE");
        BigDecimal unemploy   = fetchLatestMacroValue("UNEMPLOYMENT");

        ForexMacroIndicatorsDto dto = ForexMacroIndicatorsDto.builder()
                .symbol(symbol.toUpperCase())
                .baseCurrency(base)
                .quoteCurrency(quote)
                .country("US")
                .providerName(PROVIDER_NAME)
                .fetchDate(LocalDate.now())
                .centralBankRate(fedRate)
                .cpiYoy(cpi)
                .gdpGrowthYoy(gdpGrowth)
                .unemploymentRate(unemploy)
                .build();

        return Optional.of(dto);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    Optional<JsonObject> avRequest(String function, String symbol) {
        String url = BASE_URL + "?function=" + function + "&symbol=" + symbol
                + "&apikey=" + keys.getAlphavantageKey();
        return http.getJson(url, null, THROTTLE)
                .filter(root -> !root.has("Note") && !root.has("Information")
                        && !root.has("Error Message"));
    }

    private BigDecimal fetchLatestMacroValue(String function) {
        String url = BASE_URL + "?function=" + function + "&apikey=" + keys.getAlphavantageKey();
        return http.getJson(url, null, THROTTLE)
                .map(root -> {
                    JsonArray data = root.has("data") ? root.getAsJsonArray("data") : null;
                    if (data == null || data.isEmpty()) return null;
                    JsonObject latest = data.get(0).getAsJsonObject();
                    return JsonUtil.bd(latest, "value");
                })
                .orElse(null);
    }

    private static BigDecimal nullCoalesce(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }
}
