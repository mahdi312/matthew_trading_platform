package com.mst.matt.contracts.enums;

/**
 * Lifecycle status of a {@code PriceAlert} (owned by {@code alert-service}).
 */
public enum AlertStatus {

    /** Alert is armed and being evaluated against live price ticks. */
    ACTIVE,

    /**
     * Alert has fired at least once. One-shot alerts stay {@code TRIGGERED}
     * permanently; cooldown-based alerts return to {@code ACTIVE} once their
     * cooldown window elapses.
     */
    TRIGGERED,

    /** Alert was manually disabled by the user without deleting it. */
    PAUSED,

    /** Alert was cancelled/deleted; retained only for audit history if persisted. */
    CANCELLED
}
