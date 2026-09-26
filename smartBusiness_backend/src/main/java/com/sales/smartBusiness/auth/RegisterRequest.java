package com.sales.smartBusiness.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Opens an account: creates the company, its standard roles, its first branch and
 * the administrator who signs up. Deliberately short — the rest is set up afterwards
 * from the application.
 */
@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Company name is required")
    @Size(max = 120, message = "Company name must not exceed 120 characters")
    private String companyName;

    @NotBlank(message = "First name is required")
    @Size(max = 60, message = "First name must not exceed 60 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 60, message = "Last name must not exceed 60 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;
}
