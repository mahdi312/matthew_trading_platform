package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Flexible permission overrides — one row per (role OR user) × tab.
 * Allows admins to grant/revoke a specific tab for a role or individual user.
 *
 * Lookup priority:  per-user override  >  role default  >  built-in Role.allowedTimeframes()
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

    /** Role this permission applies to (null = per-user override). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AppUser.Role subjectRole;

    /** User id this permission applies to (null = role-wide). */
    @Column
    private Long subjectUserId;

    /** Tab name: CHART, ANALYZE, PORTFOLIO, TRADE_JOURNAL, INDICATOR_MIXER, SETTINGS, ALERTS. */
    @Column(nullable = false, length = 40)
    private String tabName;

    /** true = tab is VISIBLE for subject, false = hidden. */
    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;
}
