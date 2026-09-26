package com.sales.smartBusiness.platform;

import com.sales.smartBusiness.company.BusinessModule;
import com.sales.smartBusiness.company.CompanyStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
public class PlatformCompanyResponse {

    private Long id;
    private String name;
    private String email;
    private CompanyStatus status;
    private Set<BusinessModule> enabledModules;
    private LocalDateTime createdAt;
}
