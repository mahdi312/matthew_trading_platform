package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Note: @Builder.Default on createdAt ensures it is never null even when
// constructed via the Lombok builder (before @PrePersist fires).

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

    /** Added @Builder.Default so Lombok builder never leaves this null before @PrePersist. */
    @Column(nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Layout name for grouping drawings; defaults to "default" to satisfy any NOT NULL column. */
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50) DEFAULT 'default'")
    @Builder.Default
    private String layoutName = "default";


    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (layoutName == null || layoutName.isBlank()) layoutName = "default";
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
