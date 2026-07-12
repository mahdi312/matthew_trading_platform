package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoEntity {
    private String id;
    private String name;
    private String symbol;
    private String country;
}
