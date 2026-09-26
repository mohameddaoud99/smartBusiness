package com.sales.smartBusiness.security;

/**
 * The security principal for a platform admin — no {@code companyId}, because a
 * platform admin does not belong to a company. Its single authority is
 * {@code PLATFORM_ADMIN}, which is why {@code @PreAuthorize("hasAuthority('USER_VIEW')")}
 * and similar company-scoped checks always refuse it: that authority never appears here.
 */
public record PlatformPrincipal(Long id, String email) {
}
