package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.service.auth.AuthService;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds ordered provider chains per asset class, honouring:
 * <ol>
 *   <li>API key availability (providers without a key are skipped)</li>
 *   <li>Profile preference (preferred provider is moved to the front)</li>
 *   <li><b>User plan (role) restrictions</b> – REGULAR users can only use free providers;
 *       PRO users have most providers; ADMIN / PRO_PLUS have all providers.</li>
 * </ol>
 */
@Component
public class PriceProviderRegistry {

    private final Map<MarketDataProvider, PriceService> byId = new EnumMap<>(MarketDataProvider.class);
    private final List<PriceService> allServices;
    private final AuthService authService;

    public PriceProviderRegistry(List<PriceService> services,
                                  MarketApiProperties keys,
                                  AuthService authService) {
        this.authService = authService;
        this.allServices = services.stream()
                .filter(PriceService::isEnabled)
                .toList();
        for (PriceService s : allServices) {
            byId.put(s.getProviderId(), s);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the ordered provider chain for the given symbol + profile,
     * filtered by the currently-logged-in user's plan.
     */
    public List<PriceService> chainFor(String symbol, UserProfile profile) {
        String s = SymbolNormalizer.normalize(symbol);
        AssetClass asset = profile != null
                ? AssetClassDetector.fromProfileFocus(profile.getAssetFocus(), s)
                : AssetClassDetector.detect(s);

        MarketDataProvider preferred = profile != null
                ? MarketDataProvider.fromString(profile.getChartProvider())
                : MarketDataProvider.AUTO;

        List<MarketDataProvider> order = defaultOrder(asset);
        if (preferred != MarketDataProvider.AUTO) {
            order = prioritize(order, preferred);
        }

        // Filter by user-plan restrictions
        AppUser.Role role = currentRole();

        return order.stream()
                .filter(p -> role.canUseProvider(p.name()))
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(p -> p.supports(s))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Returns the list of providers enabled for the given asset class AND
     * allowed by the current user's plan.
     */
    public List<MarketDataProvider> enabledProvidersFor(AssetClass asset) {
        AppUser.Role role = currentRole();
        return defaultOrder(asset).stream()
                .filter(byId::containsKey)
                .filter(p -> role.canUseProvider(p.name()))
                .toList();
    }

    /**
     * Returns the list of providers enabled for the given asset class AND
     * allowed by a specific role. Used by the admin panel to preview restrictions.
     */
    public List<MarketDataProvider> enabledProvidersFor(AssetClass asset, AppUser.Role role) {
        AppUser.Role effectiveRole = role != null ? role : AppUser.Role.REGULAR_USER;
        return defaultOrder(asset).stream()
                .filter(byId::containsKey)
                .filter(p -> effectiveRole.canUseProvider(p.name()))
                .toList();
    }

    /**
     * Returns {@code true} if the current user's plan allows the named provider.
     * AUTO is always allowed.
     */
    public boolean isProviderAllowedForCurrentUser(String providerName) {
        return currentRole().canUseProvider(providerName);
    }

    public Optional<PriceService> get(MarketDataProvider id) {
        return Optional.ofNullable(byId.get(id));
    }

    public String lastSuccessfulProviderName() {
        return PriceRouter.getLastProviderName();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the current user's role, falling back to REGULAR_USER if not logged in. */
    private AppUser.Role currentRole() {
        return authService.currentUser()
                .map(AppUser::getRole)
                .orElse(AppUser.Role.REGULAR_USER);
    }

    private static List<MarketDataProvider> defaultOrder(AssetClass asset) {
        return switch (asset) {
            case CRYPTO -> List.of(
                    MarketDataProvider.BINANCE,
                    MarketDataProvider.COINGECKO,
                    MarketDataProvider.TWELVE_DATA,
                    MarketDataProvider.ALPHA_VANTAGE,
                    MarketDataProvider.FINNHUB,
                    MarketDataProvider.YAHOO);
            case FOREX -> List.of(
                    MarketDataProvider.FRANKFURTER,
                    MarketDataProvider.FIXER,
                    MarketDataProvider.FREE_CURRENCY_API,
                    MarketDataProvider.OPEN_EXCHANGE_RATES,
                    MarketDataProvider.EXCHANGE_RATE_API,
                    MarketDataProvider.CURRENCY_LAYER,
                    MarketDataProvider.TWELVE_DATA,
                    MarketDataProvider.ALPHA_VANTAGE,
                    MarketDataProvider.FINNHUB,
                    MarketDataProvider.YAHOO);
            case STOCK -> List.of(
                    MarketDataProvider.YAHOO,
                    MarketDataProvider.FINNHUB,
                    MarketDataProvider.POLYGON,
                    MarketDataProvider.ALPHA_VANTAGE,
                    MarketDataProvider.TWELVE_DATA,
                    MarketDataProvider.MARKETSTACK,
                    MarketDataProvider.BINANCE,
                    MarketDataProvider.COINGECKO);
            // T-15: Yahoo handles commodity futures (=F) and indices (^) natively.
            case COMMODITY -> List.of(
                    MarketDataProvider.YAHOO,
                    MarketDataProvider.TWELVE_DATA,
                    MarketDataProvider.ALPHA_VANTAGE,
                    MarketDataProvider.FINNHUB);
            case INDEX -> List.of(
                    MarketDataProvider.YAHOO,
                    MarketDataProvider.TWELVE_DATA,
                    MarketDataProvider.ALPHA_VANTAGE,
                    MarketDataProvider.FINNHUB);
        };
    }

    private static List<MarketDataProvider> prioritize(
            List<MarketDataProvider> order, MarketDataProvider preferred) {
        List<MarketDataProvider> result = new ArrayList<>();
        result.add(preferred);
        for (MarketDataProvider p : order) {
            if (p != preferred) result.add(p);
        }
        return result;
    }
}
