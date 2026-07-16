package com.mst.matt.marketservice.bitunix.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Raw JSON envelope returned by BitUnix's
 * {@code GET /api/v1/futures/market/tickers} endpoint:
 * {@code {"code": 0, "data": [...], "msg": "Success"}}.
 */
@Data
@NoArgsConstructor
public class BitUnixTickerResponse {

    private int code;
    private List<BitUnixTickerItem> data;
    private String msg;
}
