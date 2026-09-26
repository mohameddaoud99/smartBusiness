package com.sales.smartBusiness.product;

import com.sales.smartBusiness.brand.BrandMapper;
import com.sales.smartBusiness.category.CategoryMapper;
import com.sales.smartBusiness.common.BaseMapperConfig;
import com.sales.smartBusiness.tax.TaxMapper;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = BaseMapperConfig.class, uses = {CategoryMapper.class, BrandMapper.class, TaxMapper.class})
interface ProductMapper {

    /** Photos need a disk read, which does not belong in a declarative mapper. */
    @Mapping(target = "imageDataUri", ignore = true)
    @Mapping(target = "images", ignore = true)
    ProductResponse toResponse(Product product);

    /** Company, reference, category, brand and taxes are resolved by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "reference", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "brand", ignore = true)
    @Mapping(target = "defaultTaxes", ignore = true)
    @Mapping(target = "images", ignore = true)
    Product toEntity(ProductRequest request);

    @InheritConfiguration(name = "toEntity")
    void updateEntity(ProductRequest request, @MappingTarget Product product);
}
