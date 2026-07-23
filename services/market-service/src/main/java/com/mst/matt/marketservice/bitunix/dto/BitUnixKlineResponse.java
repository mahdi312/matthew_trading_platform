package com.mst.matt.marketservice.bitunix.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Raw JSON envelope returned by BitUnix's
 * {@code GET /api/v1/futures/market/kline} endpoint:
 * {@code {"code": 0, "data": [...], "msg": "Success"}}.
 *
 * <p>{@code code == 0} indicates success; any other value is a BitUnix-side
 * error and {@code msg} carries the human-readable reason.</p>
 */
@Data
@NoArgsConstructor
public class BitUnixKlineResponse {

    private int code;
    private List<BitUnixKlineItem> data;
    private String msg;
}
