package com.mst.matt.tradingservice.bitunix.auth;

/**
 * Immutable value holder for a per-user BitUnix API credential pair.
 *
 * <p>Credentials are never stored in trading-service's own DB — they are
 * fetched from identity-service (or a future secrets store) and held
 * transiently for the duration of a single request call. Using a record
 * ensures the pair can never be mutated after construction.</p>
 *
 * @param apiKey    BitUnix API key
 * @param secretKey BitUnix secret key (used for HMAC signing only — never logged)
 */
public record BitUnixCredential(String apiKey, String secretKey) {

    public BitUnixCredential {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("BitUnix apiKey must not be blank");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("BitUnix secretKey must not be blank");
        }
    }
}
