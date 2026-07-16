package com.mst.matt.contracts.dto;

import com.mst.matt.contracts.enums.BrokerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Asset balance for a single currency/asset on a broker account.
 *
 * <p>Returned (as part of a list) by
 * {@link com.mst.matt.contracts.broker.trading.TradingProvider#getBalances}.
 * Cross-service DTO — no JPA annotations.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceDto {

    /** Broker reporting this balance. */
    private BrokerType brokerType;

    /** Platform user id. */
    private Long userId;

    /** Asset / currency ticker (e.g., "USDT", "BTC"). */
    private String asset;

    /** Total balance (free + locked). */
    private BigDecimal total;

    /** Free (available for new orders) balance. */
    private BigDecimal free;

    /** Locked (in open orders or as collateral) balance. */
    private BigDecimal locked;
}
