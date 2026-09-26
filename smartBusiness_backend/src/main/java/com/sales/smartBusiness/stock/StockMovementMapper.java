package com.sales.smartBusiness.stock;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = BaseMapperConfig.class)
interface StockMovementMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productReference", source = "product.reference")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    StockMovementResponse toResponse(StockMovement movement);
}
