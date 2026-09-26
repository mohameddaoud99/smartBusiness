package com.sales.smartBusiness.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The platform-admin counterpart of {@link CurrentUser} — never used for company endpoints. */
@Component
public class CurrentPlatformAdmin {

    public PlatformPrincipal get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new IllegalStateException("No authenticated platform admin in the security context");
        }
        return principal;
    }

    public Long id() {
        return get().id();
    }

    public String email() {
        return get().email();
    }
}
