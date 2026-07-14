package com.mst.matt.identityservice.dto;

import com.mst.matt.identityservice.model.UserProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for creating or updating a {@link UserProfile}.
 */
@Data
public class UserProfileRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 20)
    private String avatarColor;

    @Size(max = 500)
    private String description;

    private boolean active;

    private UserProfile.ProfileAssetFocus assetFocus;

    @Size(max = 32)
    private String defaultSymbol;

    @Size(max = 32)
    private String chartProvider;

    @Size(max = 32)
    private String fundamentalProvider;

    @Size(max = 1024)
    private String watchlist;

    private String drawingSettingsJson;
}
