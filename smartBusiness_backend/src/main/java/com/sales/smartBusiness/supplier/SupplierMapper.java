package com.sales.smartBusiness.supplier;

import com.sales.smartBusiness.common.Address;
import com.sales.smartBusiness.common.AddressDto;
import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = BaseMapperConfig.class)
interface SupplierMapper {

    SupplierResponse toResponse(Supplier supplier);

    AddressDto toAddressDto(Address address);

    Address toAddress(AddressDto dto);

    /** Company and reference are assigned by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "reference", ignore = true)
    Supplier toEntity(SupplierRequest request);

    /** A PUT replaces the record; the reference is handled separately by the service. */
    @InheritConfiguration(name = "toEntity")
    void updateEntity(SupplierRequest request, @MappingTarget Supplier supplier);
}
