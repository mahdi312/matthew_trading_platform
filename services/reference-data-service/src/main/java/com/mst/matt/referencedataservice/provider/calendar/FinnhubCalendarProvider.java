package com.mst.matt.referencedataservice.provider.calendar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider;
import com.mst.matt.contracts.provider.dto.EconomicEventDto;
import com.mst.matt.referencedataservice.client.JsonUtil;
import com.mst.matt.referencedataservice.client.RefDataHttpClient;
import com.mst.matt.referencedataservice.config.RefDataProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finnhub implementation of {@link EconomicCalendarProvider}.
 *
 * <h3>Endpoints used</h3>
 * <ul>
 *   <li>{@code GET /calendar/earnings?from=&to=} — earnings announcements
 *       (returns {@code {"earningsCalendar":[…]}})</li>
 *   <li>{@code GET /calendar/economic?from=&to=} — macro economic events
 *       (returns {@code {"economicCalendar":[…]}})</li>
 * </ul>
 *
 * <p>Throttle key {@code "finnhub"} is registered by
 * {@link com.mst.matt.referencedataservice.provider.fundamentals.FinnhubFundamentalsProvider}
 * at Spring startup; this provider relies on that registration.</p>
 */
@Component
public class FinnhubCalendarProvider implements EconomicCalendarProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubCalendarProvider.class);

    public static final String PROVIDER_NAME = "FINNHUB";
    private static final String BASE_URL     = "https://finnhub.io/api/v1";
    private static final String THROTTLE_KEY = "finnhub";
    private static final String UA           = "reference-data-service/1.0";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RefDataHttpClient http;
    private final RefDataProviderProperties keys;

    public FinnhubCalendarProvider(RefDataHttpClient http, RefDataProviderProperties keys) {
        this.http  = http;
        this.keys  = keys;
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK, AssetClass.FOREX, AssetClass.CRYPTO);
    }

    // ── EconomicCalendarProvider ──────────────────────────────────────────────

    @Override
    public List<EconomicEventDto> getUpcomingEvents(Instant from, Instant to, String impactLevel) {
        if (!keys.hasFinnhubKey()) return List.of();
        List<EconomicEventDto> events = new ArrayList<>();
        events.addAll(fetchEarningsCalendar(from, to));
        events.addAll(fetchEconomicCalendar(from, to));

        // Apply optional impact filter
        if (impactLevel != null && !impactLevel.isBlank()) {
            events.removeIf(e -> !impactLevel.equalsIgnoreCase(e.getImpactLevel()));
        }

        events.sort(Comparator.comparing(
                EconomicEventDto::getScheduledAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
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
        if (!keys.hasFinnhubKey()) return List.of();
        // Economic calendar can include past events; earnings also covers past
        List<EconomicEventDto> events = new ArrayList<>();
        events.addAll(fetchEarningsCalendar(from, to));
        events.addAll(fetchEconomicCalendar(from, to));
        events.removeIf(e -> !e.isReleased());
        events.sort(Comparator.comparing(
                EconomicEventDto::getActualReleasedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return events;
    }

    @Override
    public List<EconomicEventDto> getEventsByCountry(String countryCode, Instant from, Instant to) {
        return getUpcomingEvents(from, to, null).stream()
                .filter(e -> countryCode != null && countryCode.equalsIgnoreCase(e.getCountry()))
                .toList();
    }

    @Override
    public List<EconomicEventDto> getEventsByAssetClass(AssetClass assetClass, Instant from, Instant to) {
        return getUpcomingEvents(from, to, null).stream()
                .filter(e -> e.getAffectedAssetClasses().contains(assetClass))
                .toList();
    }

    @Override
    public List<EconomicEventDto> getNextEventsByCategory(String category, String country) {
        Instant from = Instant.now();
        Instant to   = from.plus(java.time.Duration.ofDays(90));
        String catLower = category != null ? category.toLowerCase() : "";
        return getUpcomingEvents(from, to, null).stream()
                .filter(e -> e.getCategory() != null
                        && e.getCategory().toLowerCase().contains(catLower))
                .filter(e -> country == null || country.equalsIgnoreCase(e.getCountry()))
                .toList();
    }

    // ── Private fetchers ──────────────────────────────────────────────────────

    /**
     * Fetch earnings calendar from Finnhub.
     * Response shape: {@code {"earningsCalendar":[{"symbol","date","epsEstimate",
     * "epsActual","revenueEstimate","revenueActual","quarter","year"}]}}
     */
    private List<EconomicEventDto> fetchEarningsCalendar(Instant from, Instant to) {
        String url = BASE_URL + "/calendar/earnings"
                + "?from=" + toDateStr(from)
                + "&to="   + toDateStr(to)
                + "&token=" + keys.getFinnhubKey();

        return http.getJson(url, UA, THROTTLE_KEY).map(root -> {
            List<EconomicEventDto> list = new ArrayList<>();
            JsonElement arr = root.get("earningsCalendar");
            if (arr == null || !arr.isJsonArray()) return list;

            for (JsonElement el : arr.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();

                String symbol       = JsonUtil.str(item, "symbol");
                String date         = JsonUtil.str(item, "date");
                String epsEstimate  = JsonUtil.str(item, "epsEstimate");
                String epsActual    = JsonUtil.str(item, "epsActual");
                String revEstimate  = JsonUtil.str(item, "revenueEstimate");
                String revActual    = JsonUtil.str(item, "revenueActual");

                Instant scheduledAt = parseDate(date);
                boolean released    = epsActual != null;

                String surprise = null;
                if (epsActual != null && epsEstimate != null) {
                    try {
                        double actual   = Double.parseDouble(epsActual);
                        double estimate = Double.parseDouble(epsEstimate);
                        surprise = actual > estimate ? "BETTER_THAN_EXPECTED"
                                : actual < estimate ? "WORSE_THAN_EXPECTED"
                                : "IN_LINE";
                    } catch (NumberFormatException ignored) {
                        // leave surprise null
                    }
                }

                String forecastStr = epsEstimate != null
                        ? "EPS: " + epsEstimate + (revEstimate != null ? ", Rev: " + revEstimate : "")
                        : null;
                String actualStr = epsActual != null
                        ? "EPS: " + epsActual + (revActual != null ? ", Rev: " + revActual : "")
                        : null;

                list.add(EconomicEventDto.builder()
                        .eventId("FH_EARN_" + symbol + "_" + date)
                        .providerName(PROVIDER_NAME)
                        .title("Earnings: " + symbol)
                        .description("Quarterly earnings report for " + symbol)
                        .country("US")
                        .category("EARNINGS")
                        .scheduledAt(scheduledAt)
                        .actualReleasedAt(released ? scheduledAt : null)
                        .isTimeTentative(true)
                        .impactLevel("HIGH")
                        .affectedAssetClass(AssetClass.STOCK)
                        .affectedCurrency("USD")
                        .forecast(forecastStr)
                        .actual(actualStr)
                        .surprise(surprise)
                        .isRecurring(true)
                        .isReleased(released)
                        .build());
            }
            return list;
        }).orElse(List.of());
    }

    /**
     * Fetch macroeconomic calendar from Finnhub.
     * Response shape: {@code {"economicCalendar":[{"country","event","impact",
     * "time","actual","prev","estimate","unit"}]}}
     */
    private List<EconomicEventDto> fetchEconomicCalendar(Instant from, Instant to) {
        String url = BASE_URL + "/calendar/economic"
                + "?from=" + toDateStr(from)
                + "&to="   + toDateStr(to)
                + "&token=" + keys.getFinnhubKey();

        return http.getJson(url, UA, THROTTLE_KEY).map(root -> {
            List<EconomicEventDto> list = new ArrayList<>();
            JsonElement arr = root.get("economicCalendar");
            if (arr == null || !arr.isJsonArray()) return list;

            for (JsonElement el : arr.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();

                String country   = JsonUtil.str(item, "country");
                String eventName = JsonUtil.str(item, "event");
                String impact    = JsonUtil.str(item, "impact");
                String time      = JsonUtil.str(item, "time");
                String actual    = JsonUtil.str(item, "actual");
                String prev      = JsonUtil.str(item, "prev");
                String estimate  = JsonUtil.str(item, "estimate");
                String unit      = JsonUtil.str(item, "unit");

                Instant scheduledAt = parseEpochOrDate(item, "time", time);
                boolean released    = actual != null;

                String impactLevel = mapImpact(impact);
                String category    = inferCategory(eventName);

                String surprise = null;
                if (actual != null && estimate != null) {
                    try {
                        double act = Double.parseDouble(actual);
                        double est = Double.parseDouble(estimate);
                        surprise = act > est ? "BETTER_THAN_EXPECTED"
                                : act < est ? "WORSE_THAN_EXPECTED"
                                : "IN_LINE";
                    } catch (NumberFormatException ignored) {
                        // leave null
                    }
                }

                String forecastStr = estimate != null
                        ? estimate + (unit != null ? " " + unit : "")
                        : null;
                String actualStr   = actual != null
                        ? actual + (unit != null ? " " + unit : "")
                        : null;
                String prevStr     = prev != null
                        ? prev + (unit != null ? " " + unit : "")
                        : null;

                list.add(EconomicEventDto.builder()
                        .eventId("FH_ECO_" + country + "_"
                                + (eventName != null ? eventName.replace(" ", "_") : "UNKNOWN")
                                + "_" + time)
                        .providerName(PROVIDER_NAME)
                        .title(eventName)
                        .country(country)
                        .category(category)
                        .scheduledAt(scheduledAt)
                        .actualReleasedAt(released ? scheduledAt : null)
                        .isTimeTentative(false)
                        .impactLevel(impactLevel)
                        .affectedAssetClass(AssetClass.FOREX)
                        .affectedAssetClass(AssetClass.STOCK)
                        .affectedCurrency(countryToCurrency(country))
                        .forecast(forecastStr)
                        .previous(prevStr)
                        .actual(actualStr)
                        .surprise(surprise)
                        .isRecurring(true)
                        .isReleased(released)
                        .build());
            }
            return list;
        }).orElse(List.of());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String toDateStr(Instant instant) {
        if (instant == null) return LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
        return instant.atZone(ZoneOffset.UTC).toLocalDate().format(DATE_FMT);
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

    /** Finnhub economic events carry a Unix-epoch "time" field. */
    private static Instant parseEpochOrDate(JsonObject obj, String key, String fallback) {
        try {
            long epoch = obj.get(key).getAsLong();
            if (epoch > 1_000_000_000L) return Instant.ofEpochSecond(epoch);
        } catch (Exception ignored) {
            // fall through to date parse
        }
        return parseDate(fallback);
    }

    /** Map Finnhub impact string → platform impact level. */
    private static String mapImpact(String impact) {
        if (impact == null) return "MEDIUM";
        return switch (impact.toLowerCase()) {
            case "high", "3"   -> "HIGH";
            case "medium", "2" -> "MEDIUM";
            case "low", "1"    -> "LOW";
            default            -> "MEDIUM";
        };
    }

    /** Derive a category string from the event name. */
    private static String inferCategory(String name) {
        if (name == null) return "ECONOMIC_DATA";
        String n = name.toLowerCase();
        if (n.contains("cpi") || n.contains("inflation"))         return "INFLATION";
        if (n.contains("gdp"))                                    return "GDP";
        if (n.contains("unemployment") || n.contains("payroll")
                || n.contains("nfp") || n.contains("jobs"))       return "EMPLOYMENT";
        if (n.contains("rate") || n.contains("fomc")
                || n.contains("fed") || n.contains("central bank")) return "CENTRAL_BANK";
        if (n.contains("trade") || n.contains("balance"))         return "TRADE";
        if (n.contains("retail"))                                  return "RETAIL_SALES";
        if (n.contains("housing") || n.contains("home"))          return "HOUSING";
        if (n.contains("pmi") || n.contains("manufacturing"))     return "PMI";
        return "ECONOMIC_DATA";
    }

    /** Map country code → most relevant ISO-4217 currency. */
    private static String countryToCurrency(String country) {
        if (country == null) return null;
        return switch (country.toUpperCase()) {
            case "US" -> "USD";
            case "EU" -> "EUR";
            case "GB" -> "GBP";
            case "JP" -> "JPY";
            case "CA" -> "CAD";
            case "AU" -> "AUD";
            case "CH" -> "CHF";
            case "CN" -> "CNY";
            default   -> null;
        };
    }
}
