package com.sales.smartBusiness.category;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Public: {@code ProductMapper} reuses {@link #toSummary} to show a product's family. */
@Mapper(config = BaseMapperConfig.class)
public interface CategoryMapper {

    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "parentName", source = "parent.name")
    @Mapping(target = "productCount", ignore = true)
    CategoryResponse toResponse(Category category);

    CategorySummaryResponse toSummary(Category category);

    /** Company and parent are assigned by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "parent", ignore = true)
    Category toEntity(CategoryRequest request);

    @InheritConfiguration(name = "toEntity")
    void updateEntity(CategoryRequest request, @MappingTarget Category category);
}
