package com.sales.smartBusiness.role;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = BaseMapperConfig.class)
public interface RoleMapper {

    /** userCount comes from a separate query, the service fills it in. */
    @Mapping(target = "userCount", ignore = true)
    RoleResponse toResponse(Role role);
}
