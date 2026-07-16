package com.mst.matt.identityservice.dto;

import lombok.Data;

import java.util.Map;

/**
 * Request body for bulk-setting app settings.
 * Each key in {@link #settings} is a well-known setting key (e.g. {@code ui.theme}).
 */
@Data
public class AppSettingsRequest {
    private Map<String, String> settings;
}
