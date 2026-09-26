package com.sales.smartBusiness.common;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Billing / shipping address, in and out. Every part is optional. */
@Getter
@Setter
public class AddressDto {

    @Size(max = 255, message = "Street must not exceed 255 characters")
    private String street;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 100, message = "Region must not exceed 100 characters")
    private String region;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;

    @Size(max = 60, message = "Country must not exceed 60 characters")
    private String country;
}
