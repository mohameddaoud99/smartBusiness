package com.sales.smartBusiness.supplier;

import com.sales.smartBusiness.common.AddressDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class SupplierResponse {

    private Long id;
    private SupplierType type;
    private String reference;
    private String name;
    private String contactName;
    private String email;
    private String phone;
    private String taxId;
    private String nationalId;
    private LocalDate birthDate;
    private AddressDto billingAddress;
    private AddressDto shippingAddress;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
