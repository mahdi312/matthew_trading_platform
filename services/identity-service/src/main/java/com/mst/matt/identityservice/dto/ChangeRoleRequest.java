package com.mst.matt.identityservice.dto;

import com.mst.matt.identityservice.model.AppUser;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for {@code PUT /api/admin/users/{id}/role}.
 */
@Data
public class ChangeRoleRequest {
    @NotNull
    private AppUser.Role role;
}
