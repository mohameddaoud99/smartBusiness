package com.sales.smartBusiness.security;

import com.sales.smartBusiness.company.CompanyStatus;
import com.sales.smartBusiness.platform.PlatformAdmin;
import com.sales.smartBusiness.platform.PlatformAdminRepository;
import com.sales.smartBusiness.platform.PlatformAdminStatus;
import com.sales.smartBusiness.user.User;
import com.sales.smartBusiness.user.UserRepository;
import com.sales.smartBusiness.user.UserStatus;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Turns a Bearer token into an authenticated caller — either a company user or a
 * platform admin, depending on what {@link JwtService#parse} finds in the token.
 * <p>
 * The user (or admin), their status and their permissions are re-read from the
 * database on every request. That costs one query per call and buys immediate
 * revocation: deactivating a user, editing a role, or disabling a platform admin
 * applies at once, with no token to blacklist.
 * <p>
 * Instantiated by {@link SecurityConfig} rather than declared as a bean: any {@code Filter}
 * bean is also registered on the servlet container, so it would run a second time per
 * request and would be dragged into every {@code @WebMvcTest} slice.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final String PLATFORM_ADMIN_AUTHORITY = "PLATFORM_ADMIN";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PlatformAdminRepository platformAdminRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER)) {
            try {
                JwtService.TokenClaims claims = jwtService.parse(header.substring(BEARER.length()));
                if (claims.platform()) {
                    authenticatePlatformAdmin(claims.subjectId());
                } else {
                    authenticateCompanyUser(claims.subjectId());
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid, tampered or expired token: the request simply stays anonymous
                // and the entry point answers 401.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateCompanyUser(Long userId) {
        userRepository.findByIdWithRoles(userId)
                .filter(this::isUsable)
                .ifPresent(this::authenticate);
    }

    private boolean isUsable(User user) {
        return user.getStatus() == UserStatus.ACTIVE
                && user.getCompany().getStatus() == CompanyStatus.ACTIVE;
    }

    private void authenticate(User user) {
        AuthenticatedUser principal = AuthenticatedUser.of(user);

        List<SimpleGrantedAuthority> authorities = principal.permissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .toList();

        setContext(principal, authorities);
    }

    private void authenticatePlatformAdmin(Long adminId) {
        platformAdminRepository.findById(adminId)
                .filter(admin -> admin.getStatus() == PlatformAdminStatus.ACTIVE)
                .ifPresent(this::authenticate);
    }

    private void authenticate(PlatformAdmin admin) {
        PlatformPrincipal principal = new PlatformPrincipal(admin.getId(), admin.getEmail());
        setContext(principal, List.of(new SimpleGrantedAuthority(PLATFORM_ADMIN_AUTHORITY)));
    }

    private void setContext(Object principal, List<SimpleGrantedAuthority> authorities) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
