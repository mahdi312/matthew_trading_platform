package com.mst.matt.contracts.enums;

/**
 * Evaluation condition for a {@code PriceAlert} (owned by {@code alert-service}).
 *
 * <p>Kept in {@code shared/contracts} (rather than inside {@code alert-service}
 * only) because the condition is echoed on the cross-service
 * {@code AlertTriggeredEventDto} published to Kafka and consumed by other
 * services (e.g., {@code notification-service} in Step 7).</p>
 */
public enum AlertCondition {

    /** Fires when the live price rises to or above the alert's target value. */
    ABOVE,

    /** Fires when the live price falls to or below the alert's target value. */
    BELOW,

    /**
     * Fires when the absolute 24h percentage change reaches or exceeds the
     * alert's target value (expressed as a percentage, e.g. {@code 5} = 5%).
     */
    PERCENT_CHANGE
}
