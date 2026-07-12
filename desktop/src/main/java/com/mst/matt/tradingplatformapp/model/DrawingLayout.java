package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Named drawing layout: a user-defined group of drawings for a specific
 * symbol + timeframe combination.
 *
 * <p>When a layout is "saved", all current drawings for the active
 * symbol/timeframe are tagged with this {@link #name}.  When loaded, only
 * drawings matching the layout name are displayed.
 */
@Entity
@Table(name = "drawing_layouts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_layout_profile_symbol_tf_name",
                columnNames = {"profile_id", "symbol", "timeframe", "name"}),
        indexes = @Index(name = "idx_layout_profile_sym_tf",
                columnList = "profile_id,symbol,timeframe"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawingLayout {

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

    /** User-given name, e.g. "BTC Breakout Plan". */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Creation/last-saved timestamp (epoch millis).
     * Avoids LocalDateTime reflection issues.
     */
    @Column(nullable = false)
    @Builder.Default
    private long savedAtEpoch = 0L;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        savedAtEpoch = System.currentTimeMillis();
    }
}
