package com.sales.smartBusiness.common;

import org.mapstruct.MapperConfig;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingInheritanceStrategy;

/**
 * Shared by every mapper: {@code @Mapper(config = BaseMapperConfig.class)}.
 * <p>
 * Any method that writes into a {@link BaseEntity} — {@code toEntity} as well as
 * {@code updateEntity} — inherits the rules below, so the audit timestamps are never
 * copied from a request and no mapper has to repeat it.
 * ({@code version} needs no rule: it has no setter.)
 */
@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING,
        mappingInheritanceStrategy = MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG)
public interface BaseMapperConfig {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BaseEntity toBaseEntity(Object source);
}
