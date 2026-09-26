package com.sales.smartBusiness.security;

import com.sales.smartBusiness.role.Permission;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The single source of the caller's identity for the services.
 * <p>
 * {@code companyId()} is the whole tenant isolation rule: it comes from the verified
 * token, never from a request parameter or body.
 */
@Component
public class CurrentUser {

    public AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("No authenticated user in the security context");
        }
        return user;
    }

    public Long id() {
        return get().id();
    }

    public Long companyId() {
        return get().companyId();
    }

    public String email() {
        return get().email();
    }

    public Set<Permission> permissions() {
        return get().permissions();
    }

    public boolean has(Permission permission) {
        return get().permissions().contains(permission);
    }
}
