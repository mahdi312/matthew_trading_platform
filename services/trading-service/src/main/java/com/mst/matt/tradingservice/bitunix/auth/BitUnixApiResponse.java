package com.mst.matt.tradingservice.bitunix.auth;

import lombok.Data;

/**
 * Generic wrapper for all BitUnix REST responses.
 *
 * <p>Every BitUnix endpoint responds with the same envelope:</p>
 * <pre>
 * {
 *   "code": 0,
 *   "msg": "Success",
 *   "data": &lt;T&gt;
 * }
 * </pre>
 *
 * <p>A {@code code} of {@code 0} means success; any other value indicates an
 * error, and {@link com.mst.matt.tradingservice.bitunix.auth.BitUnixHttpClient}
 * will throw a {@link com.mst.matt.tradingservice.exception.BitUnixApiException}.</p>
 *
 * @param <T> the type of the {@code data} field
 */
@Data
public class BitUnixApiResponse<T> {

    /** {@code 0} = success; any other value = error. */
    private int code;

    /** Human-readable status message (e.g., {@code "Success"} or an error description). */
    private String msg;

    /** The response payload; type varies per endpoint. */
    private T data;

    /** Returns {@code true} if this response represents a successful API call. */
    public boolean isSuccess() {
        return code == 0;
    }
}
