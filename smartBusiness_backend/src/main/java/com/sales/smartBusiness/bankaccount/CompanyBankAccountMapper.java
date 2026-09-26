package com.sales.smartBusiness.bankaccount;

import com.sales.smartBusiness.common.BaseMapperConfig;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = BaseMapperConfig.class)
public interface CompanyBankAccountMapper {

    CompanyBankAccountResponse toResponse(CompanyBankAccount account);

    /** Company is assigned by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "company", ignore = true)
    CompanyBankAccount toEntity(CompanyBankAccountRequest request);

    /** No null-ignore strategy: a PUT is a full replace. */
    @InheritConfiguration(name = "toEntity")
    void updateEntity(CompanyBankAccountRequest request, @MappingTarget CompanyBankAccount account);
}
