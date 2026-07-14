package com.mst.matt.marketservice.charting.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Named drawing layout — a user-defined snapshot of drawings for a
 * symbol + timeframe combination.
 *
 * <p>Adapted from desktop {@code model.DrawingLayout}:
 * {@code UserProfile profile} → {@code Long userId}.
 */
@Entity
@Table(name = "drawing_layouts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_layout_user_symbol_tf_name",
                columnNames = {"user_id", "symbol", "timeframe", "name"}),
        indexes = @Index(name = "idx_layout_user_sym_tf",
                columnList = "user_id,symbol,timeframe"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawingLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String timeframe;

    /** User-given name, e.g. "BTC Breakout Plan". */
    @Column(nullable = false, length = 100)
    private String name;

    /** Last-saved timestamp (epoch ms). */
    @Column(nullable = false)
    @Builder.Default
    private long savedAtEpoch = 0L;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        savedAtEpoch = System.currentTimeMillis();
    }
}
