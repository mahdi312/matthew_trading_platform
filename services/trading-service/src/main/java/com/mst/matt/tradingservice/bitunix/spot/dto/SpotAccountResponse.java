package com.mst.matt.tradingservice.bitunix.spot.dto;

import com.mst.matt.tradingservice.bitunix.auth.BitUnixApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response for {@code GET /api/spot/v1/user/account} (Step 6.5).
 *
 * <p>{@code data} is a list of coin balances — one entry per asset the user holds.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpotAccountResponse extends BitUnixApiResponse<List<SpotAccountResponse.CoinBalance>> {

    @Data
    public static class CoinBalance {
        /** Asset ticker, e.g. {@code "BTC"}, {@code "USDT"}. */
        private String coin;
        /** Total balance (free + locked). */
        private double balance;
        /** Amount locked in open orders ({@code balanceLocked}). */
        private double balanceLocked;
    }
}
