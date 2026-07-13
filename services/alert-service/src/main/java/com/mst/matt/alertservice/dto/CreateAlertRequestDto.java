package com.mst.matt.alertservice.dto;

import com.mst.matt.contracts.enums.AlertCondition;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.enums.BrokerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Request payload for {@code POST /alerts}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAlertRequestDto {

    @NotBlank
    private String symbol;

    @NotNull
    private AssetClass assetClass;

    /** Optional — preferred broker for the price feed; {@code null} = use registry default. */
    private BrokerType brokerType;

    /** Optional — explicit provider name; {@code null} = use registry's priority chain. */
    private String providerName;

    @NotNull
    private AlertCondition condition;

    @NotNull
    @Positive
    private BigDecimal targetValue;

    private String message;

    @Builder.Default
    private boolean repeating = false;

    @Builder.Default
    private int cooldownSeconds = 900;
}
