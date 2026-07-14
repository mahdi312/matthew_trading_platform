package com.mst.matt.tradingplatformapp.client;

import org.springframework.stereotype.Component;

/**
 * In-process JWT store for the desktop application.
 *
 * <p>After a successful login the {@code IdentityApiClient} writes the token
 * here.  Every other API client ({@code TradeApiClient}, {@code MarketApiClient},
 * etc.) reads it to attach {@code Authorization: Bearer <token>} to outgoing
 * gateway requests.</p>
 *
 * <p>This is intentionally a plain singleton Spring bean — the desktop app is
 * single-user, so no per-request scoping is needed.  On logout, call
 * {@link #clear()} to wipe the token.</p>
 */
@Component
public class TokenStore {

    private volatile String token;

    /** Returns the JWT, or {@code null} when not logged in. */
    public String getToken() {
        return token;
    }

    /** Sets (or replaces) the JWT after a successful login. */
    public void setToken(String token) {
        this.token = token;
    }

    /** Wipes the stored JWT (called on logout). */
    public void clear() {
        this.token = null;
    }

    /** Convenience: returns true when a token is present. */
    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
