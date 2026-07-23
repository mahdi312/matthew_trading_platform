package com.mst.matt.identityservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for {@code POST /auth/register}.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 80, message = "Username must be 3-80 characters")
    private String username;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Display name must not be blank")
    @Size(max = 100, message = "Display name must be at most 100 characters")
    private String displayName;

    @Email(message = "Email must be a valid address")
    private String email;
}
