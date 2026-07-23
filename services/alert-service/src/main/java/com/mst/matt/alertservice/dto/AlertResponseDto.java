package com.mst.matt.alertservice.dto;

import com.mst.matt.alertservice.model.PriceAlert;
import com.mst.matt.contracts.enums.AlertCondition;
import com.mst.matt.contracts.enums.AlertStatus;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.enums.BrokerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Response shape for all {@code /alerts} endpoints. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertResponseDto {

    private Long id;
    private Long userId;
    private String symbol;
    private AssetClass assetClass;
    private BrokerType brokerType;
    private String providerName;
    private AlertCondition condition;
    private BigDecimal targetValue;
    private AlertStatus status;
    private String message;
    private boolean repeating;
    private int cooldownSeconds;
    private Instant lastTriggeredAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static AlertResponseDto from(PriceAlert a) {
        return AlertResponseDto.builder()
                .id(a.getId())
                .userId(a.getUserId())
                .symbol(a.getSymbol())
                .assetClass(a.getAssetClass())
                .brokerType(a.getBrokerType())
                .providerName(a.getProviderName())
                .condition(a.getCondition())
                .targetValue(a.getTargetValue())
                .status(a.getStatus())
                .message(a.getMessage())
                .repeating(a.isRepeating())
                .cooldownSeconds(a.getCooldownSeconds())
                .lastTriggeredAt(a.getLastTriggeredAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
