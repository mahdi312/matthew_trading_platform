package com.mst.matt.tradingservice.bitunix.futures.dto;

import com.mst.matt.tradingservice.bitunix.auth.BitUnixApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response for {@code GET /api/v1/futures/position/get_pending_positions}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FuturesPendingPositionsResponse
        extends BitUnixApiResponse<List<FuturesPendingPositionsResponse.PositionData>> {

    @Data
    public static class PositionData {
        private String positionId;
        private String symbol;
        /** Position size in base-coin units. */
        private String qty;
        /** Total value of the position at entry. */
        private String entryValue;
        /** {@code "LONG"} or {@code "SHORT"}. */
        private String side;
        /** {@code "ISOLATION"} or {@code "CROSS"}. */
        private String marginMode;
        /** {@code "ONE_WAY"} or {@code "HEDGE"}. */
        private String positionMode;
        private String leverage;
        /** Accumulated funding fee. */
        private String funding;
        /** Accumulated trading fee. */
        private String fee;
        /** Realised PnL for this position. */
        private String realizedPNL;
        /** Current margin locked by this position. */
        private String margin;
        /** Unrealised PnL based on current mark price. */
        private String unrealizedPNL;
        /** Estimated liquidation price. */
        private String liqPrice;
        /** Margin ratio used (triggers liquidation at 100%). */
        private String marginRate;
        /** Average open price. */
        private String avgOpenPrice;
        /** Position creation timestamp (ms). */
        private long ctime;
        /** Position last-modified timestamp (ms). */
        private long mtime;
        private String subAccountId;
    }
}
