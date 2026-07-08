package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;
import org.junit.jupiter.api.Test;

import static com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass.*;
import static org.assertj.core.api.Assertions.assertThat;

class AssetClassDetectorTest {

    @Test
    void detectRoutesCryptoForexCommoditiesIndicesAndStocks() {
        assertThat(AssetClassDetector.detect("btc/usdt")).isEqualTo(CRYPTO);
        assertThat(AssetClassDetector.detect("EURUSD")).isEqualTo(FOREX);
        assertThat(AssetClassDetector.detect("GC=F")).isEqualTo(COMMODITY);
        assertThat(AssetClassDetector.detect("gold")).isEqualTo(COMMODITY);
        assertThat(AssetClassDetector.detect("^GSPC")).isEqualTo(INDEX);
        assertThat(AssetClassDetector.detect("spx")).isEqualTo(INDEX);
        assertThat(AssetClassDetector.detect("AAPL")).isEqualTo(STOCK);
    }

    @Test
    void profileFocusOverridesDetectionExceptMultiAndNull() {
        assertThat(AssetClassDetector.fromProfileFocus(ProfileAssetFocus.STOCK, "BTCUSDT"))
                .isEqualTo(STOCK);
        assertThat(AssetClassDetector.fromProfileFocus(ProfileAssetFocus.CRYPTO, "AAPL"))
                .isEqualTo(CRYPTO);
        assertThat(AssetClassDetector.fromProfileFocus(ProfileAssetFocus.MULTI, "OIL"))
                .isEqualTo(COMMODITY);
        assertThat(AssetClassDetector.fromProfileFocus(null, "NASDAQ"))
                .isEqualTo(INDEX);
    }
}
