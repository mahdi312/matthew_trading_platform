package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.service.auth.AuthService;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceProviderRegistryTest {

    private static AuthService authWithRole(AppUser.Role role) {
        AuthService auth = mock(AuthService.class);
        Optional<AppUser> user = role == null
                ? Optional.empty()
                : Optional.of(AppUser.builder().role(role).build());
        when(auth.currentUser()).thenReturn(user);
        return auth;
    }

    private static PriceService provider(MarketDataProvider id, boolean enabled, boolean supports) {
        PriceService service = mock(PriceService.class);
        when(service.getProviderId()).thenReturn(id);
        when(service.getProviderName()).thenReturn(id.getLabel());
        when(service.isEnabled()).thenReturn(enabled);
        when(service.supports(org.mockito.ArgumentMatchers.anyString())).thenReturn(supports);
        return service;
    }

    @Test
    void chainForFiltersDisabledUnsupportedAndRoleRestrictedProviders() {
        AuthService auth = authWithRole(AppUser.Role.REGULAR_USER);
        PriceService binance = provider(MarketDataProvider.BINANCE, true, true);
        PriceService coinGecko = provider(MarketDataProvider.COINGECKO, true, true);
        PriceService coinMarketCap = provider(MarketDataProvider.COINMARKETCAP, true, true);
        PriceService twelveData = provider(MarketDataProvider.TWELVE_DATA, true, true);
        PriceService yahooUnsupported = provider(MarketDataProvider.YAHOO, true, false);
        PriceService disabledFrankfurter = provider(MarketDataProvider.FRANKFURTER, false, true);
        PriceProviderRegistry registry = new PriceProviderRegistry(
                List.of(binance, coinGecko, coinMarketCap, twelveData, yahooUnsupported, disabledFrankfurter),
                null,
                auth);

        List<MarketDataProvider> providers = registry.chainFor("BTCUSDT", null).stream()
                .map(PriceService::getProviderId)
                .toList();

        assertThat(providers).containsExactly(MarketDataProvider.COINGECKO, MarketDataProvider.COINMARKETCAP);
    }

    @Test
    void profilePreferredProviderMovesAheadWhenAllowed() {
        AuthService auth = authWithRole(AppUser.Role.PRO_USER);
        PriceProviderRegistry registry = new PriceProviderRegistry(
                List.of(
                        provider(MarketDataProvider.YAHOO, true, true),
                        provider(MarketDataProvider.FINNHUB, true, true),
                        provider(MarketDataProvider.POLYGON, true, true)),
                null,
                auth);
        UserProfile profile = UserProfile.builder()
                .assetFocus(UserProfile.ProfileAssetFocus.STOCK)
                .chartProvider("FINNHUB")
                .build();

        List<MarketDataProvider> providers = registry.chainFor("AAPL", profile).stream()
                .map(PriceService::getProviderId)
                .toList();

        assertThat(providers).containsExactly(MarketDataProvider.FINNHUB, MarketDataProvider.YAHOO);
    }

    @Test
    void enabledProvidersForUsesExplicitRoleWithoutCurrentUser() {
        AuthService auth = authWithRole(null);
        PriceProviderRegistry registry = new PriceProviderRegistry(
                List.of(
                        provider(MarketDataProvider.YAHOO, true, true),
                        provider(MarketDataProvider.FINNHUB, true, true),
                        provider(MarketDataProvider.POLYGON, true, true)),
                null,
                auth);

        assertThat(registry.enabledProvidersFor(AssetClass.STOCK, AppUser.Role.ADMIN))
                .containsExactly(MarketDataProvider.YAHOO, MarketDataProvider.FINNHUB, MarketDataProvider.POLYGON);
        assertThat(registry.enabledProvidersFor(AssetClass.STOCK, AppUser.Role.REGULAR_USER))
                .containsExactly(MarketDataProvider.YAHOO);
    }

    @Test
    void getReturnsOnlyEnabledProvidersRegisteredById() {
        AuthService auth = authWithRole(AppUser.Role.ADMIN);
        PriceProviderRegistry registry = new PriceProviderRegistry(
                List.of(
                        provider(MarketDataProvider.YAHOO, true, true),
                        provider(MarketDataProvider.FINNHUB, false, true)),
                null,
                auth);

        assertThat(registry.get(MarketDataProvider.YAHOO)).isPresent();
        assertThat(registry.get(MarketDataProvider.FINNHUB)).isEmpty();
    }
}
