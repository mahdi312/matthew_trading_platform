package com.mst.matt.marketservice.bitunix;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * BitUnix REST request signing utility (Step 5.1).
 *
 * <h3>Signature algorithm (per BitUnix's official docs, verbatim)</h3>
 * <pre>
 * queryParams = sorted ascending by key, concatenated as "key1val1key2val2..." (no separators)
 * body        = compact JSON string, no spaces
 * digest      = SHA256(nonce + timestamp + api-key + queryParams + body)
 * sign        = SHA256(digest + secretKey)
 * </pre>
 *
 * <h3>Who needs this</h3>
 * <p>Kline and Tickers (Step 5.2's {@code BitUnixMarketDataProvider}) are
 * <strong>public, unauthenticated</strong> endpoints — they never call
 * {@link #sign}. This component only exists now so {@code trading-service}
 * (Step 6+) can sign BitUnix's private endpoints (order placement,
 * balances, positions) without duplicating the SHA-256 double-hash logic.</p>
 */
@Component
public class BitUnixSigner {

    /**
     * Computes the BitUnix REST signature for a request.
     *
     * @param nonce      random 32-char string (caller-generated, once per request)
     * @param timestamp  current unix time in milliseconds, as a string
     * @param apiKey     the caller's BitUnix API key
     * @param queryParams query parameters sorted ascending by key and concatenated
     *                    as "key1val1key2val2..." — see {@link #buildQueryParamString}
     *                    for a helper that produces this from a {@link Map}
     * @param body       compact (no-whitespace) JSON request body, or "" if none
     * @param secretKey  the caller's BitUnix secret key
     * @return the hex-encoded signature to send as the {@code sign} header
     */
    public String sign(String nonce, String timestamp, String apiKey,
                        String queryParams, String body, String secretKey) {
        String digest = sha256Hex(nonce + timestamp + apiKey + queryParams + body);
        return sha256Hex(digest + secretKey);
    }

    /**
     * Builds the {@code queryParams} component of the signature: all
     * parameters sorted ascending by key, concatenated as
     * {@code key1val1key2val2...} with no separators, per BitUnix's spec.
     *
     * @param params query parameters as submitted with the request (unsorted)
     * @return the concatenated, key-sorted string ready to feed into {@link #sign}
     */
    public String buildQueryParamString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        // TreeMap sorts keys ascending automatically.
        Map<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            sb.append(entry.getKey()).append(entry.getValue());
        }
        return sb.toString();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed available on every standard JVM; this is unreachable.
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
