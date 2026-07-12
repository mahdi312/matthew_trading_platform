package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoSearchResult {
    private List<CoinGeckoCoinSearchItem> coins;
    private List<CoinGeckoExchangeBasic> exchanges;
    private List<CoinGeckoCoinCategory> categories;
    private List<CoinGeckoNftBasic> nfts;
}
