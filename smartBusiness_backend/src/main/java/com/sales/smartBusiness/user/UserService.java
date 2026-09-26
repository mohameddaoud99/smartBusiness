package com.sales.smartBusiness.user;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditLogResponse;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.branch.Branch;
import com.sales.smartBusiness.branch.BranchService;
import com.sales.smartBusiness.common.SearchPattern;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.role.Role;
import com.sales.smartBusiness.role.RoleService;
import com.sales.smartBusiness.role.SystemRole;
import com.sales.smartBusiness.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final BranchService branchService;
    private final CompanyService companyService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    /**
     * Persists the login-lockout bookkeeping ({@code failedLoginAttempts}, {@code lockedUntil}) in its own
     * transaction, so it survives whatever the caller does next — {@code AuthService.login} throws once it has
     * recorded a wrong password, which would otherwise roll the count back with it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLoginOutcome(User user) {
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String search, UserStatus status, Long roleId, Pageable pageable) {
        return userRepository.search(currentUser.companyId(), SearchPattern.like(search), status, roleId, pageable)
                .map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(getUser(id));
    }

    /**
     * The account's own trail, read from the company audit log.
     * Guarded by USER_VIEW rather than AUDIT_VIEW: reading the history of a user you are
     * already allowed to manage is part of managing them.
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> findHistory(Long id) {
        getUser(id); // 404 instead of an empty list when the user does not exist
        return auditService.findFor(AuditEntity.USER, id);
    }

    public UserResponse create(UserRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessRuleException("Password is required when creating a user");
        }
        checkEmailIsFree(request.getEmail(), null);
        checkUsernameIsFree(request.getUsername(), null);
        Set<Role> roles = roleService.resolveAssignable(request.getRoleIds());
        Branch branch = resolveBranch(request.getBranchId());

        User user = userMapper.toEntity(request);
        user.setCompany(companyService.currentReference());
        user.setBranch(branch);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(roles);
        userRepository.save(user);

        record(AuditAction.USER_CREATED, user, "Account created with " + describe(roles));
        return userMapper.toResponse(user);
    }

    public UserResponse update(Long id, UserRequest request) {
        User user = getUser(id);

        checkEmailIsFree(request.getEmail(), id);
        checkUsernameIsFree(request.getUsername(), id);

        Set<Role> newRoles = roleService.resolveAssignable(request.getRoleIds());
        boolean rolesChanged = !newRoles.equals(user.getRoles());

        // No privilege escalation: an administrator cannot widen their own access
        if (rolesChanged && id.equals(currentUser.id())) {
            throw new BusinessRuleException("You cannot change your own roles");
        }

        String previousRoles = describe(user.getRoles());
        boolean wasAdmin = user.hasRole(SystemRole.COMPANY_ADMIN.name());

        userMapper.updateEntity(request, user);
        user.setBranch(resolveBranch(request.getBranchId()));
        user.setRoles(newRoles);

        if (rolesChanged) {
            record(AuditAction.USER_ROLES_CHANGED, user,
                    "Roles changed from " + previousRoles + " to " + describe(newRoles));
        } else {
            record(AuditAction.USER_UPDATED, user, "Profile details updated");
        }

        ensureCompanyKeepsAnAdmin(user, wasAdmin);
        return userMapper.toResponse(user);
    }

    public UserResponse activate(Long id) {
        User user = getUser(id);
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessRuleException("This user is already active");
        }
        user.setStatus(UserStatus.ACTIVE);
        record(AuditAction.USER_ACTIVATED, user, "Account activated");
        return userMapper.toResponse(user);
    }

    public UserResponse deactivate(Long id) {
        User user = getUser(id);
        if (id.equals(currentUser.id())) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BusinessRuleException("This user is already inactive");
        }

        boolean wasAdmin = user.hasRole(SystemRole.COMPANY_ADMIN.name());
        user.setStatus(UserStatus.INACTIVE);
        record(AuditAction.USER_DISABLED, user, "Account deactivated");

        ensureCompanyKeepsAnAdmin(user, wasAdmin);
        return userMapper.toResponse(user);
    }

    public void resetPassword(Long id, String newPassword) {
        User user = getUser(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        record(AuditAction.USER_PASSWORD_RESET, user, "Password reset by an administrator");
    }

    /** A user is only ever reachable from inside their own company. */
    private User getUser(Long id) {
        return userRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private void checkEmailIsFree(String email, Long excludedId) {
        boolean taken = excludedId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, excludedId);
        if (taken) {
            throw new DuplicateResourceException("This email is already registered");
        }
    }

    private void checkUsernameIsFree(String username, Long excludedId) {
        Long companyId = currentUser.companyId();
        boolean taken = excludedId == null
                ? userRepository.existsByCompanyIdAndUsernameIgnoreCase(companyId, username)
                : userRepository.existsByCompanyIdAndUsernameIgnoreCaseAndIdNot(companyId, username, excludedId);
        if (taken) {
            throw new DuplicateResourceException("This username is already taken");
        }
    }

    /** A branch is where someone works, not something they are granted: no permission check. */
    private Branch resolveBranch(Long branchId) {
        return branchId == null ? null : branchService.getAssignable(branchId);
    }

    /** Guards against a company locking itself out of its own administration. */
    private void ensureCompanyKeepsAnAdmin(User user, boolean wasAdmin) {
        if (!wasAdmin) {
            return;
        }
        boolean stillAdmin = user.getStatus() == UserStatus.ACTIVE
                && user.hasRole(SystemRole.COMPANY_ADMIN.name());
        if (stillAdmin) {
            return;
        }

        long remaining = userRepository.countOtherActiveHoldersOfRole(
                currentUser.companyId(), SystemRole.COMPANY_ADMIN.name(), UserStatus.ACTIVE, user.getId());

        if (remaining == 0) {
            throw new BusinessRuleException(
                    "The company must keep at least one active administrator");
        }
    }

    private String describe(Set<Role> roles) {
        if (roles.isEmpty()) {
            return "no role";
        }
        return roles.stream()
                .map(Role::getLabel)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private void record(AuditAction action, User user, String detail) {
        auditService.record(action, AuditEntity.USER, user.getId(), detail);
    }
}
