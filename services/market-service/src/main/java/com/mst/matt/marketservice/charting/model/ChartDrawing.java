package com.mst.matt.marketservice.charting.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Persistent chart drawing entity.
 *
 * <p>Adapted from desktop {@code model.ChartDrawing}:
 * <ul>
 *   <li>{@code UserProfile profile} replaced by {@code Long userId} (scalar from JWT
 *       {@code X-User-Id} header — no local user table join).</li>
 *   <li>Custom {@code equals}/{@code hashCode} on {@code id} only (same as desktop)
 *       to avoid lazy-proxy issues.</li>
 * </ul>
 */
@Entity
@Table(name = "chart_drawings",
        indexes = @Index(name = "idx_cd_user_sym_tf",
                columnList = "user_id,symbol,timeframe"))
@Getter
@Setter
@ToString(exclude = {"points", "properties"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartDrawing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user — forwarded from Gateway via {@code X-User-Id} header. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String timeframe;

    /**
     * Stored as plain VARCHAR(60) — no DB CHECK constraint.
     * This prevents ConstraintViolationException when new enum values are added later.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(60)")
    private ChartDrawingToolType toolType;

    /** JSON array of {@link ChartPoint} anchor points. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String pointsJson;

    /** JSON object for color, line width, position prices, etc. */
    @Column(columnDefinition = "TEXT")
    private String propertiesJson;

    @Column(nullable = false)
    private boolean locked;

    /** Creation timestamp (epoch ms). */
    @Column(nullable = false)
    @Builder.Default
    private long createdAtEpoch = 0L;

    /** Named layout this drawing belongs to — {@code null} = active (default) set. */
    @Column(length = 100)
    private String layoutName;

    @PrePersist
    protected void onCreate() {
        if (createdAtEpoch == 0L) createdAtEpoch = System.currentTimeMillis();
    }

    // ── Transient parsed fields (not persisted) ────────────────────────────────

    @Transient @Builder.Default
    private List<ChartPoint> points = new ArrayList<>();

    @Transient @Builder.Default
    private ChartDrawingProperties properties = ChartDrawingProperties.builder().build();

    // ── equals / hashCode on PK only (avoids lazy proxy issues) ──────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChartDrawing other)) return false;
        if (id == null || other.id == null) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
