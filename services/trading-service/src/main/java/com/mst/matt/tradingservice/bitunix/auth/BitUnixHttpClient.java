package com.mst.matt.tradingservice.bitunix.auth;

import com.google.gson.Gson;
import com.mst.matt.tradingservice.exception.BitUnixApiException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Thin HTTP transport layer for BitUnix's private REST API (Step 6.0).
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Injects the four required auth headers ({@code api-key}, {@code nonce},
 *       {@code timestamp}, {@code sign}) computed by {@link BitUnixSignatureService}.</li>
 *   <li>Serialises POST bodies to compact JSON (no whitespace) using the same
 *       {@link Gson} instance used to compute the signature — the string sent
 *       over the wire must be byte-for-byte identical to what was signed.</li>
 *   <li>Deserialises responses from JSON to caller-specified types.</li>
 *   <li>Maps non-zero BitUnix {@code code} fields to {@link BitUnixApiException}.</li>
 * </ul>
 *
 * <h3>What this class does NOT do</h3>
 * <ul>
 *   <li>No business logic — callers decide which endpoint, which body params.</li>
 *   <li>No rate limiting — rate limiting belongs in a future AOP interceptor
 *       or a token-bucket wrapper around the executor.</li>
 *   <li>No retry — okhttp's {@code retryOnConnectionFailure} handles transport
 *       retries; application-level idempotency is the caller's concern.</li>
 * </ul>
 */
@Slf4j
@Component
public class BitUnixHttpClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final BitUnixSignatureService signer;

    public BitUnixHttpClient(
            @Qualifier("tradingBitUnixHttpClient") OkHttpClient httpClient,
            @Qualifier("tradingGson") Gson gson,
            BitUnixSignatureService signer) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.signer = signer;
    }

    // ── Public GET ────────────────────────────────────────────────────────────

    /**
     * Execute a signed GET request against a BitUnix private endpoint.
     *
     * @param url         fully-constructed URL including query parameters
     * @param queryParams the same query params as in the URL, used for signing
     * @param credential  per-user API credential
     * @param responseType the expected response class
     * @return deserialized response body
     * @throws BitUnixApiException if the HTTP call fails or BitUnix returns a non-zero code
     */
    public <T> T get(String url, Map<String, String> queryParams,
                     BitUnixCredential credential, Class<T> responseType) {
        String queryParamString = signer.buildQueryParamString(queryParams);
        BitUnixSignatureService.SignedHeaders headers =
                signer.buildRestHeaders(credential, queryParamString, "");

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("api-key",    headers.apiKey())
                .addHeader("nonce",      headers.nonce())
                .addHeader("timestamp",  headers.timestamp())
                .addHeader("sign",       headers.sign())
                .addHeader("Content-Type", "application/json")
                .addHeader("language",   "en-US")
                .build();

        return execute(request, responseType);
    }

    // ── Public POST ───────────────────────────────────────────────────────────

    /**
     * Execute a signed POST request against a BitUnix private endpoint.
     *
     * <p>The {@code bodyObject} is serialised to compact JSON by {@link Gson}
     * (no spaces, no newlines). The <em>same</em> compact string is used for
     * signing and sent verbatim as the HTTP body.</p>
     *
     * @param url        the full endpoint URL (no query parameters for POST)
     * @param bodyObject the request payload (will be serialised to compact JSON)
     * @param credential per-user API credential
     * @param responseType the expected response class
     * @return deserialized response body
     * @throws BitUnixApiException if the HTTP call fails or BitUnix returns a non-zero code
     */
    public <T> T post(String url, Object bodyObject,
                      BitUnixCredential credential, Class<T> responseType) {
        // Compact JSON — toJson() produces no extraneous whitespace by default in Gson
        String compactJson = bodyObject != null ? gson.toJson(bodyObject) : "";

        BitUnixSignatureService.SignedHeaders headers =
                signer.buildRestHeaders(credential, "", compactJson);

        RequestBody body = RequestBody.create(compactJson, JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("api-key",    headers.apiKey())
                .addHeader("nonce",      headers.nonce())
                .addHeader("timestamp",  headers.timestamp())
                .addHeader("sign",       headers.sign())
                .addHeader("Content-Type", "application/json")
                .addHeader("language",   "en-US")
                .build();

        return execute(request, responseType);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private <T> T execute(Request request, Class<T> responseType) {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new BitUnixApiException(
                        "HTTP " + response.code() + " from BitUnix for " + request.url());
            }
            String rawBody = response.body().string();
            log.debug("BitUnix response [{}]: {}", request.url(), rawBody);

            T result = gson.fromJson(rawBody, responseType);

            // BitUnix wraps every response in {"code":0,"msg":"Success","data":...}
            // Check the code field via the BitUnixApiResponse wrapper if the caller
            // passes one; raw results from non-standard shapes skip this check.
            if (result instanceof BitUnixApiResponse<?> apiResponse) {
                if (apiResponse.getCode() != 0) {
                    throw new BitUnixApiException(
                            "BitUnix API error code=" + apiResponse.getCode()
                            + " msg=" + apiResponse.getMsg()
                            + " url=" + request.url());
                }
            }
            return result;
        } catch (IOException e) {
            throw new BitUnixApiException(
                    "Network error calling BitUnix " + request.url(), e);
        }
    }
}
