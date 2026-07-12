package com.mst.matt.tradingplatformapp.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mst.matt.tradingplatformapp.model.SymbolEntry;
import com.mst.matt.tradingplatformapp.model.SymbolEntry.AssetType;
import com.mst.matt.tradingplatformapp.repository.SymbolEntryRepository;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Populates the {@code symbol_entries} table from external APIs:
 * <ul>
 *   <li>CRYPTO  — Binance {@code /api/v3/exchangeInfo}</li>
 *   <li>STOCKS  — Finnhub {@code /api/v1/stock/symbol} (US exchange)</li>
 *   <li>FOREX   — hard-coded major/minor pairs (no API key needed)</li>
 * </ul>
 *
 * Sync runs once on startup (async, non-blocking).
 * Subsequent syncs run only when the DB is empty for a given type or when
 * {@link #syncAll()} is called explicitly (e.g., from settings panel).
 */
@Service
public class SymbolSyncService {

    private static final Logger log = LoggerFactory.getLogger(SymbolSyncService.class);

    private static final String BINANCE_EXCHANGE_INFO =
            "https://api.binance.com/api/v3/exchangeInfo";
    private static final String FINNHUB_US_STOCKS =
            "https://finnhub.io/api/v1/stock/symbol?exchange=US&token=";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Autowired private SymbolEntryRepository symbolRepo;

    @Value("${app.api.finnhub.key:}") private String finnhubKey;

    // ── Startup trigger ────────────────────────────────────────

    @PostConstruct
    public void onStartup() {
        // Run asynchronously so as not to block Spring context startup
        Thread.ofVirtual().start(this::syncAll);
    }

    // ── Public API ─────────────────────────────────────────────

    /**
     * Sync all asset types. Only syncs types that have fewer than 10 entries
     * to avoid hammering APIs on every restart.
     */
    public void syncAll() {
        log.info("SymbolSyncService: starting symbol sync");
        syncCrypto();
        syncForex();
        if (finnhubKey != null && !finnhubKey.isBlank()) {
            syncStocks();
        } else {
            syncStocksHardcoded();
        }
        log.info("SymbolSyncService: sync complete — crypto={}, stocks={}, forex={}",
                symbolRepo.countByAssetType(AssetType.CRYPTO),
                symbolRepo.countByAssetType(AssetType.STOCK),
                symbolRepo.countByAssetType(AssetType.FOREX));
    }

    // ── Crypto (Binance) ───────────────────────────────────────

    private void syncCrypto() {
        if (symbolRepo.countByAssetType(AssetType.CRYPTO) > 10) {
            log.debug("Crypto symbols already loaded — skipping");
            return;
        }
        try {
            log.info("Fetching crypto symbols from Binance…");
            String json = get(BINANCE_EXCHANGE_INFO);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray symbols = root.getAsJsonArray("symbols");

            List<SymbolEntry> toSave = new ArrayList<>();
            for (JsonElement el : symbols) {
                JsonObject s = el.getAsJsonObject();
                String symbol = s.get("symbol").getAsString();
                String base   = s.get("baseAsset").getAsString();
                String quote  = s.get("quoteAsset").getAsString();
                // Only USDT or BTC quoted pairs
                if (!quote.equals("USDT") && !quote.equals("BTC") && !quote.equals("ETH")) continue;
                if (symbolRepo.existsByAssetTypeAndSymbol(AssetType.CRYPTO, symbol)) continue;
                toSave.add(SymbolEntry.builder()
                        .symbol(symbol)
                        .name(base + "/" + quote)
                        .assetType(AssetType.CRYPTO)
                        .exchange("Binance")
                        .source("binance")
                        .build());
            }
            saveBatch(toSave);
            log.info("Saved {} crypto symbols from Binance", toSave.size());
        } catch (Exception e) {
            log.warn("Crypto symbol sync failed: {}", e.getMessage());
            syncCryptoHardcoded();
        }
    }

    private void syncCryptoHardcoded() {
        log.info("Using hardcoded crypto symbols fallback");
        List<String[]> pairs = List.of(
                new String[]{"BTCUSDT","Bitcoin/USDT"}, new String[]{"ETHUSDT","Ethereum/USDT"},
                new String[]{"BNBUSDT","BNB/USDT"}, new String[]{"SOLUSDT","Solana/USDT"},
                new String[]{"XRPUSDT","Ripple/USDT"}, new String[]{"ADAUSDT","Cardano/USDT"},
                new String[]{"DOGEUSDT","Dogecoin/USDT"}, new String[]{"AVAXUSDT","Avalanche/USDT"},
                new String[]{"DOTUSDT","Polkadot/USDT"}, new String[]{"MATICUSDT","Polygon/USDT"},
                new String[]{"LTCUSDT","Litecoin/USDT"}, new String[]{"LINKUSDT","Chainlink/USDT"},
                new String[]{"UNIUSDT","Uniswap/USDT"}, new String[]{"ATOMUSDT","Cosmos/USDT"},
                new String[]{"NEARUSDT","NEAR/USDT"}, new String[]{"FILUSDT","Filecoin/USDT"},
                new String[]{"TRXUSDT","TRON/USDT"}, new String[]{"XLMUSDT","Stellar/USDT"},
                new String[]{"VETUSDT","VeChain/USDT"}, new String[]{"ALGOUSDT","Algorand/USDT"}
        );
        List<SymbolEntry> entries = new ArrayList<>();
        for (String[] p : pairs) {
            if (!symbolRepo.existsByAssetTypeAndSymbol(AssetType.CRYPTO, p[0]))
                entries.add(SymbolEntry.builder()
                        .symbol(p[0]).name(p[1])
                        .assetType(AssetType.CRYPTO)
                        .exchange("Binance").source("hardcoded").build());
        }
        saveBatch(entries);
    }

    // ── Stocks (Finnhub) ──────────────────────────────────────

    private void syncStocks() {
        if (symbolRepo.countByAssetType(AssetType.STOCK) > 10) {
            log.debug("Stock symbols already loaded — skipping");
            return;
        }
        try {
            log.info("Fetching stock symbols from Finnhub…");
            String json = get(FINNHUB_US_STOCKS + finnhubKey);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();

            List<SymbolEntry> toSave = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject s = el.getAsJsonObject();
                String symbol = s.has("symbol") ? s.get("symbol").getAsString() : null;
                String desc   = s.has("description") ? s.get("description").getAsString() : symbol;
                if (symbol == null || symbol.contains(".")) continue; // skip non-US
                if (symbolRepo.existsByAssetTypeAndSymbol(AssetType.STOCK, symbol)) continue;
                toSave.add(SymbolEntry.builder()
                        .symbol(symbol)
                        .name(desc)
                        .assetType(AssetType.STOCK)
                        .exchange("US")
                        .source("finnhub")
                        .build());
            }
            saveBatch(toSave);
            log.info("Saved {} stock symbols from Finnhub", toSave.size());
        } catch (Exception e) {
            log.warn("Stock symbol sync failed: {}", e.getMessage());
            syncStocksHardcoded();
        }
    }

    private void syncStocksHardcoded() {
        log.info("Using hardcoded stock symbols fallback");
        List<String[]> stocks = List.of(
                new String[]{"AAPL","Apple Inc."}, new String[]{"MSFT","Microsoft Corp."},
                new String[]{"GOOGL","Alphabet Inc."}, new String[]{"AMZN","Amazon.com Inc."},
                new String[]{"TSLA","Tesla Inc."}, new String[]{"NVDA","NVIDIA Corp."},
                new String[]{"META","Meta Platforms"}, new String[]{"BRK.B","Berkshire Hathaway"},
                new String[]{"JPM","JPMorgan Chase"}, new String[]{"V","Visa Inc."},
                new String[]{"JNJ","Johnson & Johnson"}, new String[]{"WMT","Walmart Inc."},
                new String[]{"PG","Procter & Gamble"}, new String[]{"MA","Mastercard Inc."},
                new String[]{"UNH","UnitedHealth Group"}, new String[]{"HD","Home Depot"},
                new String[]{"DIS","Walt Disney Co."}, new String[]{"PYPL","PayPal Holdings"},
                new String[]{"BAC","Bank of America"}, new String[]{"NFLX","Netflix Inc."},
                new String[]{"CRM","Salesforce Inc."}, new String[]{"INTC","Intel Corp."},
                new String[]{"AMD","Advanced Micro Devices"}, new String[]{"QCOM","Qualcomm"},
                new String[]{"COIN","Coinbase Global"}, new String[]{"SQ","Block Inc."},
                new String[]{"PLTR","Palantir Technologies"}, new String[]{"RBLX","Roblox Corp."},
                new String[]{"HOOD","Robinhood Markets"}, new String[]{"SOFI","SoFi Technologies"}
        );
        List<SymbolEntry> entries = new ArrayList<>();
        for (String[] s : stocks) {
            if (!symbolRepo.existsByAssetTypeAndSymbol(AssetType.STOCK, s[0]))
                entries.add(SymbolEntry.builder()
                        .symbol(s[0]).name(s[1])
                        .assetType(AssetType.STOCK)
                        .exchange("NASDAQ/NYSE").source("hardcoded").build());
        }
        saveBatch(entries);
    }

    // ── Forex (hardcoded major + minor pairs) ─────────────────

    private void syncForex() {
        if (symbolRepo.countByAssetType(AssetType.FOREX) > 10) {
            log.debug("Forex symbols already loaded — skipping");
            return;
        }
        log.info("Loading hardcoded forex pairs");
        List<String[]> pairs = List.of(
                // Majors
                new String[]{"EURUSD","EUR/USD — Euro vs US Dollar"},
                new String[]{"GBPUSD","GBP/USD — Pound vs US Dollar"},
                new String[]{"USDJPY","USD/JPY — US Dollar vs Japanese Yen"},
                new String[]{"USDCHF","USD/CHF — US Dollar vs Swiss Franc"},
                new String[]{"AUDUSD","AUD/USD — Australian Dollar vs US Dollar"},
                new String[]{"USDCAD","USD/CAD — US Dollar vs Canadian Dollar"},
                new String[]{"NZDUSD","NZD/USD — New Zealand Dollar vs US Dollar"},
                // Minors
                new String[]{"EURGBP","EUR/GBP — Euro vs Pound"},
                new String[]{"EURJPY","EUR/JPY — Euro vs Japanese Yen"},
                new String[]{"GBPJPY","GBP/JPY — Pound vs Japanese Yen"},
                new String[]{"EURCHF","EUR/CHF — Euro vs Swiss Franc"},
                new String[]{"GBPCHF","GBP/CHF — Pound vs Swiss Franc"},
                new String[]{"AUDJPY","AUD/JPY — Australian Dollar vs Yen"},
                new String[]{"CADJPY","CAD/JPY — Canadian Dollar vs Yen"},
                new String[]{"EURAUD","EUR/AUD — Euro vs Australian Dollar"},
                new String[]{"EURCAD","EUR/CAD — Euro vs Canadian Dollar"},
                new String[]{"GBPAUD","GBP/AUD — Pound vs Australian Dollar"},
                new String[]{"GBPCAD","GBP/CAD — Pound vs Canadian Dollar"},
                new String[]{"AUDCAD","AUD/CAD — Australian vs Canadian Dollar"},
                new String[]{"AUDCHF","AUD/CHF — Australian Dollar vs Swiss Franc"},
                // Exotics
                new String[]{"USDHKD","USD/HKD — US Dollar vs Hong Kong Dollar"},
                new String[]{"USDSGD","USD/SGD — US Dollar vs Singapore Dollar"},
                new String[]{"USDZAR","USD/ZAR — US Dollar vs South African Rand"},
                new String[]{"USDMXN","USD/MXN — US Dollar vs Mexican Peso"},
                new String[]{"USDBRL","USD/BRL — US Dollar vs Brazilian Real"},
                new String[]{"USDTRY","USD/TRY — US Dollar vs Turkish Lira"},
                new String[]{"USDRUB","USD/RUB — US Dollar vs Russian Ruble"},
                new String[]{"USDCNY","USD/CNY — US Dollar vs Chinese Yuan"},
                new String[]{"USDINR","USD/INR — US Dollar vs Indian Rupee"},
                new String[]{"USDKRW","USD/KRW — US Dollar vs South Korean Won"}
        );
        List<SymbolEntry> entries = new ArrayList<>();
        for (String[] p : pairs) {
            if (!symbolRepo.existsByAssetTypeAndSymbol(AssetType.FOREX, p[0]))
                entries.add(SymbolEntry.builder()
                        .symbol(p[0]).name(p[1])
                        .assetType(AssetType.FOREX)
                        .exchange("FX").source("hardcoded").build());
        }
        saveBatch(entries);
    }

    // ── Helpers ────────────────────────────────────────────────

    @Transactional
    protected void saveBatch(List<SymbolEntry> entries) {
        if (entries.isEmpty()) return;
        // Save in batches of 500 to avoid locking SQLite for too long
        for (int i = 0; i < entries.size(); i += 500) {
            int end = Math.min(i + 500, entries.size());
            symbolRepo.saveAll(entries.subList(i, end));
        }
    }

    private String get(String url) throws IOException {
        Request req = new Request.Builder().url(url)
                .header("Accept", "application/json").build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new IOException("HTTP " + resp.code() + " for " + url);
            return resp.body().string();
        }
    }
}
