package com.mst.matt.tradingplatformapp.service.fundamental;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.fundamental.FundamentalsReport;
import com.mst.matt.tradingplatformapp.model.fundamental.YearlyFinancialRow;
import com.mst.matt.tradingplatformapp.service.price.HttpJsonClient;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;
import com.mst.matt.tradingplatformapp.service.price.SymbolNormalizer;
import com.mst.matt.tradingplatformapp.service.price.api.finnhub.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finnhub fundamental data service — free-tier integration.
 *
 * <h3>Free-tier fundamentals endpoints covered:</h3>
 * <ul>
 *   <li>{@code GET /stock/profile2}             — company profile</li>
 *   <li>{@code GET /stock/metric}               — basic financials (P/E, EPS, Beta, etc.)</li>
 *   <li>{@code GET /stock/earnings}             — earnings surprises (quarterly EPS)</li>
 *   <li>{@code GET /financials/reported}        — full reported financials (income/balance/cash)</li>
 *   <li>{@code GET /stock/recommendation}       — analyst recommendation trends</li>
 *   <li>{@code GET /stock/price-target}         — analyst price targets</li>
 *   <li>{@code GET /stock/dividend}             — dividend history</li>
 *   <li>{@code GET /stock/split}                — stock split history</li>
 *   <li>{@code GET /stock/peers}               — peer companies</li>
 *   <li>{@code GET /ratios}                    — financial ratios (P/B, ROE, debt/equity)</li>
 *   <li>{@code GET /stock/insider-transactions} — insider trades</li>
 *   <li>{@code GET /esg}                       — ESG scores</li>
 * </ul>
 *
 * <h3>Rate limiting:</h3>
 * All calls share the {@code "finnhub"} throttle (60 req/min free tier).
 *
 * <h3>Caching:</h3>
 * All responses are in-memory cached to stay within the free-tier limit.
 */
@Service
public class FinnhubFundamentalService implements FundamentalService {

    private static final Logger log = LoggerFactory.getLogger(FinnhubFundamentalService.class);

    private static final String BASE_URL    = "https://finnhub.io/api/v1";
    private static final String THROTTLE    = "finnhub";

    // ── Cache TTLs ──────────────────────────────────────────────────────────────
    private static final long PROFILE_TTL_MS    = 60 * 60_000L;   // 1 hour
    private static final long METRICS_TTL_MS    = 15 * 60_000L;   // 15 min
    private static final long EARNINGS_TTL_MS   = 60 * 60_000L;   // 1 hour
    private static final long FINANCIALS_TTL_MS = 60 * 60_000L;   // 1 hour
    private static final long RECOMMEND_TTL_MS  = 60 * 60_000L;   // 1 hour
    private static final long TARGET_TTL_MS     = 60 * 60_000L;   // 1 hour
    private static final long DIVIDEND_TTL_MS   = 60 * 60_000L;   // 1 hour
    private static final long SPLIT_TTL_MS      = 60 * 60_000L;   // 1 hour
    private static final long PEERS_TTL_MS      = 60 * 60_000L;   // 1 hour
    private static final long RATIOS_TTL_MS     = 60 * 60_000L;   // 1 hour
    private static final long INSIDER_TTL_MS    = 30 * 60_000L;   // 30 min
    private static final long ESG_TTL_MS        = 60 * 60_000L;   // 1 hour

    private final MarketApiProperties keys;
    private final HttpJsonClient http;
    private final Gson gson = new Gson();

    // ── In-memory caches ────────────────────────────────────────────────────────
    private final Map<String, CacheEntry<FinnhubCompanyProfile>>          profileCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubBasicFinancials>>         metricsCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubEarningsSurprise>>>  earningsCache  = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>                     financialsCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubRecommendationTrend>>> recCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubPriceTarget>>             targetCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubDividend>>>          dividendCache  = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubSplit>>>             splitCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<String>>>                   peersCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>                     ratiosCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubInsiderTransaction>>      insiderCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubEsgScore>>                esgCache       = new ConcurrentHashMap<>();

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

    // ─── Main fundamentals report ─────────────────────────────────────────────

    @Override
    public Optional<FundamentalsReport> fetchReport(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);

        Optional<FinnhubCompanyProfile> profileOpt = getCompanyProfile(sym);
        Optional<FinnhubBasicFinancials> metricsOpt = getBasicFinancials(sym);
        List<FinnhubEarningsSurprise> earningsList  = getEarningsSurprises(sym, 8);
        Optional<JsonObject> financialsOpt          = getReportedFinancials(sym, "annual");

        String companyName = sym;
        String sector      = "";
        String industry    = "";
        String country     = "";
        String currency    = "USD";

        if (profileOpt.isPresent()) {
            FinnhubCompanyProfile p = profileOpt.get();
            companyName = p.name() != null ? p.name() : sym;
            industry    = p.finnhubIndustry() != null ? p.finnhubIndustry() : "";
            country     = p.country() != null ? p.country() : "";
            currency    = p.currency() != null ? p.currency() : "USD";
            sector      = industry;
        }

        // ── Build yearly financial rows from reported financials ─────────────
        List<YearlyFinancialRow> rows = buildYearlyRows(financialsOpt, currency);

        // ── Build earnings notes ──────────────────────────────────────────────
        List<String> earningsNotes = new ArrayList<>();
        for (FinnhubEarningsSurprise e : earningsList) {
            if (earningsNotes.size() >= 5) break;
            earningsNotes.add(String.format("%s Q%d actual EPS %.2f (estimate %.2f, surprise %.2f%%)",
                    e.period() != null ? e.period() : "?",
                    e.quarter() != null ? e.quarter() : 0,
                    e.actual() != null ? e.actual() : 0.0,
                    e.estimate() != null ? e.estimate() : 0.0,
                    e.surprisePercent() != null ? e.surprisePercent() : 0.0));
        }

        // ── Enrich summary with metrics ───────────────────────────────────────
        String summaryText = buildSummaryText(sym, metricsOpt, rows);

        rows.sort(Comparator.comparing(YearlyFinancialRow::getFiscalYear).reversed());
        if (rows.isEmpty() && profileOpt.isEmpty()) return Optional.empty();

        return Optional.of(FundamentalsReport.builder()
                .symbol(sym)
                .companyName(companyName)
                .sector(sector)
                .industry(industry)
                .country(country)
                .providerUsed(getProviderId().getLabel())
                .assetTypeLabel("Stock")
                .yearlyRows(rows)
                .earningsNotes(earningsNotes)
                .summaryText(summaryText)
                .build());
    }

    // ─── Company Profile ──────────────────────────────────────────────────────

    /**
     * {@code GET /stock/profile2?symbol=...} — Company profile with logo, industry, etc.
     */
    public Optional<FinnhubCompanyProfile> getCompanyProfile(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubCompanyProfile> cached = profileCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/profile2", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE)
                .map(root -> gson.fromJson(root, FinnhubCompanyProfile.class))
                .filter(p -> p.ticker() != null && !p.ticker().isBlank())
                .map(result -> {
                    profileCache.put(sym, new CacheEntry<>(result, PROFILE_TTL_MS));
                    return result;
                });
    }

    // ─── Basic Financials / Metrics ───────────────────────────────────────────

    /**
     * {@code GET /stock/metric?symbol=...&metric=all} — Key financial metrics.
     * Returns P/E, EPS, beta, 52-week high/low, market cap, dividend yield, etc.
     */
    public Optional<FinnhubBasicFinancials> getBasicFinancials(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubBasicFinancials> cached = metricsCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/metric", "symbol=" + sym + "&metric=all");
        return http.getJson(url, null, THROTTLE)
                .map(root -> gson.fromJson(root, FinnhubBasicFinancials.class))
                .filter(bf -> bf.symbol() != null)
                .map(result -> {
                    metricsCache.put(sym, new CacheEntry<>(result, METRICS_TTL_MS));
                    return result;
                });
    }

    // ─── Earnings Surprises ───────────────────────────────────────────────────

    /**
     * {@code GET /stock/earnings?symbol=...&limit=...} — Quarterly EPS surprises.
     */
    public List<FinnhubEarningsSurprise> getEarningsSurprises(String symbol, int limit) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + limit;
        CacheEntry<List<FinnhubEarningsSurprise>> cached = earningsCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        int lim = limit > 0 ? limit : 4;
        String url = buildUrl("/stock/earnings", "symbol=" + sym + "&limit=" + lim);
        return http.getJson(url, null, THROTTLE)
                .map(root -> {
                    List<FinnhubEarningsSurprise> list = new ArrayList<>();
                    if (root.isJsonArray()) {
                        root.getAsJsonArray().forEach(el -> {
                            if (el.isJsonObject())
                                list.add(gson.fromJson(el, FinnhubEarningsSurprise.class));
                        });
                    }
                    return list;
                })
                .map(list -> {
                    earningsCache.put(key, new CacheEntry<>(list, EARNINGS_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Reported Financials ──────────────────────────────────────────────────

    /**
     * {@code GET /financials/reported?symbol=...&freq=...} — Full reported financials.
     * Returns income statement, balance sheet, and cash flow for each period.
     *
     * @param symbol stock ticker
     * @param freq   "annual" or "quarterly"
     */
    public Optional<JsonObject> getReportedFinancials(String symbol, String freq) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + freq;
        CacheEntry<JsonObject> cached = financialsCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String f = (freq != null && !freq.isBlank()) ? freq : "annual";
        // Finnhub uses stock/financials-reported (older path) and also financials/reported
        String url = buildUrl("/stock/financials-reported", "symbol=" + sym + "&freq=" + f);
        return http.getJson(url, null, THROTTLE)
                .map(result -> {
                    financialsCache.put(key, new CacheEntry<>(result, FINANCIALS_TTL_MS));
                    return result;
                });
    }

    // ─── Recommendation Trends ────────────────────────────────────────────────

    /**
     * {@code GET /stock/recommendation?symbol=...} — Analyst recommendation trends.
     */
    public List<FinnhubRecommendationTrend> getRecommendationTrends(String symbol) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<List<FinnhubRecommendationTrend>> cached = recCache.get(sym);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("/stock/recommendation", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE)
                .map(root -> {
                    List<FinnhubRecommendationTrend> list = new ArrayList<>();
                    if (root.isJsonArray()) {
                        root.getAsJsonArray().forEach(el -> {
                            if (el.isJsonObject())
                                list.add(gson.fromJson(el, FinnhubRecommendationTrend.class));
                        });
                    }
                    return list;
                })
                .map(list -> {
                    recCache.put(sym, new CacheEntry<>(list, RECOMMEND_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Price Target ─────────────────────────────────────────────────────────

    /**
     * {@code GET /stock/price-target?symbol=...} — Analyst consensus price target.
     */
    public Optional<FinnhubPriceTarget> getPriceTarget(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubPriceTarget> cached = targetCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/price-target", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE)
                .map(root -> gson.fromJson(root, FinnhubPriceTarget.class))
                .filter(pt -> pt.symbol() != null)
                .map(result -> {
                    targetCache.put(sym, new CacheEntry<>(result, TARGET_TTL_MS));
                    return result;
                });
    }

    // ─── Dividends ────────────────────────────────────────────────────────────

    /**
     * {@code GET /stock/dividend?symbol=...&from=...&to=...} — Dividend history.
     */
    public List<FinnhubDividend> getDividends(String symbol, String from, String to) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + from + "|" + to;
        CacheEntry<List<FinnhubDividend>> cached = dividendCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        String params = "symbol=" + sym + "&from=" + from + "&to=" + to;
        String url = buildUrl("/stock/dividend", params);
        return http.getJson(url, null, THROTTLE)
                .map(root -> {
                    List<FinnhubDividend> list = new ArrayList<>();
                    if (root.isJsonArray()) {
                        root.getAsJsonArray().forEach(el -> {
                            if (el.isJsonObject())
                                list.add(gson.fromJson(el, FinnhubDividend.class));
                        });
                    }
                    return list;
                })
                .map(list -> {
                    dividendCache.put(key, new CacheEntry<>(list, DIVIDEND_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Stock Splits ─────────────────────────────────────────────────────────

    /**
     * {@code GET /stock/split?symbol=...&from=...&to=...} — Stock split history.
     */
    public List<FinnhubSplit> getSplits(String symbol, String from, String to) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + from + "|" + to;
        CacheEntry<List<FinnhubSplit>> cached = splitCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        String params = "symbol=" + sym + "&from=" + from + "&to=" + to;
        String url = buildUrl("/stock/split", params);
        return http.getJson(url, null, THROTTLE)
                .map(root -> {
                    List<FinnhubSplit> list = new ArrayList<>();
                    if (root.isJsonArray()) {
                        root.getAsJsonArray().forEach(el -> {
                            if (el.isJsonObject())
                                list.add(gson.fromJson(el, FinnhubSplit.class));
                        });
                    }
                    return list;
                })
                .map(list -> {
                    splitCache.put(key, new CacheEntry<>(list, SPLIT_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Peers ────────────────────────────────────────────────────────────────

    /**
     * {@code GET /stock/peers?symbol=...} — Peer company symbols.
     */
    public List<String> getPeers(String symbol) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<List<String>> cached = peersCache.get(sym);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("/stock/peers", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE)
                .map(root -> {
                    List<String> list = new ArrayList<>();
                    if (root.isJsonArray()) {
                        root.getAsJsonArray().forEach(el -> {
                            if (el.isJsonPrimitive()) list.add(el.getAsString());
                        });
                    }
                    return list;
                })
                .map(list -> {
                    peersCache.put(sym, new CacheEntry<>(list, PEERS_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Financial Ratios ────────────────────────────────────────────────────

    /**
     * {@code GET /ratios?symbol=...} — Historical financial ratios (P/E, P/B, ROE, etc.).
     */
    public Optional<JsonObject> getFinancialRatios(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<JsonObject> cached = ratiosCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/ratios", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE)
                .map(result -> {
                    ratiosCache.put(sym, new CacheEntry<>(result, RATIOS_TTL_MS));
                    return result;
                });
    }

    // ─── Insider Transactions ─────────────────────────────────────────────────

    /**
     * {@code GET /stock/insider-transactions?symbol=...} — Insider buy/sell transactions.
     */
    public Optional<FinnhubInsiderTransaction> getInsiderTransactions(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubInsiderTransaction> cached = insiderCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/insider-transactions", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE)
                .map(root -> gson.fromJson(root, FinnhubInsiderTransaction.class))
                .map(result -> {
                    insiderCache.put(sym, new CacheEntry<>(result, INSIDER_TTL_MS));
                    return result;
                });
    }

    // ─── ESG Scores ───────────────────────────────────────────────────────────

    /**
     * {@code GET /esg?symbol=...} — ESG environmental/social/governance scores.
     */
    public Optional<FinnhubEsgScore> getEsgScores(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubEsgScore> cached = esgCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/esg", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE)
                .map(root -> gson.fromJson(root, FinnhubEsgScore.class))
                .filter(e -> e.symbol() != null)
                .map(result -> {
                    esgCache.put(sym, new CacheEntry<>(result, ESG_TTL_MS));
                    return result;
                });
    }

    // ─── URL builder ──────────────────────────────────────────────────────────

    public String buildUrl(String path, String params) {
        String token = keys.getFinnhubKey();
        StringBuilder sb = new StringBuilder(BASE_URL).append(path).append("?");
        if (params != null && !params.isBlank()) {
            sb.append(params).append("&");
        }
        sb.append("token=").append(token);
        return sb.toString();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<YearlyFinancialRow> buildYearlyRows(Optional<JsonObject> financialsOpt, String currency) {
        List<YearlyFinancialRow> rows = new ArrayList<>();
        if (financialsOpt.isEmpty()) return rows;

        JsonObject fin = financialsOpt.get();
        JsonArray data = fin.has("data") ? fin.getAsJsonArray("data") : new JsonArray();

        for (JsonElement periodEl : data) {
            if (!periodEl.isJsonObject()) continue;
            JsonObject period = periodEl.getAsJsonObject();
            String year = period.has("year")
                    ? String.valueOf(period.get("year").getAsInt())
                    : period.has("endDate")
                    ? period.get("endDate").getAsString().substring(0, 4) : "?";

            BigDecimal revenue    = BigDecimal.ZERO;
            BigDecimal netIncome  = BigDecimal.ZERO;
            BigDecimal totalAssets = BigDecimal.ZERO;
            BigDecimal opCashFlow  = BigDecimal.ZERO;

            if (period.has("report")) {
                JsonObject report = period.getAsJsonObject("report");
                // Income statement
                if (report.has("ic")) {
                    for (JsonElement lineEl : report.getAsJsonArray("ic")) {
                        if (!lineEl.isJsonObject()) continue;
                        JsonObject item = lineEl.getAsJsonObject();
                        String concept = item.has("concept") ? item.get("concept").getAsString() : "";
                        BigDecimal val = item.has("value")
                                ? JsonParseUtil.asBigDecimal(item.get("value")) : BigDecimal.ZERO;
                        if (concept.contains("Revenue") && revenue.compareTo(BigDecimal.ZERO) == 0)
                            revenue = val;
                        if (concept.contains("NetIncome") || concept.contains("ProfitLoss"))
                            netIncome = val;
                    }
                }
                // Balance sheet
                if (report.has("bs")) {
                    for (JsonElement lineEl : report.getAsJsonArray("bs")) {
                        if (!lineEl.isJsonObject()) continue;
                        JsonObject item = lineEl.getAsJsonObject();
                        String concept = item.has("concept") ? item.get("concept").getAsString() : "";
                        BigDecimal val = item.has("value")
                                ? JsonParseUtil.asBigDecimal(item.get("value")) : BigDecimal.ZERO;
                        if (concept.contains("Assets") && !concept.contains("Current")
                                && totalAssets.compareTo(BigDecimal.ZERO) == 0)
                            totalAssets = val;
                    }
                }
                // Cash flow
                if (report.has("cf")) {
                    for (JsonElement lineEl : report.getAsJsonArray("cf")) {
                        if (!lineEl.isJsonObject()) continue;
                        JsonObject item = lineEl.getAsJsonObject();
                        String concept = item.has("concept") ? item.get("concept").getAsString() : "";
                        BigDecimal val = item.has("value")
                                ? JsonParseUtil.asBigDecimal(item.get("value")) : BigDecimal.ZERO;
                        if (concept.contains("OperatingActivities") && opCashFlow.compareTo(BigDecimal.ZERO) == 0)
                            opCashFlow = val;
                    }
                }
            }

            rows.add(YearlyFinancialRow.builder()
                    .fiscalYear(year)
                    .totalRevenue(revenue)
                    .netIncome(netIncome)
                    .currency(currency)
                    .build());
        }
        return rows;
    }

    private String buildSummaryText(String sym,
                                    Optional<FinnhubBasicFinancials> metricsOpt,
                                    List<YearlyFinancialRow> rows) {
        StringBuilder sb = new StringBuilder("Finnhub fundamentals for ").append(sym).append(". ");
        metricsOpt.ifPresent(bf -> {
            if (bf.metric() != null) {
                JsonObject m = bf.metric();
                if (m.has("peNormalizedAnnual"))
                    sb.append("P/E: ").append(m.get("peNormalizedAnnual").getAsString()).append(". ");
                if (m.has("epsNormalizedAnnual"))
                    sb.append("EPS: ").append(m.get("epsNormalizedAnnual").getAsString()).append(". ");
                if (m.has("marketCapitalization"))
                    sb.append("Market Cap: ").append(m.get("marketCapitalization").getAsString()).append("M. ");
                if (m.has("52WeekHigh"))
                    sb.append("52W High: ").append(m.get("52WeekHigh").getAsString()).append(". ");
                if (m.has("52WeekLow"))
                    sb.append("52W Low: ").append(m.get("52WeekLow").getAsString()).append(". ");
                if (m.has("beta"))
                    sb.append("Beta: ").append(m.get("beta").getAsString()).append(". ");
            }
        });
        if (!rows.isEmpty()) {
            sb.append("Financial data available for ").append(rows.size()).append(" year(s).");
        }
        return sb.toString();
    }

    // ─── Cache helper ──────────────────────────────────────────────────────────

    private static final class CacheEntry<T> {
        final T    value;
        final long expiresAt;

        CacheEntry(T value, long ttlMs) {
            this.value     = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }
}
