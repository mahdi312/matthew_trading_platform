package com.mst.matt.referencedataservice.provider.calendar;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider;
import com.mst.matt.contracts.provider.dto.EconomicEventDto;
import com.mst.matt.referencedataservice.client.RefDataHttpClient;
import com.mst.matt.referencedataservice.config.RefDataProviderProperties;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Alpha Vantage implementation of {@link EconomicCalendarProvider}.
 *
 * <h3>Endpoints (both return CSV)</h3>
 * <ul>
 *   <li>{@code EARNINGS_CALENDAR} — upcoming earnings announcements</li>
 *   <li>{@code IPO_CALENDAR}      — upcoming IPO listings</li>
 * </ul>
 *
 * <p>Alpha Vantage does not have a general macro event calendar on the free tier.
 * This provider maps earnings and IPO events to {@link EconomicEventDto}.</p>
 *
 * <p>Uses a dedicated OkHttpClient (not RefDataHttpClient) since responses are CSV, not JSON.</p>
 */
@Component
public class AlphaVantageCalendarProvider implements EconomicCalendarProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageCalendarProvider.class);

    public static final String PROVIDER_NAME = "ALPHA_VANTAGE";
    private static final String BASE_URL     = "https://www.alphavantage.co/query";

    private final RefDataProviderProperties keys;
    private final OkHttpClient csvClient;

    public AlphaVantageCalendarProvider(RefDataProviderProperties keys) {
        this.keys = keys;
        this.csvClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK, AssetClass.FOREX, AssetClass.CRYPTO);
    }

    // ── EconomicCalendarProvider ──────────────────────────────────────────────

    @Override
    public List<EconomicEventDto> getUpcomingEvents(Instant from, Instant to, String impactLevel) {
        if (!keys.hasAlphavantageKey()) return List.of();
        List<EconomicEventDto> events = new ArrayList<>();
        events.addAll(fetchEarningsCalendar(from, to, impactLevel));
        events.addAll(fetchIpoCalendar(from, to));
        events.sort((a, b) -> {
            if (a.getScheduledAt() == null) return 1;
            if (b.getScheduledAt() == null) return -1;
            return a.getScheduledAt().compareTo(b.getScheduledAt());
        });
        return events;
    }

    @Override
    public List<EconomicEventDto> getHighImpactEvents(int days) {
        Instant from = Instant.now();
        Instant to   = from.plus(java.time.Duration.ofDays(days));
        return getUpcomingEvents(from, to, "HIGH");
    }

    @Override
    public List<EconomicEventDto> getHistoricalEvents(Instant from, Instant to) {
        // AV earnings calendar doesn't support past queries on free tier
        return List.of();
    }

    @Override
    public List<EconomicEventDto> getEventsByCountry(String countryCode, Instant from, Instant to) {
        // Earnings calendar is US-centric; return all for "US", empty for others
        if ("US".equalsIgnoreCase(countryCode)) return getUpcomingEvents(from, to, null);
        return List.of();
    }

    @Override
    public List<EconomicEventDto> getEventsByAssetClass(AssetClass assetClass, Instant from, Instant to) {
        if (assetClass == AssetClass.STOCK) return getUpcomingEvents(from, to, null);
        return List.of();
    }

    @Override
    public List<EconomicEventDto> getNextEventsByCategory(String category, String country) {
        Instant from = Instant.now();
        Instant to   = from.plus(java.time.Duration.ofDays(90));
        String catLower = category != null ? category.toLowerCase() : "";
        if (catLower.contains("earning") || catLower.contains("eps")) {
            return fetchEarningsCalendar(from, to, null);
        }
        if (catLower.contains("ipo")) {
            return fetchIpoCalendar(from, to);
        }
        return List.of();
    }

    // ── CSV helpers ───────────────────────────────────────────────────────────

    private List<EconomicEventDto> fetchEarningsCalendar(Instant from, Instant to,
                                                           String impactLevel) {
        String url = BASE_URL + "?function=EARNINGS_CALENDAR&horizon=3month"
                + "&apikey=" + keys.getAlphavantageKey();
        String csv = fetchCsv(url);
        if (csv == null) return List.of();

        List<EconomicEventDto> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length < 4) continue;
                // symbol, name, reportDate, fiscalDateEnding, estimate, currency
                String symbol     = safeGet(parts, 0);
                String name       = safeGet(parts, 1);
                String reportDate = safeGet(parts, 2);
                String estimate   = safeGet(parts, 4);

                Instant scheduledAt = parseDate(reportDate);
                if (from != null && scheduledAt != null && scheduledAt.isBefore(from)) continue;
                if (to   != null && scheduledAt != null && scheduledAt.isAfter(to)) continue;

                events.add(EconomicEventDto.builder()
                        .eventId("AV_EARN_" + symbol + "_" + reportDate)
                        .providerName(PROVIDER_NAME)
                        .title("Earnings: " + (name != null ? name : symbol))
                        .description("Quarterly earnings announcement for " + symbol)
                        .country("US")
                        .category("EARNINGS")
                        .scheduledAt(scheduledAt)
                        .isTimeTentative(true)
                        .impactLevel("HIGH")
                        .affectedAssetClass(AssetClass.STOCK)
                        .affectedCurrency("USD")
                        .forecast(estimate)
                        .isRecurring(true)
                        .build());
            }
        } catch (IOException e) {
            log.warn("AV earnings CSV parse error: {}", e.getMessage());
        }
        return events;
    }

    private List<EconomicEventDto> fetchIpoCalendar(Instant from, Instant to) {
        String url = BASE_URL + "?function=IPO_CALENDAR&apikey=" + keys.getAlphavantageKey();
        String csv = fetchCsv(url);
        if (csv == null) return List.of();

        List<EconomicEventDto> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length < 3) continue;
                // symbol, name, ipoDate, priceRangeLow, priceRangeHigh, currency, exchange
                String symbol   = safeGet(parts, 0);
                String name     = safeGet(parts, 1);
                String ipoDate  = safeGet(parts, 2);
                String priceRange = safeGet(parts, 3) + "-" + safeGet(parts, 4);

                Instant scheduledAt = parseDate(ipoDate);
                if (from != null && scheduledAt != null && scheduledAt.isBefore(from)) continue;
                if (to   != null && scheduledAt != null && scheduledAt.isAfter(to)) continue;

                events.add(EconomicEventDto.builder()
                        .eventId("AV_IPO_" + symbol + "_" + ipoDate)
                        .providerName(PROVIDER_NAME)
                        .title("IPO: " + (name != null ? name : symbol))
                        .description("Initial Public Offering for " + symbol
                                + " (price range: " + priceRange + ")")
                        .country("US")
                        .category("IPO")
                        .scheduledAt(scheduledAt)
                        .isTimeTentative(true)
                        .impactLevel("MEDIUM")
                        .affectedAssetClass(AssetClass.STOCK)
                        .affectedCurrency("USD")
                        .build());
            }
        } catch (IOException e) {
            log.warn("AV IPO CSV parse error: {}", e.getMessage());
        }
        return events;
    }

    private String fetchCsv(String url) {
        try (Response resp = csvClient.newCall(
                new Request.Builder().url(url).get().build()).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            String body = resp.body().string();
            // AV error responses start with '{' (JSON)
            return body.startsWith("{") ? null : body;
        } catch (IOException e) {
            log.warn("AV CSV fetch error: {}", e.getMessage());
            return null;
        }
    }

    private static Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /** Naive CSV parser — handles quoted fields. */
    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString().trim());
        return fields.toArray(new String[0]);
    }

    private static String safeGet(String[] arr, int idx) {
        if (idx >= arr.length) return null;
        String s = arr[idx].trim();
        return s.isEmpty() ? null : s;
    }
}
