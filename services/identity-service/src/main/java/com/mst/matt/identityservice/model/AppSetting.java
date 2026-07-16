package com.mst.matt.identityservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-user application settings stored as a key-value pair in the database.
 *
 * <p>The desktop monolith stored settings in {@code ~/.trading-platform/app-settings.properties}.
 * In the microservice environment settings must be per-user and server-side, so each
 * setting is a row keyed by {@code (appUserId, settingKey)}.</p>
 */
@Entity
@Table(name = "app_settings",
        uniqueConstraints = @UniqueConstraint(
                name  = "uk_app_settings_user_key",
                columnNames = {"app_user_id", "setting_key"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner — references {@link AppUser#getId()} without a hard FK. */
    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    @Column(name = "setting_key", nullable = false, length = 128)
    private String key;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;
}
