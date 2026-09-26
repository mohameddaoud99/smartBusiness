package com.sales.smartBusiness.auth;

import com.sales.smartBusiness.company.BusinessModule;
import com.sales.smartBusiness.role.Permission;
import com.sales.smartBusiness.role.RoleSummaryResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

/**
 * Everything the frontend needs to draw the interface for this user:
 * who they are, which company they are in, what they may do, and which modules their
 * company is even entitled to use.
 */
@Getter
@Setter
public class SessionResponse {

    private Long id;
    private String fullName;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String phone;
    private Long companyId;
    private String companyName;
    private List<RoleSummaryResponse> roles;
    private Set<Permission> permissions;
    /** Set by a platform admin — the company's own admin cannot change this. */
    private Set<BusinessModule> enabledModules;
}
