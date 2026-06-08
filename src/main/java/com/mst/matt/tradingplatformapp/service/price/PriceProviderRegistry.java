package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds ordered provider chains per asset class, honoring profile preference and API key availability.
 */
@Component
public class PriceProviderRegistry {

    private final Map<MarketDataProvider, PriceService> byId = new EnumMap<>(MarketDataProvider.class);
    private final List<PriceService> allServices;

    public PriceProviderRegistry(List<PriceService> services, MarketApiProperties keys) {
        this.allServices = services.stream()
                .filter(PriceService::isEnabled)
                .toList();
        for (PriceService s : allServices) {
            byId.put(s.getProviderId(), s);
        }
    }

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

        return order.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(p -> p.supports(s))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<MarketDataProvider> enabledProvidersFor(AssetClass asset) {
        return defaultOrder(asset).stream()
                .filter(byId::containsKey)
                .toList();
    }

    public Optional<PriceService> get(MarketDataProvider id) {
        return Optional.ofNullable(byId.get(id));
    }

    public String lastSuccessfulProviderName() {
        return PriceRouter.getLastProviderName();
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
