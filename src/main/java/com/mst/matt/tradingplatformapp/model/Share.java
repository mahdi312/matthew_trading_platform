package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shares", indexes = {
        @Index(name = "idx_share_symbol_market", columnList = "symbol,market_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 256)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Trade.AssetType assetType;

    @Column(length = 32)
    private String baseCurrency;

    @Column(length = 32)
    private String quoteCurrency;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
