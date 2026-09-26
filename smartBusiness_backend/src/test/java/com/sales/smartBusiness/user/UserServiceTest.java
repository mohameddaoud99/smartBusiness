package com.sales.smartBusiness.user;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.branch.Branch;
import com.sales.smartBusiness.branch.BranchService;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.role.Permission;
import com.sales.smartBusiness.role.Role;
import com.sales.smartBusiness.role.RoleService;
import com.sales.smartBusiness.role.SystemRole;
import com.sales.smartBusiness.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Focused on business behaviour: company isolation, uniqueness, lifecycle rules,
 * privilege-escalation guards and history writing.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long CALLER_ID = 42L;

    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private RoleService roleService;
    @Mock private BranchService branchService;
    @Mock private CompanyService companyService;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CurrentUser currentUser;

    @InjectMocks private UserService userService;

    private Company company;
    private User existing;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);
        company.setName("ABC Distribution");

        existing = new User();
        existing.setId(1L);
        existing.setCompany(company);
        existing.setFirstName("Sonia");
        existing.setLastName("Trabelsi");
        existing.setUsername("s.trabelsi");
        existing.setEmail("sonia@example.com");
        existing.setStatus(UserStatus.ACTIVE);

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(currentUser.id()).thenReturn(CALLER_ID);
        lenient().when(currentUser.email()).thenReturn("admin@abc.test");
        lenient().when(currentUser.permissions()).thenReturn(EnumSet.allOf(Permission.class));
    }

    private UserRequest request() {
        UserRequest request = new UserRequest();
        request.setFirstName("Sonia");
        request.setLastName("Trabelsi");
        request.setUsername("s.trabelsi");
        request.setEmail("sonia@example.com");
        request.setStatus(UserStatus.ACTIVE);
        request.setPassword("Password123");
        return request;
    }

    private Branch branch(Long id, String name) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setCompany(company);
        branch.setCode(name.toUpperCase());
        branch.setName(name);
        return branch;
    }

    private Role role(Long id, String name, Permission... permissions) {
        Role role = new Role();
        role.setId(id);
        role.setCompany(company);
        role.setName(name);
        role.setLabel(name);
        role.setPermissions(permissions.length == 0
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(Arrays.asList(permissions)));
        return role;
    }

    /** The single audit line the operation under test was expected to write. */
    private AuditAction capturedAction() {
        ArgumentCaptor<AuditAction> captor = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditService).record(captor.capture(), eq(AuditEntity.USER), any(), any());
        return captor.getValue();
    }

    private String capturedDetail() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), eq(AuditEntity.USER), any(), captor.capture());
        return captor.getValue();
    }

    // ----- Creation -----

    @Test
    @DisplayName("create hashes the password, attaches the company and records a CREATED event")
    void createHashesPasswordAndRecordsHistory() {
        UserRequest request = request();
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.existsByCompanyIdAndUsernameIgnoreCase(COMPANY_ID, "s.trabelsi")).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(existing);
        when(companyService.currentReference()).thenReturn(company);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed-value");

        userService.create(request);

        assertThat(existing.getPasswordHash()).isEqualTo("hashed-value");
        assertThat(existing.getCompany()).isSameAs(company);
        assertThat(capturedAction()).isEqualTo(AuditAction.USER_CREATED);
    }

    @Test
    @DisplayName("create leaves a user with no role — that is the secure default")
    void createWithoutRolesGrantsNothing() {
        UserRequest request = request();
        when(userMapper.toEntity(request)).thenReturn(existing);
        when(companyService.currentReference()).thenReturn(company);

        userService.create(request);

        assertThat(existing.getRoles()).isEmpty();
        assertThat(capturedDetail()).contains("no role");
    }

    @Test
    @DisplayName("create rejects a username already used inside the company")
    void createRejectsDuplicateUsername() {
        when(userRepository.existsByCompanyIdAndUsernameIgnoreCase(COMPANY_ID, "s.trabelsi"))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.create(request()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("username");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rejects an email that already exists")
    void createRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("sonia@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("create requires a password")
    void createRequiresPassword() {
        UserRequest request = request();
        request.setPassword(null);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Password is required");
    }

    // ----- Roles and privilege escalation -----

    @Test
    @DisplayName("a refused role assignment stops the creation before anything is saved")
    void refusedRolesStopTheCreation() {
        UserRequest request = request();
        request.setRoleIds(Set.of(5L));
        when(roleService.resolveAssignable(Set.of(5L)))
                .thenThrow(new BusinessRuleException("You cannot grant permissions that you do not have yourself"));

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("do not have yourself");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a caller cannot change their own roles")
    void cannotChangeOwnRoles() {
        existing.setId(CALLER_ID);
        UserRequest request = request();
        request.setRoleIds(Set.of(5L));

        when(userRepository.findByIdAndCompanyId(CALLER_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(roleService.resolveAssignable(Set.of(5L)))
                .thenReturn(new HashSet<>(Set.of(role(5L, "SALES_MANAGER"))));

        assertThatThrownBy(() -> userService.update(CALLER_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("your own roles");
    }

    // ----- Branch (organisational, not a permission) -----

    @Test
    @DisplayName("create leaves the branch empty when none is selected")
    void createWithoutBranchLeavesItEmpty() {
        UserRequest request = request();
        when(userMapper.toEntity(request)).thenReturn(existing);
        when(companyService.currentReference()).thenReturn(company);

        userService.create(request);

        assertThat(existing.getBranch()).isNull();
        verifyNoInteractions(branchService);
    }

    @Test
    @DisplayName("create attaches the selected branch")
    void createAttachesSelectedBranch() {
        Branch tunis = branch(10L, "Tunis");
        UserRequest request = request();
        request.setBranchId(10L);

        when(userMapper.toEntity(request)).thenReturn(existing);
        when(companyService.currentReference()).thenReturn(company);
        when(branchService.getAssignable(10L)).thenReturn(tunis);

        userService.create(request);

        assertThat(existing.getBranch()).isSameAs(tunis);
    }

    @Test
    @DisplayName("update replaces the branch, unlike roles this needs no extra permission")
    void updateReplacesBranch() {
        Branch sfax = branch(11L, "Sfax");
        existing.setBranch(branch(10L, "Tunis"));

        UserRequest request = request();
        request.setBranchId(11L);

        when(userRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(branchService.getAssignable(11L)).thenReturn(sfax);

        userService.update(1L, request);

        assertThat(existing.getBranch()).isSameAs(sfax);
    }

    // ----- Update -----

    @Test
    @DisplayName("update records ROLE_CHANGED with the previous and the new roles")
    void updateRecordsRoleChange() {
        existing.setRoles(new HashSet<>(Set.of(role(3L, "VIEWER"))));

        UserRequest request = request();
        request.setRoleIds(Set.of(5L));

        when(userRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(roleService.resolveAssignable(Set.of(5L)))
                .thenReturn(new HashSet<>(Set.of(role(5L, "SALES_MANAGER"))));

        userService.update(1L, request);

        assertThat(capturedAction()).isEqualTo(AuditAction.USER_ROLES_CHANGED);
        assertThat(capturedDetail()).contains("VIEWER").contains("SALES_MANAGER");
    }

    @Test
    @DisplayName("update records a plain UPDATED event when the roles are unchanged")
    void updateRecordsUpdatedWhenRolesUnchanged() {
        when(userRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        userService.update(1L, request());

        assertThat(capturedAction()).isEqualTo(AuditAction.USER_UPDATED);
    }

    @Test
    @DisplayName("a user of another company is not found")
    void updateFailsForAnotherCompany() {
        when(userRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(99L, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Lifecycle -----

    @Test
    @DisplayName("deactivate flips the status and records the event")
    void deactivateChangesStatus() {
        when(userRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        userService.deactivate(1L);

        assertThat(existing.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(capturedAction()).isEqualTo(AuditAction.USER_DISABLED);
    }

    @Test
    @DisplayName("deactivate rejects an already inactive user")
    void deactivateRejectsAlreadyInactive() {
        existing.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.deactivate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already inactive");
    }

    @Test
    @DisplayName("a caller cannot deactivate their own account")
    void deactivateRejectsSelf() {
        existing.setId(CALLER_ID);
        when(userRepository.findByIdAndCompanyId(CALLER_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.deactivate(CALLER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("your own account");
    }

    @Test
    @DisplayName("the last active administrator cannot be deactivated")
    void deactivateKeepsAtLeastOneAdmin() {
        existing.setRoles(new HashSet<>(Set.of(role(1L, SystemRole.COMPANY_ADMIN.name()))));
        when(userRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(userRepository.countOtherActiveHoldersOfRole(
                COMPANY_ID, SystemRole.COMPANY_ADMIN.name(), UserStatus.ACTIVE, 1L)).thenReturn(0L);

        assertThatThrownBy(() -> userService.deactivate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one active administrator");
    }

    @Test
    @DisplayName("activate rejects an already active user")
    void activateRejectsAlreadyActive() {
        when(userRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.activate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already active");
    }

    // ----- Search -----

    @Test
    @DisplayName("blank search becomes a match-all pattern, never null")
    void blankSearchBecomesMatchAll() {
        when(userRepository.search(eq(COMPANY_ID), eq("%"), any(), any(), any()))
                .thenReturn(Page.empty());

        userService.search("   ", null, null, PageRequest.of(0, 10));

        verify(userRepository).search(eq(COMPANY_ID), eq("%"), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("search term is lower-cased, wrapped in wildcards and scoped to the company")
    void searchTermIsNormalised() {
        when(userRepository.search(eq(COMPANY_ID), eq("%sonia%"), any(), any(), any()))
                .thenReturn(Page.empty());

        userService.search("  SONIA ", null, null, PageRequest.of(0, 10));

        verify(userRepository).search(eq(COMPANY_ID), eq("%sonia%"), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("resetPassword hashes the new value and records the event")
    void resetPasswordHashes() {
        when(userRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("new-hash");

        userService.resetPassword(1L, "NewPassword1");

        assertThat(existing.getPasswordHash()).isEqualTo("new-hash");
        assertThat(capturedAction()).isEqualTo(AuditAction.USER_PASSWORD_RESET);
    }
}
