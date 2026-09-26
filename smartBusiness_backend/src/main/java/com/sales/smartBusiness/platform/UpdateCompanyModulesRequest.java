package com.sales.smartBusiness.platform;

import com.sales.smartBusiness.company.BusinessModule;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * May be empty — a company with every module withheld can still sign in and manage
 * its own users, roles and branches, which are never part of this set.
 */
@Getter
@Setter
public class UpdateCompanyModulesRequest {

    @NotNull(message = "The list of modules is required")
    private Set<BusinessModule> modules = new HashSet<>();
}
