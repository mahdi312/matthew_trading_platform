package com.mst.matt.tradingservice.bitunix.futures.dto;

import com.mst.matt.tradingservice.bitunix.auth.BitUnixApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response for {@code GET /api/v1/futures/account?marginCoin=USDT}.
 *
 * <p>The {@code data} field is a list (one entry per margin coin the user holds).</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FuturesAccountResponse extends BitUnixApiResponse<List<FuturesAccountResponse.AccountData>> {

    @Data
    public static class AccountData {
        /** Margin coin, e.g. {@code "USDT"}. */
        private String marginCoin;
        /** Available balance for new orders. */
        private String available;
        /** Locked by open orders. */
        private String frozen;
        /** Locked by open positions. */
        private String margin;
        /** Maximum transferable amount. */
        private String transfer;
        /** {@code "ONE_WAY"} or {@code "HEDGE"}. */
        private String positionMode;
        /** Unrealised PnL across cross-margin positions. */
        private String crossUnrealizedPNL;
        /** Unrealised PnL across isolated positions. */
        private String isolationUnrealizedPNL;
        /** Futures bonus balance (non-withdrawable). */
        private String bonus;
    }
}
