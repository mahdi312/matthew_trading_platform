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
public class CoinGeckoVolumeChart {
    private List<List<Object>> volumes;
}
