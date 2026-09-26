package com.sales.smartBusiness.audit;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface AuditLogMapper {

    @Mapping(target = "actionLabel", source = "action.label")
    AuditLogResponse toResponse(AuditLog log);

    List<AuditLogResponse> toResponses(List<AuditLog> logs);
}
