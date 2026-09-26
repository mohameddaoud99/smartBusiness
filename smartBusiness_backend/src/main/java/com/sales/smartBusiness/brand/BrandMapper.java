package com.sales.smartBusiness.brand;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Public: {@code ProductMapper} reuses {@link #toSummary} to show a product's brand. */
@Mapper(config = BaseMapperConfig.class)
public interface BrandMapper {

    @Mapping(target = "productCount", ignore = true)
    BrandResponse toResponse(Brand brand);

    BrandSummaryResponse toSummary(Brand brand);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    Brand toEntity(BrandRequest request);

    @InheritConfiguration(name = "toEntity")
    void updateEntity(BrandRequest request, @MappingTarget Brand brand);
}
