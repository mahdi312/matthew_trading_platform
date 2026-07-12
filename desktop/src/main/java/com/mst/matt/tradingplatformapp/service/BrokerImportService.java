package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.model.Trade;
import com.mst.matt.tradingplatformapp.model.Trade.*;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
 * Supported formats (auto-detected from header row):
 *  ● Binance  — "Date(UTC)", "Pair", "Side", "Price", "Executed", "Amount", "Fee"
 *  ● Bybit    — "Time", "Symbol", "Side", "Qty", "Price", "Order PnL", "Taker Fee"
 *  ● eToro    — "Date", "Type", "Details", "Amount", "Units", "Open Rate", "Close Rate", "Profit"
 *  ● MT4/MT5  — "Ticket", "Open Time", "Type", "Size", "Symbol", "Price", "S / L", "T / P", "Close Time", "Price", "Profit"
 *  ● Interactive Brokers — "Trades", "Header", "DataDiscriminator", "Asset Category", "Currency",
 *                          "Symbol", "Date/Time", "Quantity", "T. Price", "C. Price", "Proceeds",
 *                          "Comm/Fee", "Basis", "Realized P/L"
 *  ● Generic  — auto-maps columns containing "symbol"/"pair", "price", "qty"/"quantity",
 *               "side"/"type", "date"/"time"
 */
@Service
public class BrokerImportService {

    private static final Logger log = LoggerFactory.getLogger(BrokerImportService.class);

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

    // ── Public API ────────────────────────────────────────────

    /**
     * Auto-detects the broker from the CSV header and parses all trades.
     *
     * @param file     CSV file (File object from FileChooser)
     * @param profile  the active user profile to attach trades to
     * @return ImportResult with parsed trades, broker name, and skipped rows
     */
    public ImportResult importCsv(File file, UserProfile profile) throws IOException {
        List<String[]> rows = readCsv(file);
        if (rows.isEmpty()) {
            return new ImportResult(Broker.GENERIC, List.of(), List.of(), 0);
        }

        String[] header = rows.get(0);
        Broker broker = detectBroker(header);
        log.info("Detected broker: {} from file: {}", broker, file.getName());

        List<String[]> dataRows = rows.subList(1, rows.size());
        List<Trade> trades = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (int i = 0; i < dataRows.size(); i++) {
            String[] row = dataRows.get(i);
            try {
                Trade t = parsRow(broker, header, row, profile, file.getName());
                if (t != null) {
                    trades.add(t);
                }
            } catch (Exception e) {
                skipped.add("Row " + (i + 2) + ": " + e.getMessage());
                log.debug("Skipped row {}: {}", i + 2, e.getMessage());
            }
        }

        return new ImportResult(broker, trades, skipped, dataRows.size());
    }

    // ── Broker Detection ──────────────────────────────────────

    private Broker detectBroker(String[] header) {
        String joined = String.join(",", header).toLowerCase();
        if (joined.contains("date(utc)") || joined.contains("executed") && joined.contains("pair"))
            return Broker.BINANCE;
        if (joined.contains("order pnl") || joined.contains("taker fee") && joined.contains("symbol"))
            return Broker.BYBIT;
        if (joined.contains("open rate") && joined.contains("close rate") && joined.contains("details"))
            return Broker.ETORO;
        if (joined.contains("ticket") && joined.contains("s / l") && joined.contains("t / p"))
            return Broker.MT4_MT5;
        if (joined.contains("realized p/l") || joined.contains("t. price") && joined.contains("c. price"))
            return Broker.INTERACTIVE_BROKERS;
        return Broker.GENERIC;
    }

    // ── Row Parsers ───────────────────────────────────────────

    private Trade parsRow(Broker broker, String[] header, String[] row,
                          UserProfile profile, String sourceFile) {
        return switch (broker) {
            case BINANCE             -> parseBinanceRow(header, row, profile, sourceFile);
            case BYBIT               -> parseBybitRow(header, row, profile, sourceFile);
            case ETORO               -> parseEtoroRow(header, row, profile, sourceFile);
            case MT4_MT5             -> parseMt4Row(header, row, profile, sourceFile);
            case INTERACTIVE_BROKERS -> parseIbRow(header, row, profile, sourceFile);
            default                  -> parseGenericRow(header, row, profile, sourceFile);
        };
    }

    // ── Binance ───────────────────────────────────────────────
    // Headers: Date(UTC), Pair, Side, Price, Executed, Amount, Fee
    private Trade parseBinanceRow(String[] header, String[] row,
                                  UserProfile profile, String sourceFile) {
        Map<String, String> m = mapRow(header, row);
        String pair     = get(m, "pair", "symbol");
        String side     = get(m, "side", "type");
        String price    = get(m, "price");
        String executed = get(m, "executed");
        String amount   = get(m, "amount");
        String fee      = get(m, "fee");
        String dateStr  = get(m, "date(utc)", "date", "time");

        if (pair == null || price == null) return null;
        // Strip quantity suffix e.g. "0.01BTC" → "0.01"
        executed = stripAlpha(executed);
        amount   = stripAlpha(amount);
        fee      = stripAlpha(fee);

        Trade t = new Trade();
        t.setProfile(profile);
        t.setSymbol(clean(pair).toUpperCase());
        t.setAssetName(clean(pair).toUpperCase());
        t.setAssetType(detectAssetType(pair));
        t.setDirection("buy".equalsIgnoreCase(clean(side)) ? TradeDirection.LONG : TradeDirection.SHORT);
        t.setStatus(TradeStatus.CLOSED);
        t.setEntryPrice(bd(price));
        t.setExitPrice(bd(price)); // Binance spot: entry==exit per row
        t.setQuantity(bd(executed).compareTo(BigDecimal.ZERO) > 0 ? bd(executed) : bd(amount));
        t.setFee(bd(fee));
        t.setExchange("Binance");
        t.setEntryTime(parseDateTime(dateStr));
        t.setExitTime(t.getEntryTime());
        t.setNotes("Imported from Binance — " + sourceFile);
        t.computePnL();
        return t;
    }

    // ── Bybit ─────────────────────────────────────────────────
    // Headers: Time, Symbol, Side, Qty, Price, Order PnL, Taker Fee, ...
    private Trade parseBybitRow(String[] header, String[] row,
                                UserProfile profile, String sourceFile) {
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
        t.setProfile(profile);
        t.setSymbol(clean(symbol).toUpperCase());
        t.setAssetName(clean(symbol).toUpperCase());
        t.setAssetType(detectAssetType(symbol));
        t.setDirection("buy".equalsIgnoreCase(clean(side)) ? TradeDirection.LONG : TradeDirection.SHORT);
        t.setStatus(TradeStatus.CLOSED);
        t.setEntryPrice(bd(price));
        t.setExitPrice(bd(price));
        t.setQuantity(bd(qty));
        t.setFee(bd(fee));
        t.setExchange("Bybit");
        t.setEntryTime(parseDateTime(dateStr));
        t.setExitTime(t.getEntryTime());
        t.setNotes("Imported from Bybit — " + sourceFile);
        if (pnl != null && !pnl.isBlank()) {
            t.setPnlAmount(bd(pnl));
        }
        t.computePnL();
        return t;
    }

    // ── eToro ─────────────────────────────────────────────────
    // Headers: Date, Type, Details, Amount, Units, Open Rate, Close Rate, Profit, ...
    private Trade parseEtoroRow(String[] header, String[] row,
                                UserProfile profile, String sourceFile) {
        Map<String, String> m = mapRow(header, row);
        String type      = get(m, "type");
        String details   = get(m, "details");
        String units     = get(m, "units");
        String openRate  = get(m, "open rate");
        String closeRate = get(m, "close rate");
        String profit    = get(m, "profit");
        String dateStr   = get(m, "date");

        // Only process trade rows (not deposits/withdrawals)
        if (type == null || (!type.toLowerCase().contains("trade") &&
                !type.toLowerCase().contains("position"))) return null;
        if (openRate == null || openRate.isBlank()) return null;

        // Symbol is usually in "Details" e.g. "BTC/USD" or "Apple"
        String symbol = (details != null && !details.isBlank())
                ? clean(details).toUpperCase().replaceAll("[^A-Z0-9/]", "")
                : "UNKNOWN";
        if (symbol.contains("/")) symbol = symbol.replace("/", "");

        Trade t = new Trade();
        t.setProfile(profile);
        t.setSymbol(symbol);
        t.setAssetName(symbol);
        t.setAssetType(detectAssetType(symbol));
        // eToro positions: if profit > 0 → LONG profitable, else SHORT or loss
        BigDecimal profitBd = bd(profit);
        t.setDirection(TradeDirection.LONG);
        t.setStatus(TradeStatus.CLOSED);
        t.setEntryPrice(bd(openRate));
        t.setExitPrice(bd(closeRate).compareTo(BigDecimal.ZERO) > 0 ? bd(closeRate) : bd(openRate));
        t.setQuantity(bd(units).compareTo(BigDecimal.ZERO) > 0 ? bd(units) : BigDecimal.ONE);
        t.setExchange("eToro");
        t.setEntryTime(parseDateTime(dateStr));
        t.setExitTime(t.getEntryTime());
        t.setNotes("Imported from eToro — " + sourceFile);
        t.setPnlAmount(profitBd);
        t.computePnL();
        return t;
    }

    // ── MetaTrader 4/5 ────────────────────────────────────────
    // Headers: Ticket, Open Time, Type, Size, Symbol, Price, S/L, T/P, Close Time, Price, Profit
    private Trade parseMt4Row(String[] header, String[] row,
                              UserProfile profile, String sourceFile) {
        if (row.length < 8) return null;
        Map<String, String> m = mapRow(header, row);
        String type      = get(m, "type");
        String symbol    = get(m, "symbol");
        String size      = get(m, "size", "lots");
        String openPrice = get(m, "price");
        String sl        = get(m, "s / l", "sl", "stop loss");
        String tp        = get(m, "t / p", "tp", "take profit");
        String profit    = get(m, "profit");
        String openTime  = get(m, "open time", "open time");
        String closeTime = get(m, "close time");

        if (symbol == null || type == null) return null;
        // Skip non-trade rows (balance, credit)
        String typeLower = type.toLowerCase();
        if (typeLower.contains("balance") || typeLower.contains("credit")) return null;

        Trade t = new Trade();
        t.setProfile(profile);
        t.setSymbol(clean(symbol).toUpperCase());
        t.setAssetName(clean(symbol).toUpperCase());
        t.setAssetType(detectAssetType(symbol));
        t.setDirection(typeLower.contains("sell") ? TradeDirection.SHORT : TradeDirection.LONG);
        t.setStatus(TradeStatus.CLOSED);
        t.setEntryPrice(bd(openPrice));
        t.setQuantity(bd(size).compareTo(BigDecimal.ZERO) > 0
                ? bd(size).multiply(BigDecimal.valueOf(100_000)) // lots → units
                : BigDecimal.ONE);
        if (sl != null && !sl.isBlank() && !sl.equals("0"))
            t.setStopLoss(bd(sl));
        if (tp != null && !tp.isBlank() && !tp.equals("0"))
            t.setTakeProfit(bd(tp));
        t.setEntryTime(parseDateTime(openTime));
        t.setExitTime(closeTime != null ? parseDateTime(closeTime) : t.getEntryTime());
        t.setExchange("MetaTrader");
        t.setNotes("Imported from MT4/MT5 — " + sourceFile);
        t.setPnlAmount(bd(profit));
        t.computePnL();
        return t;
    }

    // ── Interactive Brokers ───────────────────────────────────
    // Key columns: Symbol, Date/Time, Quantity, T. Price, Realized P/L, Comm/Fee
    private Trade parseIbRow(String[] header, String[] row,
                             UserProfile profile, String sourceFile) {
        Map<String, String> m = mapRow(header, row);
        // IB reports have multi-type rows; look for "Trades","Data","Order"
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
        t.setProfile(profile);
        t.setSymbol(clean(symbol).toUpperCase());
        t.setAssetName(clean(symbol).toUpperCase());
        t.setAssetType(detectAssetType(symbol));
        t.setDirection(qtyBd.compareTo(BigDecimal.ZERO) > 0
                ? TradeDirection.LONG : TradeDirection.SHORT);
        t.setStatus(TradeStatus.CLOSED);
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

    // ── Generic CSV ───────────────────────────────────────────
    private Trade parseGenericRow(String[] header, String[] row,
                                  UserProfile profile, String sourceFile) {
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
        t.setProfile(profile);
        t.setSymbol(clean(symbol).toUpperCase());
        t.setAssetName(clean(symbol).toUpperCase());
        t.setAssetType(detectAssetType(symbol));
        t.setDirection(side != null && (side.toLowerCase().contains("sell") ||
                side.toLowerCase().contains("short"))
                ? TradeDirection.SHORT : TradeDirection.LONG);
        t.setStatus(exit != null && !exit.isBlank() ? TradeStatus.CLOSED : TradeStatus.OPEN);
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

    // ── Utilities ─────────────────────────────────────────────

    private List<String[]> readCsv(File file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue;
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
    }

    /** Respects quoted fields containing commas. */
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

    /** Strips trailing alphabetic suffix from a numeric+alpha string e.g. "0.012BTC" → "0.012" */
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
                    // try as date only → midnight
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
        if (s.endsWith("USDT") || s.endsWith("BTC") || s.endsWith("ETH") ||
                s.endsWith("BNB") || s.endsWith("BUSD") || s.contains("USD") && s.length() <= 7)
            return AssetType.CRYPTO;
        if (s.contains("USD") || s.contains("EUR") || s.contains("GBP") ||
                s.contains("JPY") || s.contains("AUD") || s.length() == 6)
            return AssetType.FOREX;
        return AssetType.STOCK;
    }
}
