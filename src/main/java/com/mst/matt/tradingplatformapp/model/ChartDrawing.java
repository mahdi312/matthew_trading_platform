package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chart_drawings",
        indexes = @Index(name = "idx_drawing_profile_sym_tf",
                columnList = "profile_id,symbol,timeframe"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartDrawing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private UserProfile profile;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String timeframe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ChartDrawingToolType toolType;

    /** JSON array of {time, price} anchor points. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String pointsJson;

    /** JSON object for color, line width, position prices, text, etc. */
    @Column(columnDefinition = "TEXT")
    private String propertiesJson;

    @Column(nullable = false)
    private boolean locked;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** Transient parsed points — not persisted. */
    @Transient
    @Builder.Default
    private List<ChartPoint> points = new ArrayList<>();

    /** Transient parsed properties — not persisted. */
    @Transient
    @Builder.Default
    private ChartDrawingProperties properties = ChartDrawingProperties.builder().build();
}
