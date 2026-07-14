package com.mst.matt.identityservice.dto;

import com.mst.matt.identityservice.model.AppUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * REST response DTO for an {@link AppUser} — omits password hash and internal fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUserResponse {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private AppUser.Role role;
    private AppUser.AuthProvider authProvider;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private String hiddenTabs;
    private String favoriteTimeframes;

    /** Factory — convert entity to DTO (never exposes passwordHash). */
    public static AppUserResponse from(AppUser u) {
        return AppUserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .displayName(u.getDisplayName())
                .email(u.getEmail())
                .role(u.getRole())
                .authProvider(u.getAuthProvider())
                .active(u.isActive())
                .createdAt(u.getCreatedAt())
                .lastLoginAt(u.getLastLoginAt())
                .hiddenTabs(u.getHiddenTabs())
                .favoriteTimeframes(u.getFavoriteTimeframes())
                .build();
    }
}
