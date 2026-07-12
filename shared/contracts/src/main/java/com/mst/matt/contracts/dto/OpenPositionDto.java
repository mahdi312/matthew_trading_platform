package com.mst.matt.contracts.dto;

import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.contracts.enums.OrderSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents an open futures or margin position held by a user on a broker.
 *
 * <p>Returned by {@link com.mst.matt.contracts.broker.trading.TradingProvider#getOpenPositions}.
 * Cross-service DTO — no JPA annotations.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenPositionDto {

    /** Broker holding this position. */
    private BrokerType brokerType;

    /** Platform user id owning the position. */
    private Long userId;

    /** Trading symbol (e.g., "BTCUSDT-PERP"). */
    private String symbol;

    /** Position direction. */
    private OrderSide side;

    /** Position size in base-asset units. */
    private BigDecimal size;

    /** Average entry price. */
    private BigDecimal entryPrice;

    /** Current mark / last price; used for unrealised PnL calculation. */
    private BigDecimal markPrice;

    /**
     * Unrealised profit and loss in quote-asset units.
     * Positive = profit, negative = loss.
     */
    private BigDecimal unrealisedPnl;

    /** Leverage applied to this position. */
    private Integer leverage;

    /** Liquidation price; {@code null} if not available from the broker. */
    private BigDecimal liquidationPrice;

    /** When the position was opened. */
    private Instant openedAt;
}
