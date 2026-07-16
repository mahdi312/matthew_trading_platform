package com.mst.matt.marketservice.watchlist.repository;

import com.mst.matt.marketservice.watchlist.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link WatchlistItem}.
 */
@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {

    /** Return all watchlist items for a user ordered by addedAt descending. */
    List<WatchlistItem> findByUserIdOrderByAddedAtDesc(Long userId);

    /** Check if a symbol is already on the user's watchlist. */
    boolean existsByUserIdAndSymbol(Long userId, String symbol);

    /** Find a specific entry by user + symbol (for idempotent add). */
    Optional<WatchlistItem> findByUserIdAndSymbol(Long userId, String symbol);

    /** Delete a specific symbol from a user's watchlist. */
    void deleteByUserIdAndSymbol(Long userId, String symbol);

    /** Count entries for a user (used for seeding check). */
    long countByUserId(Long userId);
}
