package com.mst.matt.alertservice.dto;

import com.mst.matt.contracts.enums.AlertCondition;
import com.mst.matt.contracts.enums.AlertStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request payload for {@code PUT /alerts/{id}}.
 * All fields optional/nullable — only non-null fields are applied, so a
 * client can send just {@code status: "ACTIVE"} to re-arm a one-shot alert
 * without resending the rest of the definition.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAlertRequestDto {

    private AlertCondition condition;
    private BigDecimal targetValue;
    private AlertStatus status;
    private String message;
    private Boolean repeating;
    private Integer cooldownSeconds;
}
