package com.mst.matt.tradingplatformapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A single anchor stored in absolute price-time coordinates. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartPoint {
    private LocalDateTime time;
    private double price;
}
