package com.sales.smartBusiness.auth;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.branch.BranchService;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.company.CompanyStatus;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.InvalidCredentialsException;
import com.sales.smartBusiness.company.BusinessModule;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.numbering.NumberingService;
import com.sales.smartBusiness.role.Role;
import com.sales.smartBusiness.role.RoleService;
import com.sales.smartBusiness.role.SystemRole;
import com.sales.smartBusiness.tax.TaxService;
import com.sales.smartBusiness.warehouse.WarehouseService;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.security.JwtService;
import com.sales.smartBusiness.user.User;
import com.sales.smartBusiness.user.UserMapper;
import com.sales.smartBusiness.user.UserRepository;
import com.sales.smartBusiness.user.UserService;
import com.sales.smartBusiness.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    /** Same message whichever half of the pair is wrong — no account enumeration. */
    private static final String REJECTED = "Invalid email or password";

    @Value("${smartbusiness.login.lockout-threshold:5}")
    private int lockoutThreshold;

    @Value("${smartbusiness.login.lockout-duration:PT15M}")
    private Duration lockoutDuration;

    private final CompanyService companyService;
    private final BranchService branchService;
    private final RoleService roleService;
    private final TaxService taxService;
    private final NumberingService numberingService;
    private final WarehouseService warehouseService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    /**
     * Sign-up: company, standard roles, main branch, then the administrator.
     * The account is usable straight away — the response already carries a token.
     */
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("This email is already registered");
        }

        Company company = companyService.register(request.getCompanyName().trim(), email);
        roleService.createSystemRoles(company);
        taxService.createDefaults(company);
        numberingService.createDefaults(company);
        warehouseService.createDefaults(company);
        branchService.createMain(company);

        Role admin = roleService.getSystemRole(company.getId(), SystemRole.COMPANY_ADMIN);

        User user = new User();
        user.setCompany(company);
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setUsername(usernameFrom(email));
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(new HashSet<>(Set.of(admin)));
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.recordFor(user, AuditAction.USER_CREATED, AuditEntity.USER, user.getId(),
                "Company created and first administrator registered");

        return issueToken(user);
    }

    public AuthResponse login(LoginRequest request) {
        // An unknown email belongs to no company, so there is no trail to write it to
        User user = userRepository.findByEmailWithRoles(request.getEmail().trim())
                .orElseThrow(() -> new InvalidCredentialsException(REJECTED));

        LocalDateTime now = LocalDateTime.now();
        if (user.isLockedOut(now)) {
            // No audit write, no attempt counted: it would only extend a lockout an attacker keeps hitting
            throw new InvalidCredentialsException(
                    "Too many failed attempts. Please try again in a few minutes.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.recordLoginFailure(lockoutThreshold, lockoutDuration, now);
            // Its own transaction: the one this method is running in is about to be rolled back by the throw below
            userService.saveLoginOutcome(user);
            auditService.recordLoginFailure(user, user.isLockedOut(now)
                    ? "Wrong password - account locked out" : "Wrong password");
            throw new InvalidCredentialsException(REJECTED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.recordLoginFailure(user, "Account is " + user.getStatus());
            throw new InvalidCredentialsException(
                    "This account is not active. Please contact your administrator.");
        }
        if (user.getCompany().getStatus() != CompanyStatus.ACTIVE) {
            auditService.recordLoginFailure(user, "Company account is suspended");
            throw new InvalidCredentialsException(
                    "This company account is suspended. Please contact support.");
        }

        user.recordLoginSuccess(now);
        auditService.recordFor(user, AuditAction.LOGIN_SUCCESS, AuditEntity.USER, user.getId(), null);

        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public SessionResponse me() {
        return toSession(loadCurrentUser());
    }

    public SessionResponse updateProfile(UpdateProfileRequest request) {
        User user = loadCurrentUser();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        String phone = request.getPhone();
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());

        auditService.record(AuditAction.USER_UPDATED, AuditEntity.USER, user.getId(),
                "Profile updated by the account holder");

        return toSession(user);
    }

    public void changePassword(ChangePasswordRequest request) {
        User user = loadCurrentUser();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Your current password is not correct");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        auditService.record(AuditAction.PASSWORD_CHANGED, AuditEntity.USER, user.getId(),
                "Password changed by the account holder");
    }

    private User loadCurrentUser() {
        return userRepository.findByIdWithRoles(currentUser.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", currentUser.id()));
    }

    private AuthResponse issueToken(User user) {
        return new AuthResponse(jwtService.generate(user), jwtService.expiresAt(), toSession(user));
    }

    private SessionResponse toSession(User user) {
        SessionResponse session = new SessionResponse();
        session.setId(user.getId());
        session.setFullName(user.getFullName());
        session.setFirstName(user.getFirstName());
        session.setLastName(user.getLastName());
        session.setUsername(user.getUsername());
        session.setEmail(user.getEmail());
        session.setPhone(user.getPhone());
        session.setCompanyId(user.getCompany().getId());
        session.setCompanyName(user.getCompany().getName());
        session.setRoles(userMapper.toRoleSummaries(user.getRoles()));
        session.setPermissions(user.collectPermissions());
        session.setEnabledModules(BusinessModule.enabledAmong(user.getCompany().getEnabledModules()));
        return session;
    }

    /**
     * The sign-up form does not ask for a username: the company is brand new, so the
     * local part of the email is free and good enough as a starting display name.
     */
    private String usernameFrom(String email) {
        String candidate = email.substring(0, email.indexOf('@'))
                .replaceAll("[^a-zA-Z0-9._-]", ".");
        return candidate.length() > 50 ? candidate.substring(0, 50) : candidate;
    }
}
