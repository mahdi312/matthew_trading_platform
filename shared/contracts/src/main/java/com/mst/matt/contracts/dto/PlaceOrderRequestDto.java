package com.mst.matt.contracts.dto;

import com.mst.matt.contracts.enums.OrderSide;
import com.mst.matt.contracts.enums.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Cross-service DTO used to submit a new order via a
 * {@link com.mst.matt.contracts.broker.trading.TradingProvider}.
 *
 * <p>Contains all fields needed for both spot and futures orders.
 * Fields that are only relevant to futures (e.g., {@link #leverage}) are
 * {@code null} for spot orders.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceOrderRequestDto {

    // ── Identity ─────────────────────────────────────────────────────────────

    /** Platform user id submitting the order. */
    @NotNull
    private Long userId;

    // ── Instrument ───────────────────────────────────────────────────────────

    /** Trading symbol as understood by the target broker (e.g., "BTCUSDT"). */
    @NotBlank
    private String symbol;

    // ── Order parameters ─────────────────────────────────────────────────────

    /** Buy or sell. */
    @NotNull
    private OrderSide side;

    /** Execution type. */
    @NotNull
    private OrderType orderType;

    /** Quantity in base-asset units. */
    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "Quantity must be positive")
    private BigDecimal quantity;

    /**
     * Limit / stop price; {@code null} for {@link com.mst.matt.contracts.enums.OrderType#MARKET}.
     */
    private BigDecimal price;

    /**
     * Stop trigger price; required for STOP and STOP_LIMIT order types.
     */
    private BigDecimal stopPrice;

    /**
     * Trailing offset (absolute or percentage depending on broker) for
     * {@link com.mst.matt.contracts.enums.OrderType#TRAILING_STOP} orders.
     */
    private BigDecimal trailingOffset;

    // ── Futures-specific ──────────────────────────────────────────────────────

    /**
     * Leverage multiplier for futures orders; {@code null} for spot.
     * Broker must verify against its supported max leverage.
     */
    private Integer leverage;

    /**
     * Whether this is a reduce-only order (closes an existing position
     * without opening a new one); futures only.
     */
    private boolean reduceOnly;

    // ── Client metadata ───────────────────────────────────────────────────────

    /**
     * Optional client-assigned order ID for idempotency.
     * If set, the broker implementation should pass it through.
     */
    private String clientOrderId;
}
