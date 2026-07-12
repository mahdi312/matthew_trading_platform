package com.matthew.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Shared Trade model used across all services
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    private String id;
    private String userId;
    private String symbol;
    private String orderType;  // MARKET, LIMIT, STOP_LOSS
    private String side;        // BUY, SELL
    private BigDecimal quantity;
    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private BigDecimal pnl;
    private BigDecimal pnlPercent;
    private String status;      // OPEN, CLOSED, CANCELLED
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    private String broker;      // BitUnix, Binance, etc.
    private String notes;
}
