package com.mst.matt.identityservice.dto;

import lombok.Data;

/**
 * Request body for {@code PUT /api/admin/users/{id}/active}.
 */
@Data
public class SetActiveRequest {
    private boolean active;
}
