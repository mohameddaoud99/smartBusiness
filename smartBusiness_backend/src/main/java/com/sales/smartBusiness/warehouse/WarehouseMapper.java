package com.sales.smartBusiness.warehouse;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = BaseMapperConfig.class)
interface WarehouseMapper {

    WarehouseResponse toResponse(Warehouse warehouse);

    /** Company and the default flag are the service's: a request can never make a warehouse the default. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "defaultWarehouse", ignore = true)
    Warehouse toEntity(WarehouseRequest request);

    @InheritConfiguration(name = "toEntity")
    void updateEntity(WarehouseRequest request, @MappingTarget Warehouse warehouse);
}
