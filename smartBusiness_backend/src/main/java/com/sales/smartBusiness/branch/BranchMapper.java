package com.sales.smartBusiness.branch;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.*;

@Mapper(config = BaseMapperConfig.class)
public interface BranchMapper {

    BranchResponse toResponse(Branch branch);

    BranchSummaryResponse toSummary(Branch branch);

    /** Company and status are assigned by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    @Mapping(target = "status", ignore = true)
    Branch toEntity(BranchRequest request);

    @InheritConfiguration(name = "toEntity")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(BranchRequest request, @MappingTarget Branch branch);
}
