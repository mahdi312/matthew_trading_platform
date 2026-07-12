package com.mst.matt.identityservice.dto;

import lombok.Data;

/**
 * Request body for {@code POST /auth/brokers/{brokerType}/connect}.
 *
 * <p><b>Stub only</b> — field set will expand when the broker-link feature is
 * fully implemented. Currently accepts API-key credentials; OAuth2-based
 * broker flows will add a {@code redirectUri} field.</p>
 */
@Data
public class BrokerConnectRequest {

    /**
     * Exchange API key provided by the user from their broker account settings.
     * Stored encrypted; never returned in any response.
     */
    private String apiKey;

    /**
     * Exchange API secret paired with {@link #apiKey}.
     * Stored encrypted; never returned in any response.
     */
    private String apiSecret;

    /**
     * Optional label the user assigns to this broker connection
     * (e.g., "My Main BitUnix Account").
     */
    private String label;
}
