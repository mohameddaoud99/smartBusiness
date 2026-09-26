package com.sales.smartBusiness.supplier;

import com.sales.smartBusiness.common.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SupplierRequest {

    @NotNull(message = "Supplier type is required")
    private SupplierType type;

    /** Optional — a code is generated ("F-0001") when this is left blank on creation. */
    @Size(max = 20, message = "Reference must not exceed 20 characters")
    private String reference;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @Size(max = 120, message = "Contact name must not exceed 120 characters")
    private String contactName;

    @Email(message = "Email must be a valid address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    /** Tax registration number for a company supplier ("matricule fiscal"). */
    @Size(max = 30, message = "Tax id must not exceed 30 characters")
    private String taxId;

    /** National id card number for an individual supplier ("CIN"). */
    @Size(max = 30, message = "National id must not exceed 30 characters")
    private String nationalId;

    @Past(message = "Date of birth must be in the past")
    private LocalDate birthDate;

    @Valid
    private AddressDto billingAddress;

    @Valid
    private AddressDto shippingAddress;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
}
