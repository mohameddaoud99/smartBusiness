package com.sales.smartBusiness.security;

import com.sales.smartBusiness.role.Permission;
import com.sales.smartBusiness.user.User;

import java.util.Set;

/**
 * What the application knows about the caller of the current request.
 * Rebuilt from the database on every request, so a disabled account or a changed
 * role takes effect immediately instead of at the next token renewal.
 */
public record AuthenticatedUser(Long id,
                                Long companyId,
                                String email,
                                Set<Permission> permissions) {

    public static AuthenticatedUser of(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getCompany().getId(),
                user.getEmail(),
                user.collectPermissions());
    }
}
