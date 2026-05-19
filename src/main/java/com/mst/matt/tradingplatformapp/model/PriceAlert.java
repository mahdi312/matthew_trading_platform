package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A configurable price alert.
 * Supports threshold alerts, % change alerts, and indicator signal alerts.
 */
@Entity
@Table(name = "price_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private UserProfile profile;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType alertType;

    @Column(precision = 20, scale = 8)
    private BigDecimal targetPrice;          // For PRICE_ABOVE / PRICE_BELOW

    @Column(precision = 10, scale = 4)
    private BigDecimal percentageThreshold;  // For PCT_CHANGE alerts

    @Enumerated(EnumType.STRING)
    @Column
    private AlertCondition condition;        // ABOVE, BELOW, CROSS_UP, CROSS_DOWN

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean notifyEmail;

    @Column(nullable = false)
    private boolean notifyTelegram;

    @Column(nullable = false)
    private boolean notifyDesktop;

    @Column
    private String customMessage;

    @Column
    private LocalDateTime triggeredAt;

    @Column
    private boolean triggered;              // One-time trigger flag

    @Column
    private boolean repeating;             // Re-arm after trigger

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public enum AlertType {
        PRICE_ABOVE,
        PRICE_BELOW,
        PCT_CHANGE_24H,
        INDICATOR_BUY_SIGNAL,
        INDICATOR_SELL_SIGNAL,
        FIBONACCI_LEVEL_TOUCH,
        VOLUME_SPIKE
    }

    public enum AlertCondition { ABOVE, BELOW, CROSS_UP, CROSS_DOWN }
}
