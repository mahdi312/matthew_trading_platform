package com.mst.matt.tradingservice.service;

import com.mst.matt.tradingservice.model.Trade;
import com.mst.matt.tradingservice.model.Trade.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Parses broker-exported CSV files and converts rows into {@link Trade} objects.
 *
 * <p>Ported from the desktop monolith's {@code BrokerImportService}.
 * The key adaptation for the microservice context:
 * <ul>
 *   <li>The desktop version accepted {@code java.io.File} (from a FileChooser).
 *       This version accepts a Spring {@link MultipartFile} (uploaded via REST)
 *       or a raw {@code InputStream}/{@code byte[]} for programmatic use.</li>
 *   <li>Operates on {@code Long userId} instead of a {@code UserProfile} entity.</li>
 *   <li>Saves parsed trades via {@link TradeService} so they are immediately persisted.</li>
 * </ul>
 *
 * <h3>Supported broker formats (auto-detected from CSV header)</h3>
 * <ul>
 *   <li><b>Binance</b>  — "Date(UTC)", "Pair", "Side", "Price", "Executed", "Amount", "Fee"</li>
 *   <li><b>Bybit</b>    — "Time", "Symbol", "Side", "Qty", "Price", "Order PnL", "Taker Fee"</li>
 *   <li><b>eToro</b>    — "Date", "Type", "Details", "Amount", "Units", "Open Rate", "Close Rate", "Profit"</li>
 *   <li><b>MT4/MT5</b>  — "Ticket", "Open Time", "Type", "Size", "Symbol", "Price", "S / L", "T / P", "Close Time", "Profit"</li>
 *   <li><b>Interactive Brokers</b> — "Trades", "Header", "DataDiscriminator", "Symbol", "Date/Time", "Quantity", "T. Price", "Realized P/L"</li>
 *   <li><b>Generic</b>  — auto-maps common column names for any other CSV</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerImportService {

    private final TradeService tradeService;

    public enum Broker {
        BINANCE("Binance"),
        BYBIT("Bybit"),
        ETORO("eToro"),
        MT4_MT5("MetaTrader"),
        INTERACTIVE_BROKERS("Interactive Brokers"),
        GENERIC("Generic CSV");

        public final String label;
        Broker(String label) { this.label = label; }
    }

    public record ImportResult(
            Broker broker,
            List<Trade> trades,
            List<String> skippedRows,
            int totalRows
    ) {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Parse and persist all valid trades from the uploaded CSV.
     *
     * @param file    multipart CSV file from the REST upload endpoint
     * @param userId  user ID to associate all imported trades with
     * @return {@link ImportResult} with broker type, saved trades, and skipped-row messages
     */
    @Transactional
    public ImportResult importCsv(MultipartFile file, Long userId) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.csv";
        List<String[]> rows = readCsv(file.getInputStream());

        if (rows.isEmpty()) {
            return new ImportResult(Broker.GENERIC, List.of(), List.of(), 0);
        }

        String[] header = rows.get(0);
        Broker broker = detectBroker(header);
        log.info("Detected broker: {} from file: {}", broker, filename);

        List<String[]> dataRows = rows.subList(1, rows.size());
        List<Trade> trades  = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (int i = 0; i < dataRows.size(); i++) {
            String[] row = dataRows.get(i);
            try {
                Trade t = parseRow(broker, header, row, userId, filename);
                if (t != null) {
                    Trade saved = tradeService.saveTrade(t);
                    trades.add(saved);
                }
            } catch (Exception e) {
                skipped.add("Row " + (i + 2) + ": " + e.getMessage());
                log.debug("Skipped row {}: {}", i + 2, e.getMessage());
            }
        }

        log.info("Import complete: broker={} parsed={} skipped={} file={}",
                broker.label, trades.size(), skipped.size(), filename);
        return new ImportResult(broker, trades, skipped, dataRows.size());
    }

    /**
     * Parse-only variant (no DB save) — useful for previewing before commit.
     *
     * @param inputStream raw CSV bytes
     * @param userId      user ID to stamp on Trade objects
     * @param filename    logical file name for audit notes
     * @return unsaved {@link Trade} objects in an {@link ImportResult}
     */
    public ImportResult previewCsv(InputStream inputStream, Long userId, String filename) throws IOException {
        List<String[]> rows = readCsv(inputStream);
        if (rows.isEmpty()) return new ImportResult(Broker.GENERIC, List.of(), List.of(), 0);

        String[] header  = rows.get(0);
        Broker broker    = detectBroker(header);
        List<String[]> dataRows = rows.subList(1, rows.size());
        List<Trade> trades  = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (int i = 0; i < dataRows.size(); i++) {
            try {
                Trade t = parseRow(broker, header, dataRows.get(i), userId, filename);
                if (t != null) trades.add(t);
            } catch (Exception e) {
                skipped.add("Row " + (i + 2) + ": " + e.getMessage());
            }
        }
        return new ImportResult(broker, trades, skipped, dataRows.size());
    }

    // ── Broker Detection ───────────────────────────────────────────────────────

    private Broker detectBroker(String[] header) {
        String joined = String.join(",", header).toLowerCase();
        if (joined.contains("date(utc)") || (joined.contains("executed") && joined.contains("pair")))
            return Broker.BINANCE;
        if (joined.contains("order pnl") || (joined.contains("taker fee") && joined.contains("symbol")))
            return Broker.BYBIT;
        if (joined.contains("open rate") && joined.contains("close rate") && joined.contains("details"))
            return Broker.ETORO;
        if (joined.contains("ticket") && joined.contains("s / l") && joined.contains("t / p"))
            return Broker.MT4_MT5;
        if (joined.contains("realized p/l") || (joined.contains("t. price") && joined.contains("c. price")))
            return Broker.INTERACTIVE_BROKERS;
        return Broker.GENERIC;
    }

    // ── Row Dispatch ───────────────────────────────────────────────────────────

    private Trade parseRow(Broker broker, String[] header, String[] row,
                           Long userId, String sourceFile) {
        return switch (broker) {
            case BINANCE             -> parseBinanceRow(header, row, userId, sourceFile);
            case BYBIT               -> parseBybitRow(header, row, userId, sourceFile);
            case ETORO               -> parseEtoroRow(header, row, userId, sourceFile);
            case MT4_MT5             -> parseMt4Row(header, row, userId, sourceFile);
            case INTERACTIVE_BROKERS -> parseIbRow(header, row, userId, sourceFile);
            default                  -> parseGenericRow(header, row, userId, sourceFile);
        };
    }

    // ── Binance ────────────────────────────────────────────────────────────────
    // Headers: Date(UTC), Pair, Side, Price, Executed, Amount, Fee
    private Trade parseBinanceRow(String[] header, String[] row, Long userId, String sourceFile) {
        Map<String, String> m = mapRow(header, row);
        String pair     = get(m, "pair", "symbol");
        String side     = get(m, "side", "type");
        String price    = get(m, "price");
        String executed = get(m, "executed");
        String amount   = get(m, "amount");
        String fee      = get(m, "fee");
        String dateStr  = get(m, "date(utc)", "date", "time");

        if (pair == null || price == null) return null;
        executed = stripAlpha(executed);
        amount   = stripAlpha(amount);
        fee      = stripAlpha(fee);

        Trade t = new Trade();
        t.setUserId(userId);
        t.setSymbol(clean(pair).toUpperCase());
        t.setAssetName(clean(pair).toUpperCase());
        t.setAssetType(detectAssetType(pair));
        t.setDirection("buy".equalsIgnoreCase(clean(side)) ? TradeDirection.LONG : TradeDirection.SHORT);
        t.setStatus(TradeStatus.CLOSED);
        t.setSource(TradeSource.BROKER_IMPORT);
        t.setEntryPrice(bd(price));
        t.setExitPrice(bd(price)); // Binance spot: entry == exit per row
        t.setQuantity(bd(executed).compareTo(BigDecimal.ZERO) > 0 ? bd(executed) : bd(amount));
        t.setFee(bd(fee));
        t.setExchange("Binance");
        t.setEntryTime(parseDateTime(dateStr));
        t.setExitTime(t.getEntryTime());
        t.setNotes("Imported from Binance — " + sourceFile);
        t.computePnL();
        return t;
    }

    // ── Bybit ──────────────────────────────────────────────────────────────────
    // Headers: Time, Symbol, Side, Qty, Price, Order PnL, Taker Fee
    private Trade parseBybitRow(String[] header, String[] row, Long userId, String sourceFile) {
        Map<String, String> m = mapRow(header, row);
        String symbol  = get(m, "symbol");
        String side    = get(m, "side");
        String qty     = get(m, "qty", "quantity", "size");
        String price   = get(m, "price", "avg. price");
        String pnl     = get(m, "order pnl", "pnl", "realized pnl");
        String fee     = get(m, "taker fee", "fee");
        String dateStr = get(m, "time", "date", "create time");

        if (symbol == null || price == null) return null;

        Trade t = new Trade();
        t.setUserId(userId);
        t.setSymbol(clean(symbol).toUpperCase());
        t.setAssetName(clean(symbol).toUpperCase());
        t.setAssetType(detectAssetType(symbol));
        t.setDirection("buy".equalsIgnoreCase(clean(side)) ? TradeDirection.LONG : TradeDirection.SHORT);
        t.setStatus(TradeStatus.CLOSED);
        t.setSource(TradeSource.BROKER_IMPORT);
        t.setEntryPrice(bd(price));
        t.setExitPrice(bd(price));
        t.setQuantity(bd(qty));
        t.setFee(bd(fee));
        t.setExchange("Bybit");
        t.setEntryTime(parseDateTime(dateStr));
        t.setExitTime(t.getEntryTime());
        t.setNotes("Imported from Bybit — " + sourceFile);
        if (pnl != null && !pnl.isBlank()) t.setPnlAmount(bd(pnl));
        t.computePnL();
        return t;
    }

    // ── eToro ──────────────────────────────────────────────────────────────────
    // Headers: Date, Type, Details, Amount, Units, Open Rate, Close Rate, Profit
    private Trade parseEtoroRow(String[] header, String[] row, Long userId, String sourceFile) {
        Map<String, String> m = mapRow(header, row);
        String type      = get(m, "type");
        String details   = get(m, "details");
        String units     = get(m, "units");
        String openRate  = get(m, "open rate");
        String closeRate = get(m, "close rate");
        String profit    = get(m, "profit");
        String dateStr   = get(m, "date");

        if (type == null || (!type.toLowerCase().contains("trade")
                && !type.toLowerCase().contains("position"))) return null;
        if (openRate == null || openRate.isBlank()) return null;

        String symbol = (details != null && !details.isBlank())
                ? clean(details).toUpperCase().replaceAll("[^A-Z0-9/]", "")
                : "UNKNOWN";
        if (symbol.contains("/")) symbol = symbol.replace("/", "");

        Trade t = new Trade();
        t.setUserId(userId);
        t.setSymbol(symbol);
        t.setAssetName(symbol);
        t.setAssetType(detectAssetType(symbol));
        t.setDirection(TradeDirection.LONG);
        t.setStatus(TradeStatus.CLOSED);
        t.setSource(TradeSource.BROKER_IMPORT);
        t.setEntryPrice(bd(openRate));
        t.setExitPrice(bd(closeRate).compareTo(BigDecimal.ZERO) > 0 ? bd(closeRate) : bd(openRate));
        t.setQuantity(bd(units).compareTo(BigDecimal.ZERO) > 0 ? bd(units) : BigDecimal.ONE);
        t.setExchange("eToro");
        t.setEntryTime(parseDateTime(dateStr));
        t.setExitTime(t.getEntryTime());
        t.setNotes("Imported from eToro — " + sourceFile);
        t.setPnlAmount(bd(profit));
        t.computePnL();
        return t;
    }

    // ── MetaTrader 4/5 ─────────────────────────────────────────────────────────
    // Headers: Ticket, Open Time, Type, Size, Symbol, Price, S/L, T/P, Close Time, Price, Profit
    private Trade parseMt4Row(String[] header, String[] row, Long userId, String sourceFile) {
        if (row.length < 8) return null;
        Map<String, String> m = mapRow(header, row);
        String type      = get(m, "type");
        String symbol    = get(m, "symbol");
        String size      = get(m, "size", "lots");
        String openPrice = get(m, "price");
        String sl        = get(m, "s / l", "sl", "stop loss");
        String tp        = get(m, "t / p", "tp", "take profit");
        String profit    = get(m, "profit");
        String openTime  = get(m, "open time");
        String closeTime = get(m, "close time");

        if (symbol == null || type == null) return null;
        String typeLower = type.toLowerCase();
        if (typeLower.contains("balance") || typeLower.contains("credit")) return null;

        Trade t = new Trade();
        t.setUserId(userId);
        t.setSymbol(clean(symbol).toUpperCase());
        t.setAssetName(clean(symbol).toUpperCase());
        t.setAssetType(detectAssetType(symbol));
        t.setDirection(typeLower.contains("sell") ? TradeDirection.SHORT : TradeDirection.LONG);
        t.setStatus(TradeStatus.CLOSED);
        t.setSource(TradeSource.BROKER_IMPORT);
        t.setEntryPrice(bd(openPrice));
        t.setQuantity(bd(size).compareTo(BigDecimal.ZERO) > 0
                ? bd(size).multiply(BigDecimal.valueOf(100_000)) : BigDecimal.ONE);
        if (sl != null && !sl.isBlank() && !sl.equals("0")) t.setStopLoss(bd(sl));
        if (tp != null && !tp.isBlank() && !tp.equals("0")) t.setTakeProfit(bd(tp));
        t.setEntryTime(parseDateTime(openTime));
        t.setExitTime(closeTime != null ? parseDateTime(closeTime) : t.getEntryTime());
        t.setExchange("MetaTrader");
        t.setNotes("Imported from MT4/MT5 — " + sourceFile);
        t.setPnlAmount(bd(profit));
        t.computePnL();
        return t;
    }

    // ── Interactive Brokers ────────────────────────────────────────────────────
    // Key columns: Symbol, Date/Time, Quantity, T. Price, Realized P/L, Comm/Fee
    private Trade parseIbRow(String[] header, String[] row, Long userId, String sourceFile) {
        Map<String, String> m = mapRow(header, row);
        String discriminator = get(m, "datadiscriminator", "data discriminator");
        if (discriminator != null && !discriminator.equalsIgnoreCase("Order")
                && !discriminator.equalsIgnoreCase("Trade")) return null;

        String symbol  = get(m, "symbol");
        String dateStr = get(m, "date/time", "date", "time");
        String qty     = get(m, "quantity");
        String price   = get(m, "t. price", "trade price", "price");
        String pnl     = get(m, "realized p/l", "realized pnl");
        String fee     = get(m, "comm/fee", "commission", "fee");

        if (symbol == null || price == null) return null;
        BigDecimal qtyBd = bd(qty);
        if (qtyBd.compareTo(BigDecimal.ZERO) == 0) return null;

        Trade t = new Trade();
        t.setUserId(userId);
        t.setSymbol(clean(symbol).toUpperCase());
        t.setAssetName(clean(symbol).toUpperCase());
        t.setAssetType(detectAssetType(symbol));
        t.setDirection(qtyBd.compareTo(BigDecimal.ZERO) > 0 ? TradeDirection.LONG : TradeDirection.SHORT);
        t.setStatus(TradeStatus.CLOSED);
        t.setSource(TradeSource.BROKER_IMPORT);
        t.setEntryPrice(bd(price));
        t.setExitPrice(bd(price));
        t.setQuantity(qtyBd.abs());
        t.setFee(bd(fee).abs());
        t.setExchange("Interactive Brokers");
        t.setEntryTime(parseDateTime(dateStr));
        t.setExitTime(t.getEntryTime());
        t.setNotes("Imported from Interactive Brokers — " + sourceFile);
        if (pnl != null && !pnl.isBlank()) t.setPnlAmount(bd(pnl));
        t.computePnL();
        return t;
    }

    // ── Generic CSV ────────────────────────────────────────────────────────────
    private Trade parseGenericRow(String[] header, String[] row, Long userId, String sourceFile) {
        Map<String, String> m = mapRow(header, row);
        String symbol = get(m, "symbol", "pair", "ticker", "instrument");
        String price  = get(m, "price", "entry price", "open price");
        String qty    = get(m, "qty", "quantity", "size", "amount");
        String side   = get(m, "side", "type", "direction", "action");
        String date   = get(m, "date", "time", "datetime", "open time", "entry time");
        String fee    = get(m, "fee", "commission");
        String pnl    = get(m, "pnl", "profit", "realized pnl", "p&l");
        String exit   = get(m, "exit price", "close price", "close rate");

        if (symbol == null || price == null) return null;

        Trade t = new Trade();
        t.setUserId(userId);
        t.setSymbol(clean(symbol).toUpperCase());
        t.setAssetName(clean(symbol).toUpperCase());
        t.setAssetType(detectAssetType(symbol));
        t.setDirection(side != null && (side.toLowerCase().contains("sell")
                || side.toLowerCase().contains("short"))
                ? TradeDirection.SHORT : TradeDirection.LONG);
        t.setStatus(exit != null && !exit.isBlank() ? TradeStatus.CLOSED : TradeStatus.OPEN);
        t.setSource(TradeSource.BROKER_IMPORT);
        t.setEntryPrice(bd(price));
        t.setExitPrice(exit != null ? bd(exit) : null);
        t.setQuantity(bd(qty).compareTo(BigDecimal.ZERO) > 0 ? bd(qty) : BigDecimal.ONE);
        t.setFee(bd(fee));
        t.setEntryTime(parseDateTime(date));
        t.setExitTime(t.getEntryTime());
        t.setExchange("Imported");
        t.setNotes("Imported from Generic CSV — " + sourceFile);
        if (pnl != null) t.setPnlAmount(bd(pnl));
        t.computePnL();
        return t;
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private List<String[]> readCsv(InputStream is) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue;
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
    }

    /** Handles quoted fields that may contain commas. */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString().trim());
        return fields.toArray(new String[0]);
    }

    private Map<String, String> mapRow(String[] header, String[] row) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < header.length && i < row.length; i++) {
            m.put(header[i].trim().toLowerCase(), row[i].trim());
        }
        return m;
    }

    /** Returns the first non-blank value matching any of the given keys (case-insensitive). */
    private String get(Map<String, String> m, String... keys) {
        for (String k : keys) {
            String v = m.get(k.toLowerCase());
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("^\"|\"$", "");
    }

    /** Strips trailing alphabetic suffix: "0.012BTC" → "0.012" */
    private String stripAlpha(String s) {
        if (s == null) return null;
        return s.replaceAll("[A-Za-z]+$", "").trim();
    }

    private BigDecimal bd(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static final List<DateTimeFormatter> DT_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    );

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return LocalDateTime.now();
        String cleaned = s.trim().replaceAll("^\"|\"$", "");
        for (DateTimeFormatter fmt : DT_FORMATS) {
            try {
                try {
                    return LocalDateTime.parse(cleaned, fmt);
                } catch (DateTimeParseException e2) {
                    return LocalDate.parse(cleaned, fmt).atTime(LocalTime.MIDNIGHT);
                }
            } catch (DateTimeParseException ignored) {}
        }
        log.debug("Cannot parse datetime: '{}'", s);
        return LocalDateTime.now();
    }

    private AssetType detectAssetType(String symbol) {
        if (symbol == null) return AssetType.CRYPTO;
        String s = symbol.toUpperCase();
        if (s.endsWith("USDT") || s.endsWith("BTC") || s.endsWith("ETH")
                || s.endsWith("BNB") || s.endsWith("BUSD")
                || (s.contains("USD") && s.length() <= 7))
            return AssetType.CRYPTO;
        if (s.contains("USD") || s.contains("EUR") || s.contains("GBP")
                || s.contains("JPY") || s.contains("AUD") || s.length() == 6)
            return AssetType.FOREX;
        return AssetType.STOCK;
    }
}
