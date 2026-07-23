package com.mst.matt.tradingservice.bitunix.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * BitUnix request-signing service (Step 6.0).
 *
 * <h3>Signature algorithm (verbatim from BitUnix official docs)</h3>
 * <pre>
 * nonce       = random 32-char string, generated fresh per request
 * timestamp   = current UTC time in milliseconds (string)
 * queryParams = GET params sorted ascending by key, concatenated as
 *               "key1val1key2val2…" (no separator, no spaces)
 * body        = compact JSON string (no spaces); "" for GET / no-body requests
 *
 * digest = SHA256(nonce + timestamp + apiKey + queryParams + body)
 * sign   = SHA256(digest + secretKey)
 * </pre>
 *
 * <p>WebSocket login uses the same double-hash but feeds sorted WS params
 * instead of queryParams+body — see
 * {@link #signWsLogin(String, String, BitUnixCredential)}.</p>
 *
 * <p>This is a standalone Spring {@code @Component} — it holds no state and
 * contains no HTTP logic, so it can be injected into both
 * {@code BitUnixHttpClient} and the private WebSocket client without
 * any circular dependency risk.</p>
 */
@Component
public class BitUnixSignatureService {

    // ── Nonce / timestamp factories ───────────────────────────────────────────

    /**
     * Generates a fresh random 32-character nonce string (hex UUID, stripped of dashes).
     * A new nonce must be generated for <em>each</em> request — reuse is rejected.
     */
    public String generateNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Returns the current UTC time in milliseconds as a decimal string,
     * as required by BitUnix's auth headers.
     */
    public String generateTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    // ── REST signing ──────────────────────────────────────────────────────────

    /**
     * Computes the BitUnix REST signature for a private API request.
     *
     * @param nonce       fresh 32-char nonce (from {@link #generateNonce()})
     * @param timestamp   request timestamp ms string (from {@link #generateTimestamp()})
     * @param credential  per-user API key + secret
     * @param queryParams GET query params sorted ascending by key, concatenated
     *                    as {@code key1val1key2val2…} — use
     *                    {@link #buildQueryParamString} to produce this
     * @param body        compact (no-whitespace) JSON request body, or {@code ""}
     *                    for GET requests with no body
     * @return hex-encoded signature ready for the {@code sign} HTTP header
     */
    public String sign(String nonce, String timestamp,
                       BitUnixCredential credential,
                       String queryParams, String body) {
        String digest = sha256Hex(nonce + timestamp + credential.apiKey() + queryParams + body);
        return sha256Hex(digest + credential.secretKey());
    }

    /**
     * Builds the {@code queryParams} component required for signing GET requests:
     * all parameters sorted ascending by key, concatenated as
     * {@code key1val1key2val2…} with no separator characters.
     *
     * @param params query parameters exactly as they will appear in the URL
     * @return sorted-concatenated parameter string; empty string if params is null/empty
     */
    public String buildQueryParamString(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        // TreeMap auto-sorts keys ascending.
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(params).forEach((k, v) -> sb.append(k).append(v));
        return sb.toString();
    }

    /**
     * Convenience method: build a fully-populated {@link SignedHeaders} record
     * for a REST private call, generating a fresh nonce and timestamp internally.
     *
     * @param credential  per-user credential
     * @param queryParams sorted param string (use {@link #buildQueryParamString})
     * @param body        compact JSON body or {@code ""}
     * @return all four auth header values, ready to attach to the HTTP request
     */
    public SignedHeaders buildRestHeaders(BitUnixCredential credential,
                                         String queryParams,
                                         String body) {
        String nonce     = generateNonce();
        String timestamp = generateTimestamp();
        String sign      = sign(nonce, timestamp, credential, queryParams, body);
        return new SignedHeaders(credential.apiKey(), nonce, timestamp, sign);
    }

    // ── WebSocket login signing ───────────────────────────────────────────────

    /**
     * Computes the BitUnix WebSocket login signature.
     *
     * <p>Sorting rule for WS login: sort the login param names
     * {@code apiKey}, {@code nonce}, {@code timestamp} ascending (alphabetical)
     * and concatenate their values — same double-hash as REST.</p>
     *
     * @param nonce      fresh 32-char nonce
     * @param timestamp  timestamp ms string
     * @param credential per-user credential
     * @return hex-encoded sign for the {@code "op":"login"} WS frame
     */
    public String signWsLogin(String nonce, String timestamp, BitUnixCredential credential) {
        // Sorted params: apiKey < nonce < timestamp (alphabetical order)
        String params = credential.apiKey() + nonce + timestamp;
        String digest  = sha256Hex(nonce + timestamp + credential.apiKey() + params);
        return sha256Hex(digest + credential.secretKey());
    }

    // ── Internal SHA-256 helper ───────────────────────────────────────────────

    String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    // ── Signed headers value object ───────────────────────────────────────────

    /**
     * All four auth headers required by BitUnix private REST endpoints.
     *
     * @param apiKey    the {@code api-key} header value
     * @param nonce     the {@code nonce} header value
     * @param timestamp the {@code timestamp} header value
     * @param sign      the {@code sign} header value
     */
    public record SignedHeaders(String apiKey, String nonce, String timestamp, String sign) {}
}
