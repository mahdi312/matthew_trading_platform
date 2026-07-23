package com.mst.matt.identityservice.dto;

import com.mst.matt.identityservice.model.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * REST DTO for reading a {@link UserProfile} — omits JPA internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long id;
    private String name;
    private String avatarColor;
    private String description;
    private boolean active;
    private Long appUserId;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
    private UserProfile.ProfileAssetFocus assetFocus;
    private String defaultSymbol;
    private String chartProvider;
    private String fundamentalProvider;
    private String watchlist;
    private String drawingSettingsJson;

    /** Factory — convert entity to DTO. */
    public static UserProfileResponse from(UserProfile p) {
        return UserProfileResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .avatarColor(p.getAvatarColor())
                .description(p.getDescription())
                .active(p.isActive())
                .appUserId(p.getAppUserId())
                .createdAt(p.getCreatedAt())
                .lastAccessedAt(p.getLastAccessedAt())
                .assetFocus(p.getAssetFocus())
                .defaultSymbol(p.getDefaultSymbol())
                .chartProvider(p.getChartProvider())
                .fundamentalProvider(p.getFundamentalProvider())
                .watchlist(p.getWatchlist())
                .drawingSettingsJson(p.getDrawingSettingsJson())
                .build();
    }
}
