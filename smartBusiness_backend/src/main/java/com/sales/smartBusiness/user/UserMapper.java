package com.sales.smartBusiness.user;

import com.sales.smartBusiness.branch.BranchMapper;
import com.sales.smartBusiness.common.BaseMapperConfig;
import com.sales.smartBusiness.role.Role;
import com.sales.smartBusiness.role.RoleSummaryResponse;
import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

@Mapper(config = BaseMapperConfig.class, uses = BranchMapper.class)
public interface UserMapper {

    UserResponse toResponse(User user);

    RoleSummaryResponse toRoleSummary(Role role);

    List<RoleSummaryResponse> toRoleSummaries(Collection<Role> roles);

    /** Company, branch, roles and password are assigned explicitly by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    User toEntity(UserRequest request);

    @InheritConfiguration(name = "toEntity")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UserRequest request, @MappingTarget User user);
}
