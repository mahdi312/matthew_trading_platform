package com.mst.matt.identityservice.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Flexible permission overrides — one row per (role OR user) × tab.
 *
 * <p>Copied as-is from the JavaFX monolith and re-homed in
 * {@code identity-service}. Allows admins to grant or revoke a specific
 * feature tab for an entire role or for a single user.</p>
 *
 * <p>Lookup priority: per-user override &gt; role default &gt; built-in
 * {@link AppUser.Role#allowedTimeframes()}.</p>
 */
@Entity
@Table(name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_role_perm_subject_tab",
                columnNames = {"subjectRole", "subjectUserId", "tabName"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Role this permission applies to; {@code null} = per-user override.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AppUser.Role subjectRole;

    /**
     * User id this permission applies to; {@code null} = role-wide rule.
     */
    @Column
    private Long subjectUserId;

    /**
     * Tab/feature name: CHART, ANALYZE, PORTFOLIO, TRADE_JOURNAL,
     * INDICATOR_MIXER, SETTINGS, ALERTS.
     */
    @Column(nullable = false, length = 40)
    private String tabName;

    /**
     * {@code true} = tab is VISIBLE for the subject; {@code false} = hidden.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;
}
