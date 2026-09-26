package com.sales.smartBusiness.security;

import com.sales.smartBusiness.platform.PlatformAdminRepository;
import com.sales.smartBusiness.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Stateless API secured by a Bearer token.
 * <p>
 * {@code @EnableMethodSecurity} is what makes {@code @PreAuthorize} effective — without
 * it the annotations are silently ignored.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PlatformAdminRepository platformAdminRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No cookies, no browser form: CSRF protection does not apply
                .csrf(csrf -> csrf.disable())
                // Reuses the CORS rules declared in WebConfig
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // A JSON API serves no HTML of its own to sandbox, but every response still carries this header —
                // belt-and-braces against an error page or a misconfigured proxy serving something browsable
                .headers(headers -> headers.contentSecurityPolicy(csp ->
                        csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                "/api/platform/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                write(response, HttpStatus.UNAUTHORIZED,
                                        "Please sign in to continue"))
                        .accessDeniedHandler((request, response, exception) ->
                                write(response, HttpStatus.FORBIDDEN,
                                        "You are not allowed to perform this action")))
                // Built here rather than exposed as a bean: a Filter bean would also be
                // registered on the servlet container, running a second time per request.
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userRepository, platformAdminRepository),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Same error shape as GlobalExceptionHandler, so the frontend has a single case to
     * handle. Written by hand rather than serialised: the messages are constants defined
     * just above, and Spring Boot 4 exposes no injectable Jackson 2 ObjectMapper.
     */
    private void write(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":%d,\"message\":\"%s\",\"errors\":null}".formatted(status.value(), message));
    }
}
