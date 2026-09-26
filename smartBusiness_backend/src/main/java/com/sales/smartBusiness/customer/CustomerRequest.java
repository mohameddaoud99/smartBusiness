package com.sales.smartBusiness.customer;

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
public class CustomerRequest {

    @NotNull(message = "Customer type is required")
    private CustomerType type;

    /** Optional — a code is generated ("C-0001") when this is left blank on creation. */
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

    /** Tax registration number for a company customer ("matricule fiscal"). */
    @Size(max = 30, message = "Tax id must not exceed 30 characters")
    private String taxId;

    /** National id card number for an individual customer ("CIN"). */
    @Size(max = 30, message = "National id must not exceed 30 characters")
    private String nationalId;

    @Past(message = "Date of birth must be in the past")
    private LocalDate birthDate;

    @Size(max = 60, message = "VAT-suspension permit number must not exceed 60 characters")
    private String vatSuspensionNumber;

    @Valid
    private AddressDto billingAddress;

    @Valid
    private AddressDto shippingAddress;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
}
