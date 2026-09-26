package com.sales.smartBusiness.customer;

import com.sales.smartBusiness.common.Address;
import com.sales.smartBusiness.common.AddressDto;
import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = BaseMapperConfig.class)
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);

    AddressDto toAddressDto(Address address);

    Address toAddress(AddressDto dto);

    /** Company and reference are assigned by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "reference", ignore = true)
    Customer toEntity(CustomerRequest request);

    /** A PUT replaces the record; the reference is handled separately by the service. */
    @InheritConfiguration(name = "toEntity")
    void updateEntity(CustomerRequest request, @MappingTarget Customer customer);
}
