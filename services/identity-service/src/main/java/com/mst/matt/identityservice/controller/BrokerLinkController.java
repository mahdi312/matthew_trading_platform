package com.mst.matt.identityservice.controller;

import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.identityservice.dto.BrokerConnectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <b>STUB</b> — Broker API-key / OAuth linking controller.
 *
 * <p>This controller will allow authenticated users to connect their broker
 * accounts (via API key or OAuth2) to the platform, so that
 * {@code trading-service} can execute real trades on their behalf.</p>
 *
 * <h3>Planned full implementation (later step)</h3>
 * <ul>
 *   <li>Encrypt and store API key/secret pairs per user per broker.</li>
 *   <li>Support OAuth2-based broker connections (e.g., Coinbase OAuth2).</li>
 *   <li>Validate credentials against the broker's test-API before storing.</li>
 *   <li>Emit a {@code BrokerLinkedEvent} for trading-service to pick up.</li>
 *   <li>Support listing ({@code GET /auth/brokers}) and revoking
 *       ({@code DELETE /auth/brokers/{brokerType}}) connections.</li>
 * </ul>
 *
 * <p><b>DO NOT</b> implement broker-specific trading calls here.
 * Identity-service only issues identity and stores credentials —
 * actual trading goes through {@code trading-service}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/auth/brokers")
public class BrokerLinkController {

    /**
     * Stub: link a broker account to the currently authenticated user.
     *
     * <p>Currently returns {@code 501 Not Implemented} with a descriptive
     * payload. Replace this body with real credential encryption + storage
     * in the broker-link implementation step.</p>
     *
     * @param brokerType the broker to connect (path variable, must match {@link BrokerType})
     * @param request    API key / secret payload
     * @return 501 stub response
     */
    @PostMapping("/{brokerType}/connect")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> connectBroker(
            @PathVariable String brokerType,
            @RequestBody(required = false) BrokerConnectRequest request) {

        // Validate that the brokerType string is a known enum value
        BrokerType type;
        try {
            type = BrokerType.valueOf(brokerType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unknown broker type: " + brokerType,
                                 "supported", java.util.Arrays.toString(BrokerType.values())));
        }

        log.info("[STUB] Broker connect requested for broker={} (not yet implemented)", type);

        return ResponseEntity.status(501).body(Map.of(
                "status",  "NOT_IMPLEMENTED",
                "broker",  type.name(),
                "message", "Broker API-key linking is stubbed. "
                           + "Full implementation is planned in the broker-link step. "
                           + "Broker: " + type.name()
        ));
    }

    /**
     * Stub: list all broker connections for the current user.
     *
     * @return 501 stub response
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> listBrokerConnections() {
        log.info("[STUB] List broker connections requested (not yet implemented)");
        return ResponseEntity.status(501).body(Map.of(
                "status",  "NOT_IMPLEMENTED",
                "message", "Listing broker connections is planned in the broker-link step."
        ));
    }

    /**
     * Stub: revoke a specific broker connection.
     *
     * @param brokerType the broker connection to revoke
     * @return 501 stub response
     */
    @DeleteMapping("/{brokerType}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> revokeBrokerConnection(
            @PathVariable String brokerType) {
        log.info("[STUB] Broker revoke requested for broker={} (not yet implemented)", brokerType);
        return ResponseEntity.status(501).body(Map.of(
                "status",  "NOT_IMPLEMENTED",
                "message", "Revoking broker connections is planned in the broker-link step."
        ));
    }
}
