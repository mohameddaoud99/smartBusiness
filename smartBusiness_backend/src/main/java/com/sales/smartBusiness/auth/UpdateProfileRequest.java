package com.sales.smartBusiness.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * What the account holder may change about themselves from the profile screen.
 * The email is the login identifier and is not editable here; roles, status and
 * branch are an administrator's decision, not the user's own.
 */
@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 60, message = "First name must not exceed 60 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 60, message = "Last name must not exceed 60 characters")
    private String lastName;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;
}
