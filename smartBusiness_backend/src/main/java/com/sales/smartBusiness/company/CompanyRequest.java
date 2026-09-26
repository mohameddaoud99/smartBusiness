package com.sales.smartBusiness.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Status is absent on purpose: suspending a company is a platform decision,
 * not something a tenant can do to itself. Logo and stamp are uploaded through their
 * own endpoints, not through this DTO — image bytes do not belong in a JSON body.
 */
@Getter
@Setter
public class CompanyRequest {

    @NotBlank(message = "Company name is required")
    @Size(max = 120, message = "Company name must not exceed 120 characters")
    private String name;

    @Email(message = "Email must be a valid address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    /** Business tax identifier ("matricule fiscale" or equivalent) — format varies by country. */
    @Size(max = 30, message = "Tax ID must not exceed 30 characters")
    private String taxId;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code, e.g. TND")
    private String currency;
}
