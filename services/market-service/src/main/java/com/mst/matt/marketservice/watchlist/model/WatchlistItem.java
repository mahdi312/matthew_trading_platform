package com.mst.matt.marketservice.watchlist.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single watchlist entry for a user.
 *
 * <p>Follows the same {@code userId-from-header} pattern as
 * {@code charting.model.DrawingLayout}: the user's id is injected
 * by the gateway into the {@code X-User-Id} request header, and the
 * controller reads it from there — this service never validates JWTs.</p>
 */
@Entity
@Table(
    name = "watchlist_items",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_watchlist_user_symbol",
        columnNames = { "user_id", "symbol" }
    ),
    indexes = @Index(name = "idx_watchlist_user_id", columnList = "user_id")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user — resolved from {@code X-User-Id} header. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Normalised upper-case trading symbol, e.g. BTCUSDT, AAPL. */
    @Column(nullable = false, length = 40)
    private String symbol;

    /** Broad asset class string, e.g. CRYPTO | STOCK | FOREX. */
    @Column(length = 20)
    private String assetClass;

    /** Timestamp when the symbol was added. */
    @Column(nullable = false, updatable = false)
    private Instant addedAt;

    @PrePersist
    void prePersist() {
        if (addedAt == null) addedAt = Instant.now();
    }
}
