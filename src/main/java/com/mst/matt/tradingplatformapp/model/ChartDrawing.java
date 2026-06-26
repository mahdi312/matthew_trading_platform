package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Persistent chart drawing entity.
 *
 * <p>Timestamps are stored as {@code long} (epoch milliseconds) to avoid
 * {@code InaccessibleObjectException} when Gson reflects into {@code LocalDateTime}
 * fields on newer JVMs with strong module encapsulation.
 *
 * <p><b>Bug fix — LazyInitializationException:</b>
 * The {@code profile} field is {@code @ManyToOne(fetch = FetchType.LAZY)} which means
 * Hibernate replaces it with a proxy object.  Lombok's {@code @Data}-generated
 * {@code equals()} / {@code hashCode()} include <em>all</em> fields, causing the proxy
 * to be initialised (via {@code UserProfile.equals}) when the drawing list is checked
 * with {@code contains()} or {@code indexOf()} in {@link com.mst.matt.tradingplatformapp.ui.chart.drawing.ChartDrawingEngine#setDrawings}
 * — <em>after</em> the Hibernate session is already closed, producing
 * {@code LazyInitializationException}.
 *
 * <p>Solution: replace {@code @Data} with {@code @Getter @Setter @ToString @Builder} and
 * provide an explicit {@code equals()} / {@code hashCode()} based solely on the surrogate
 * primary key ({@code id}).  This is both safe (no lazy-load needed) and semantically
 * correct (two JPA entities are equal iff they represent the same DB row).
 */
@Entity
@Table(name = "chart_drawings",
        indexes = @Index(name = "idx_drawing_profile_sym_tf",
                columnList = "profile_id,symbol,timeframe"))
@Getter
@Setter
@ToString(exclude = {"profile", "points", "properties"})  // exclude lazy & large fields
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

    /** JSON array of {time, price} anchor points. time is stored as epoch-millis (long). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String pointsJson;

    /** JSON object for color, line width, position prices, text, etc. */
    @Column(columnDefinition = "TEXT")
    private String propertiesJson;

    @Column(nullable = false)
    private boolean locked;

    /**
     * Creation timestamp stored as epoch milliseconds.
     * Replaces the former {@code LocalDateTime createdAt} field to avoid
     * Gson / Java-module reflection errors with {@code java.time.LocalDateTime}.
     */
    @Column(nullable = false)
    @Builder.Default
    private long createdAtEpoch = 0L;

    /** Named layout this drawing belongs to – null means it is part of the active (default) set. */
    @Column(length = 100)
    private String layoutName;

    @PrePersist
    protected void onCreate() {
        if (createdAtEpoch == 0L) createdAtEpoch = System.currentTimeMillis();
    }

    /** Transient parsed points — not persisted. */
    @Transient
    @Builder.Default
    private List<ChartPoint> points = new ArrayList<>();

    /** Transient parsed properties — not persisted. */
    @Transient
    @Builder.Default
    private ChartDrawingProperties properties = ChartDrawingProperties.builder().build();

    // ── equals / hashCode ─────────────────────────────────────────────────────
    //
    // Bug fix: use only the surrogate PK so equality never touches the lazy
    // `profile` proxy.  Transient drawings (id == null) fall back to identity.
    //
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChartDrawing other)) return false;
        if (id == null || other.id == null) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        // Stable hash: use the class itself when id is not yet assigned
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
