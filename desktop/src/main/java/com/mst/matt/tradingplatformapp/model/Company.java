package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "companies", indexes = {
        @Index(name = "idx_company_ticker", columnList = "ticker")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(length = 20)
    private String ticker;

    @Column(length = 128)
    private String sector;

    @Column(length = 128)
    private String industry;

    @Column(length = 64)
    private String country;

    @Column(length = 256)
    private String website;

    @Column(length = 2000)
    private String description;

    @Column(precision = 24, scale = 2)
    private BigDecimal marketCap;

    @Column(length = 64)
    private String dataProvider;

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
