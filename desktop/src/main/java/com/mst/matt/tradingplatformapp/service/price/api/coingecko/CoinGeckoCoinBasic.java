package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoinGeckoCoinBasic {
    private String id;
    private String symbol;
    private String name;
    private Map<String, String> platforms;
}
