package com.matthew.market.service;

import com.matthew.market.model.OHLCV;
import java.util.List;

public interface MarketDataService {
    List<OHLCV> getOHLCV(String symbol, String interval, long from, long to);
    Double getLivePrice(String symbol);
    String getProviderName();
}
