package com.mst.matt.tradingservice.bitunix.futures.dto;

import com.mst.matt.tradingservice.bitunix.auth.BitUnixApiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Response for {@code POST /api/v1/futures/trade/cancel_orders}.
 *
 * <p>BitUnix returns a split result: orders that were accepted for cancellation
 * go into {@code successList}; orders that could not be cancelled (e.g., already
 * filled, already cancelled) go into {@code failureList} with error details.
 * Per BitUnix's own docs: HTTP 200 ≠ guaranteed cancellation — confirm via the
 * WebSocket order channel.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FuturesCancelOrderResult extends BitUnixApiResponse<FuturesCancelOrderResult.CancelData> {

    @Data
    public static class CancelData {
        private List<SuccessEntry> successList;
        private List<FailureEntry> failureList;
    }

    @Data
    public static class SuccessEntry {
        private String orderId;
        private String clientId;
    }

    @Data
    public static class FailureEntry {
        private String orderId;
        private String clientId;
        private String errorMsg;
        private int errorCode;
    }
}
