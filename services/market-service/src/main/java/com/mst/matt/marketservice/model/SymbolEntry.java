package com.mst.matt.marketservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Universal symbol catalogue — one entry per tradable symbol.
 * Ported from the desktop monolith's {@code SymbolEntry}.
 */
@Entity
@Table(name = "symbol_entries",
        indexes = {
                @Index(name = "idx_symbol_entries_type",   columnList = "assetType"),
                @Index(name = "idx_symbol_entries_search", columnList = "symbol, name")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_symbol_entries_type_symbol", columnNames = {"assetType","symbol"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SymbolEntry {

    public enum AssetType { CRYPTO, STOCK, FOREX }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(nullable = false, length = 40)  private String symbol;
    @Column(length = 200)                   private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AssetType assetType;

    @Column(length = 60)  private String exchange;
    @Column(length = 40)  private String source;
}
