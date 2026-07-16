package com.mst.matt.tradingservice.dto;

import com.mst.matt.tradingservice.model.Trade;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Inbound DTO for creating or updating a trade.
 *
 * <p>Decouples the REST surface from the JPA entity so the wire format can
 * evolve independently. Validation annotations prevent malformed requests
 * from reaching the service layer.
 *
 * <p>For live-broker trades (source = BROKER_LIVE) the {@code brokerType}
 * field must be set; the orchestration service will route the order to the
 * corresponding {@code TradingProvider} implementation.
 */
@Data
public class TradeRequest {

    /** The user placing this trade; populated from the JWT principal in the controller. */
    private Long userId;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotBlank(message = "Asset name is required")
    private String assetName;

    @NotNull(message = "Asset type is required")
    private Trade.AssetType assetType;

    @NotNull(message = "Trade direction is required")
    private Trade.TradeDirection direction;

    /** Controls whether this is a journal-only entry or a live broker order. */
    private Trade.TradeSource source = Trade.TradeSource.MANUAL;

    /**
     * For live-broker trades: which broker to route the order to.
     * Must be {@code "BITUNIX"} (or another registered broker key) when
     * {@code source == BROKER_LIVE}; ignored for MANUAL trades.
     */
    private String brokerType;

    @NotNull(message = "Entry price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Entry price must be positive")
    private BigDecimal entryPrice;

    /** Exit price — supply to create an already-closed trade. */
    private BigDecimal exitPrice;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be positive")
    private BigDecimal quantity;

    private BigDecimal stopLoss;
    private BigDecimal takeProfit;
    private BigDecimal fee;

    @NotNull(message = "Entry time is required")
    private LocalDateTime entryTime;

    private LocalDateTime exitTime;

    @Size(max = 1000)
    private String notes;

    private String exchange;
    private String strategy;
    private String screenshotPath;
}
