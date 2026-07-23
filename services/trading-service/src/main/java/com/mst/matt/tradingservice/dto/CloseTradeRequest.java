package com.mst.matt.tradingservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/trades/{id}/close}.
 */
@Data
public class CloseTradeRequest {

    @NotNull(message = "Exit price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Exit price must be positive")
    private BigDecimal exitPrice;
}
