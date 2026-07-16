package com.mst.matt.identityservice.dto;

import lombok.Data;

/**
 * Request body for {@code PUT /api/admin/users/{id}/tab-permissions}.
 * Carries tab name -&gt; visible flag mappings for the given user.
 */
@Data
public class TabPermissionsRequest {
    /** Map of tabName -> visible (true = user can see this tab). */
    private java.util.Map<String, Boolean> tabVisibility;
}
