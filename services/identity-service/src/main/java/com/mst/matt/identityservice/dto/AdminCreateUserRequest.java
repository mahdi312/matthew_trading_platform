package com.mst.matt.identityservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for {@code POST /api/admin/users} (admin creates a new user).
 */
@Data
public class AdminCreateUserRequest {

    @NotBlank
    @Size(max = 80)
    private String username;

    @Size(max = 100)
    private String displayName;

    @NotBlank
    @Size(min = 6, max = 255)
    private String password;

    @Size(max = 255)
    private String email;

    private com.mst.matt.identityservice.model.AppUser.Role role;
}
