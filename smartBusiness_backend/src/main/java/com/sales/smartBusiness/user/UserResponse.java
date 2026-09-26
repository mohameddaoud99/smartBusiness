package com.sales.smartBusiness.user;

import com.sales.smartBusiness.branch.BranchSummaryResponse;
import com.sales.smartBusiness.role.RoleSummaryResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String username;
    private String email;
    private String phone;
    private UserStatus status;
    /** Null when the user is not tied to a single site. */
    private BranchSummaryResponse branch;
    private List<RoleSummaryResponse> roles;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
