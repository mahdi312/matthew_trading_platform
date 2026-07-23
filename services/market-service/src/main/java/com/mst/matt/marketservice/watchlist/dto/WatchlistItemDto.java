package com.mst.matt.marketservice.watchlist.dto;

import com.mst.matt.marketservice.watchlist.model.WatchlistItem;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response DTO returned by watchlist endpoints.
 */
@Data
@Builder
public class WatchlistItemDto {

    private Long    id;
    private String  symbol;
    private String  assetClass;
    private Instant addedAt;

    public static WatchlistItemDto from(WatchlistItem item) {
        return WatchlistItemDto.builder()
                .id(item.getId())
                .symbol(item.getSymbol())
                .assetClass(item.getAssetClass())
                .addedAt(item.getAddedAt())
                .build();
    }
}
