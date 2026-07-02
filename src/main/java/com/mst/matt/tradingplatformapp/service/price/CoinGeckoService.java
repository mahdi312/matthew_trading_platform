package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoFullCoin;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoMarketChart;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoMarketCoin;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoSimplePrice;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * CoinGecko service — broader crypto coverage than Binance alone.
 *
 * Free demo API (no credit card, just register for key).
 * Endpoints used:
 *   /simple/price           → current price + 24h change
 *   /coins/{id}/ohlc        → OHLCV data (limited history on free tier)
 *   /coins/markets          → full market data for multiple coins
 */
@Service
public class CoinGeckoService implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoService.class);

    @Value("${api.coingecko.base-url:https://api.coingecko.com/api/v3}")
    private String baseUrl;

    @Value("${api.coingecko-key:}")
    private String apiKey;

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    // Map from trading symbols to CoinGecko coin IDs
    private static final Map<String, String> SYMBOL_TO_ID = Map.ofEntries(
            Map.entry("BTC",  "bitcoin"),
            Map.entry("ETH",  "ethereum"),
            Map.entry("BNB",  "binancecoin"),
            Map.entry("SOL",  "solana"),
            Map.entry("ADA",  "cardano"),
            Map.entry("XRP",  "ripple"),
            Map.entry("DOT",  "polkadot"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("MATIC","matic-network"),
            Map.entry("LINK", "chainlink"),
            Map.entry("LTC",  "litecoin"),
            Map.entry("UNI",  "uniswap"),
            Map.entry("ATOM", "cosmos"),
            Map.entry("XLM",  "stellar"),
            Map.entry("NEAR", "near"),
            Map.entry("ALGO", "algorand"),
            Map.entry("VET",  "vechain"),
            Map.entry("FIL",  "filecoin"),
            Map.entry("TRX",  "tron")
    );

    public CoinGeckoService(@Autowired @Qualifier("priceHttpClient") OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        String coinId = toCoinId(symbol);
        if (coinId == null) return Optional.empty();
        String display = symbol.toUpperCase();

        Optional<PriceQuote> fromMarkets = fetchFromMarkets(coinId, display);
        if (fromMarkets.isPresent()) return fromMarkets;
        return fetchFromSimplePrice(coinId, display);
    }

    private Optional<PriceQuote> fetchFromMarkets(String coinId, String display) {
        String url = baseUrl + "/coins/markets"
                + "?vs_currency=usd"
                + "&ids=" + coinId
                + "&order=market_cap_desc"
                + "&sparkline=false"
                + "&price_change_percentage=24h";
        try (Response response = httpClient.newCall(buildRequest(url)).execute()) {
            if (!response.isSuccessful() || response.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(response.body().string(), JsonArray.class);
            if (arr == null || arr.isEmpty()) return Optional.empty();
            return CoinGeckoMarketCoin.fromJson(arr.get(0).getAsJsonObject())
                    .map(c -> c.toPriceQuote(display));
        } catch (IOException e) {
            log.warn("CoinGecko markets error for {}: {}", display, e.getMessage());
            return Optional.empty();
        }
    }

    /** Lighter endpoint from report.html when markets is rate-limited. */
    private Optional<PriceQuote> fetchFromSimplePrice(String coinId, String display) {
        String url = baseUrl + "/simple/price?ids=" + coinId
                + "&vs_currencies=usd&include_24hr_change=true&include_market_cap=true";
        try (Response response = httpClient.newCall(buildRequest(url)).execute()) {
            if (!response.isSuccessful() || response.body() == null) return Optional.empty();
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null || !root.has(coinId)) return Optional.empty();
            return CoinGeckoSimplePrice.fromCoinNode(root.getAsJsonObject(coinId))
                    .map(p -> p.toPriceQuote(display, display));
        } catch (IOException e) {
            log.error("CoinGecko error for {}: {}", display, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String coinId = toCoinId(symbol);
        if (coinId == null) return Collections.emptyList();

        // CoinGecko OHLC endpoint: days param drives how much history
        int days = toDays(timeframe, limit);
        String url = baseUrl + "/coins/" + coinId
                + "/ohlc?vs_currency=usd&days=" + days;

        Request request = buildRequest(url);
        List<OhlcvBar> bars = new ArrayList<>();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return bars;

            JsonArray arr = gson.fromJson(response.body().string(), JsonArray.class);

            for (JsonElement el : arr) {
                JsonArray row = el.getAsJsonArray();
                long ts = row.get(0).getAsLong();

                OhlcvBar bar = OhlcvBar.builder()
                        .symbol(symbol.toUpperCase())
                        .timeframe(timeframe)
                        .openTime(LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(ts), ZoneOffset.UTC))
                        .open(new BigDecimal(row.get(1).getAsString()))
                        .high(new BigDecimal(row.get(2).getAsString()))
                        .low(new BigDecimal(row.get(3).getAsString()))
                        .close(new BigDecimal(row.get(4).getAsString()))
                        .volume(BigDecimal.ZERO) // CoinGecko OHLC has no volume column
                        .assetType(AssetType.CRYPTO)
                        .build();

                bars.add(bar);
            }

        } catch (IOException e) {
            log.error("CoinGecko OHLCV error for {}: {}", symbol, e.getMessage());
        }

        // Trim to requested limit
        if (bars.size() > limit)
            bars = bars.subList(bars.size() - limit, bars.size());

        return bars;
    }

    @Override
    public boolean supports(String symbol) {
        return toCoinId(symbol.toUpperCase()) != null
                || toCoinId(stripSuffix(symbol.toUpperCase())) != null;
    }

    @Override
    public String getProviderName() { return "CoinGecko"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.COINGECKO; }

    // ── CoinGecko endpoint wrappers (raw JSON helpers for free endpoints) ──

    // --- Simple ---
    public Optional<JsonObject> getSimplePrice(String idsCsv, String vsCurrency,
                                               boolean includeMarketCap, boolean include24hChange,
                                               boolean includeLastUpdatedAt) {
        String url = baseUrl + "/simple/price?ids=" + urlEncode(idsCsv)
                + "&vs_currencies=" + urlEncode(vsCurrency)
                + "&include_market_cap=" + includeMarketCap
                + "&include_24hr_vol=" + include24hChange
                + "&include_24hr_change=" + include24hChange
                + "&include_last_updated_at=" + includeLastUpdatedAt;
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko getSimplePrice error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<String> getSupportedVsCurrencies() {
        String url = baseUrl + "/simple/supported_vs_currencies";
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Collections.emptyList();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            List<String> res = new ArrayList<>();
            if (arr != null) for (JsonElement e : arr) res.add(e.getAsString());
            return res;
        } catch (IOException e) {
            log.error("CoinGecko supported currencies error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<JsonObject> getSimpleTokenPrice(String platform, String contractAddressesCsv, String vsCurrencies,
                                                    boolean includeMarketCap, boolean include24hChange,
                                                    boolean includeLastUpdatedAt) {
        String url = baseUrl + "/simple/token_price/" + urlEncode(platform)
                + "?contract_addresses=" + urlEncode(contractAddressesCsv)
                + "&vs_currencies=" + urlEncode(vsCurrencies)
                + "&include_market_cap=" + includeMarketCap
                + "&include_24hr_vol=" + include24hChange
                + "&include_24hr_change=" + include24hChange
                + "&include_last_updated_at=" + includeLastUpdatedAt;
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko token price error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Coins ---
    public Optional<JsonArray> getCoinsMarkets(String vsCurrency, String idsCsv, String order, int perPage, int page,
                                               boolean sparkline, String priceChangePercentage) {
        String url = baseUrl + "/coins/markets?vs_currency=" + urlEncode(vsCurrency)
                + (idsCsv == null || idsCsv.isBlank() ? "" : "&ids=" + urlEncode(idsCsv))
                + "&order=" + urlEncode(order)
                + "&per_page=" + perPage
                + "&page=" + page
                + "&sparkline=" + sparkline
                + (priceChangePercentage == null ? "" : "&price_change_percentage=" + urlEncode(priceChangePercentage));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko coins/markets error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinById(String id, boolean localization, boolean marketData,
                                            boolean communityData, boolean developerData, boolean sparkline) {
        String url = baseUrl + "/coins/" + urlEncode(id)
                + "?localization=" + localization
                + "&market_data=" + marketData
                + "&community_data=" + communityData
                + "&developer_data=" + developerData
                + "&sparkline=" + sparkline;
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko coin by id error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinHistory(String id, String date, boolean localization) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/history?date=" + urlEncode(date)
                + "&localization=" + localization;
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko coin history error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinMarketChart(String id, String vsCurrency, String days, String interval) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/market_chart?vs_currency=" + urlEncode(vsCurrency)
                + "&days=" + urlEncode(days)
                + (interval == null ? "" : "&interval=" + urlEncode(interval));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko market chart error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinMarketChartRange(String id, String vsCurrency, long from, long to) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/market_chart/range?vs_currency=" + urlEncode(vsCurrency)
                + "&from=" + from + "&to=" + to;
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko market chart range error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinTickers(String id, String exchangeIds, boolean includeExchangeLogo, String order, int page) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/tickers"
                + (exchangeIds == null || exchangeIds.isBlank() ? "" : "?exchange_ids=" + urlEncode(exchangeIds))
                + "&include_exchange_logo=" + includeExchangeLogo
                + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko coin tickers error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getCoinOhlcRange(String id, String vsCurrency, long from, long to) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/ohlc?vs_currency=" + urlEncode(vsCurrency)
                + "&days=1"; // CoinGecko OHLC uses days param; range OHLC not universally available
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko coin ohlc error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Contract ---
    public Optional<JsonObject> getTokenByContract(String platform, String contractAddress,
                                                   boolean localization, boolean marketData,
                                                   boolean communityData, boolean developerData,
                                                   boolean sparkline) {
        String url = baseUrl + "/coins/" + urlEncode(platform) + "/contract/" + urlEncode(contractAddress)
                + "?localization=" + localization
                + "&market_data=" + marketData
                + "&community_data=" + communityData
                + "&developer_data=" + developerData
                + "&sparkline=" + sparkline;
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko contract token error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getTokenMarketChartByContract(String platform, String contractAddress,
                                                              String vsCurrency, String days, String interval) {
        String url = baseUrl + "/coins/" + urlEncode(platform) + "/contract/" + urlEncode(contractAddress) + "/market_chart?vs_currency="
                + urlEncode(vsCurrency) + "&days=" + urlEncode(days) + (interval == null ? "" : "&interval=" + urlEncode(interval));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko contract market chart error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getTokenMarketChartRangeByContract(String platform, String contractAddress,
                                                                   String vsCurrency, long from, long to) {
        String url = baseUrl + "/coins/" + urlEncode(platform) + "/contract/" + urlEncode(contractAddress) + "/market_chart/range?vs_currency="
                + urlEncode(vsCurrency) + "&from=" + from + "&to=" + to;
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko contract market chart range error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- NFT ---
    public Optional<JsonObject> listNftCollections(int perPage, int page, String order) {
        String url = baseUrl + "/nfts/list?per_page=" + perPage + "&page=" + page + (order == null ? "" : "&order=" + urlEncode(order));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko nfts list error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getNftCollection(String id, boolean localization) {
        String url = baseUrl + "/nfts/" + urlEncode(id) + "?localization=" + localization;
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko nft collection error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getNftMarketChart(String id, String days, String order) {
        String url = baseUrl + "/nfts/" + urlEncode(id) + "/market_chart?days=" + urlEncode(days) + (order == null ? "" : "&order=" + urlEncode(order));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko nft market chart error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Exchanges ---
    public Optional<JsonArray> listExchanges(int perPage, int page) {
        String url = baseUrl + "/exchanges?per_page=" + perPage + "&page=" + page;
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko exchanges list error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getExchangeById(String id) {
        String url = baseUrl + "/exchanges/" + urlEncode(id);
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko exchange error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getExchangeTickers(String id, String coinIds, boolean includeExchangeLogo, String order, int page) {
        String url = baseUrl + "/exchanges/" + urlEncode(id) + "/tickers"
                + (coinIds == null || coinIds.isBlank() ? "" : "?coin_ids=" + urlEncode(coinIds))
                + "&include_exchange_logo=" + includeExchangeLogo
                + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko exchange tickers error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getExchangeVolumeChart(String id, String days) {
        String url = baseUrl + "/exchanges/" + urlEncode(id) + "/volume_chart?days=" + urlEncode(days);
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko exchange volume error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getExchangeBtcVolumeChart(String id, String days) {
        String url = baseUrl + "/exchanges/" + urlEncode(id) + "/btc_volume_chart?days=" + urlEncode(days);
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko exchange btc volume error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Derivatives ---
    public Optional<JsonArray> listDerivatives(int perPage, int page, String order) {
        String url = baseUrl + "/derivatives?per_page=" + perPage + "&page=" + page + (order == null ? "" : "&order=" + urlEncode(order));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko derivatives list error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getDerivativeExchanges(int perPage, int page, String order) {
        String url = baseUrl + "/derivatives/exchanges?per_page=" + perPage + "&page=" + page + (order == null ? "" : "&order=" + urlEncode(order));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko derivatives exchanges error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getDerivativeExchangeById(String id) {
        String url = baseUrl + "/derivatives/exchanges/" + urlEncode(id);
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko derivative exchange error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getDerivativeExchangeVolumeChart(String id, String days) {
        String url = baseUrl + "/derivatives/exchanges/" + urlEncode(id) + "/volume_chart?days=" + urlEncode(days);
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko derivative volume error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Treasury & Market-wide ---
    public Optional<JsonObject> getGlobalData() {
        String url = baseUrl + "/global";
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko global error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getGlobalDeFiData() {
        String url = baseUrl + "/global/decentralized_finance_defi";
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko global defi error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getSearchTrending() {
        String url = baseUrl + "/search/trending";
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            if (obj.has("coins")) return Optional.ofNullable(obj.getAsJsonArray("coins"));
            return Optional.empty();
        } catch (IOException e) {
            log.error("CoinGecko trending error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> search(String query) {
        String url = baseUrl + "/search?query=" + urlEncode(query);
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko search error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Onchain / DEX ---
    public Optional<JsonArray> listNetworks() {
        String url = baseUrl + "/onchain/networks";
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko onchain networks error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getPoolsByNetwork(String network, int perPage, int page, String order) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/pools?per_page=" + perPage + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko onchain pools error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getPoolData(String network, String poolAddress) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/pools/" + urlEncode(poolAddress);
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko pool data error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getTokensByNetwork(String network, int perPage, int page, String order) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/tokens?per_page=" + perPage + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko onchain tokens error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getTokenData(String network, String contractAddress) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/tokens/" + urlEncode(contractAddress);
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko onchain token error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getDexesByNetwork(String network, int perPage, int page, String order) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/dexes?per_page=" + perPage + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko onchain dexes error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getDexData(String network, String dexName) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/dexes/" + urlEncode(dexName);
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko onchain dex error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Typed POJO wrappers ---
    public Map<String, CoinGeckoSimplePrice> getSimplePriceTyped(String idsCsv, String vsCurrency,
                                                                 boolean includeMarketCap, boolean include24hChange,
                                                                 boolean includeLastUpdatedAt) {
        Optional<JsonObject> rootOpt = getSimplePrice(idsCsv, vsCurrency, includeMarketCap, include24hChange, includeLastUpdatedAt);
        if (rootOpt.isEmpty()) return Collections.emptyMap();
        JsonObject root = rootOpt.get();
        Map<String, CoinGeckoSimplePrice> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            try {
                JsonObject coinNode = e.getValue().getAsJsonObject();
                CoinGeckoSimplePrice.fromCoinNode(coinNode).ifPresent(p -> out.put(e.getKey(), p));
            } catch (Exception ex) {
                log.debug("parse simple price entry {} failed: {}", e.getKey(), ex.getMessage());
            }
        }
        return out;
    }

    public List<CoinGeckoMarketCoin> getCoinsMarketsTyped(String vsCurrency, String idsCsv, String order, int perPage, int page,
                                                          boolean sparkline, String priceChangePercentage) {
        Optional<JsonArray> arrOpt = getCoinsMarkets(vsCurrency, idsCsv, order, perPage, page, sparkline, priceChangePercentage);
        if (arrOpt.isEmpty()) return Collections.emptyList();
        JsonArray arr = arrOpt.get();
        List<CoinGeckoMarketCoin> out = new ArrayList<>();
        for (JsonElement el : arr) {
            try {
                JsonObject node = el.getAsJsonObject();
                CoinGeckoMarketCoin.fromJson(node).ifPresent(out::add);
            } catch (Exception ex) {
                log.debug("parse markets row failed: {}", ex.getMessage());
            }
        }
        return out;
    }

    public Optional<CoinGeckoFullCoin> getCoinByIdTyped(String id, boolean localization, boolean marketData,
                                                        boolean communityData, boolean developerData, boolean sparkline) {
        Optional<JsonObject> obj = getCoinById(id, localization, marketData, communityData, developerData, sparkline);
        return obj.map(j -> gson.fromJson(j, CoinGeckoFullCoin.class));
    }

    public Optional<CoinGeckoMarketChart> getCoinMarketChartTyped(String id, String vsCurrency, String days, String interval) {
        Optional<JsonObject> obj = getCoinMarketChart(id, vsCurrency, days, interval);
        return obj.map(j -> gson.fromJson(j, CoinGeckoMarketChart.class));
    }

    // --- Utilities ---
    private String urlEncode(String s) {
        if (s == null) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ── Helpers ─────────────────────────────────────────────

    private String toCoinId(String symbol) {
        // Strip common suffixes: BTCUSDT → BTC
        String base = stripSuffix(symbol.toUpperCase());
        return SYMBOL_TO_ID.get(base);
    }

    private String stripSuffix(String s) {
        for (String suffix : List.of("USDT","USD","BTC","ETH","BNB","BUSD"))
            if (s.endsWith(suffix) && s.length() > suffix.length())
                return s.substring(0, s.length() - suffix.length());
        return s;
    }

    private int toDays(String timeframe, int limit) {
        return switch (timeframe.toLowerCase()) {
            case "1m"  -> 1;
            case "5m"  -> 1;
            case "15m" -> 2;
            case "30m" -> 3;
            case "1h"  -> Math.max(1, limit / 24) + 1;
            case "4h"  -> Math.max(1, limit / 6)  + 1;
            case "1d"  -> Math.min(limit + 5, 365);
            case "1w"  -> Math.min(limit * 7 + 10, 1825);
            default    -> 30;
        };
    }

    private Request buildRequest(String url) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TradingPlatform/1.0")
                .addHeader("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank() && !"demo".equalsIgnoreCase(apiKey))
            b.addHeader("x-cg-demo-api-key", apiKey);
        return b.build();
    }
}