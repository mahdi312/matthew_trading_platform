package com.mst.matt.contracts.provider.registry;

/**
 * Thrown by {@link ProviderRegistry#executeWithFallback} when all providers
 * in the fallback chain have failed or have open circuit breakers.
 */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String message) {
        super(message);
    }

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
