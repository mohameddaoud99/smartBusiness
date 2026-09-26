package com.sales.smartBusiness.tax;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = BaseMapperConfig.class)
public interface TaxMapper {

    @Mapping(target = "appliesToLine", expression = "java(tax.appliesToLine())")
    TaxResponse toResponse(Tax tax);

    TaxSummaryResponse toSummary(Tax tax);

    /** Company and the system flag are assigned by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "system", ignore = true)
    Tax toEntity(TaxRequest request);

    /** A tax's kind is immutable; the service normalises rate/amount after this runs. */
    @InheritConfiguration(name = "toEntity")
    @Mapping(target = "kind", ignore = true)
    void updateEntity(TaxRequest request, @MappingTarget Tax tax);
}
