package com.sales.smartBusiness.company;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CompanyResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String postalCode;
    private String city;
    private String taxId;
    private String currency;
    private CompanyStatus status;

    /** "data:{contentType};base64,...", ready for an <img [src]>. Null when none is set. */
    private String logoDataUri;
    private String stampDataUri;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
