package com.sales.smartBusiness.role;

import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * The rules that keep the role editor safe: system roles are untouchable, nobody
 * grants what they do not hold, and a role in use cannot vanish under its holders.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private RoleRepository roleRepository;
    @Mock private CompanyService companyService;
    @Mock private RoleMapper roleMapper;
    @Mock private CurrentUser currentUser;
    @Mock private AuditService auditService;

    @InjectMocks private RoleService roleService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(currentUser.permissions()).thenReturn(EnumSet.allOf(Permission.class));
        lenient().when(roleMapper.toResponse(any())).thenReturn(new RoleResponse());
        lenient().when(companyService.currentReference()).thenReturn(company);
        lenient().when(roleRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private RoleRequest request(String label, Permission... permissions) {
        RoleRequest request = new RoleRequest();
        request.setLabel(label);
        request.setPermissions(EnumSet.copyOf(Set.of(permissions)));
        return request;
    }

    private Role systemRole() {
        Role role = new Role();
        role.setId(1L);
        role.setCompany(company);
        role.setName(SystemRole.COMPANY_ADMIN.name());
        role.setLabel("Company Administrator");
        role.setSystem(true);
        return role;
    }

    private Role customRole() {
        Role role = new Role();
        role.setId(2L);
        role.setCompany(company);
        role.setName("COMMERCIAL_SENIOR");
        role.setLabel("Commercial Senior");
        role.setSystem(false);
        return role;
    }

    // ----- Creation -----

    @Test
    @DisplayName("the stable name is derived from the label, accents stripped")
    void nameIsDerivedFromLabel() {
        roleService.create(request("Responsable dépôt", Permission.STOCK_VIEW));

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("RESPONSABLE_DEPOT");
        assertThat(captor.getValue().getLabel()).isEqualTo("Responsable dépôt");
        assertThat(captor.getValue().isSystem()).isFalse();
    }

    @Test
    @DisplayName("a label colliding with an existing role is refused")
    void duplicateNameIsRefused() {
        when(roleRepository.existsByCompanyIdAndNameIgnoreCase(COMPANY_ID, "COMMERCIAL_SENIOR"))
                .thenReturn(true);

        assertThatThrownBy(() -> roleService.create(request("Commercial Senior", Permission.SALE_VIEW)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("a label with no letter or digit is refused")
    void emptyNameIsRefused() {
        assertThatThrownBy(() -> roleService.create(request("---", Permission.SALE_VIEW)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one letter or digit");
    }

    @Test
    @DisplayName("a caller cannot build a role around a permission they lack")
    void cannotGrantPermissionsTheCallerLacks() {
        when(currentUser.permissions()).thenReturn(EnumSet.of(Permission.SALE_VIEW, Permission.ROLE_CREATE));

        assertThatThrownBy(() -> roleService.create(request("Super", Permission.STOCK_ADJUST)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("do not have yourself");

        verify(roleRepository, never()).save(any());
    }

    // ----- Assigning roles to a user -----

    @Test
    @DisplayName("a role from another company cannot be assigned")
    void rolesOutsideTheCompanyAreRejected() {
        when(roleRepository.findByCompanyIdAndIdIn(COMPANY_ID, Set.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> roleService.resolveAssignable(Set.of(99L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("roles cannot hand a user a permission the caller does not hold")
    void assignedRolesCannotExceedTheCaller() {
        when(currentUser.permissions()).thenReturn(EnumSet.of(Permission.USER_VIEW, Permission.USER_CREATE));
        Role stockManager = customRole();
        stockManager.setPermissions(EnumSet.of(Permission.STOCK_ADJUST));
        when(roleRepository.findByCompanyIdAndIdIn(COMPANY_ID, Set.of(2L))).thenReturn(List.of(stockManager));

        assertThatThrownBy(() -> roleService.resolveAssignable(Set.of(2L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("do not have yourself");
    }

    @Test
    @DisplayName("no role selected means no role — the secure default")
    void noRoleIdsResolveToNoRoles() {
        assertThat(roleService.resolveAssignable(null)).isEmpty();
        verifyNoInteractions(roleRepository);
    }

    // ----- System roles -----

    @Test
    @DisplayName("a standard role cannot be modified")
    void systemRoleCannotBeUpdated() {
        when(roleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(systemRole()));

        assertThatThrownBy(() -> roleService.update(1L, request("Renamed", Permission.SALE_VIEW)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be modified");
    }

    @Test
    @DisplayName("a standard role cannot be deleted")
    void systemRoleCannotBeDeleted() {
        when(roleRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(systemRole()));

        assertThatThrownBy(() -> roleService.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be deleted");

        verify(roleRepository, never()).delete(any());
    }

    // ----- Update and deletion -----

    @Test
    @DisplayName("updating changes the label and permissions but never the stable name")
    void updateKeepsTheStableName() {
        Role role = customRole();
        when(roleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(role));

        roleService.update(2L, request("Senior Sales", Permission.SALE_VIEW, Permission.SALE_CREATE));

        assertThat(role.getName()).isEqualTo("COMMERCIAL_SENIOR");
        assertThat(role.getLabel()).isEqualTo("Senior Sales");
        assertThat(role.getPermissions())
                .containsExactlyInAnyOrder(Permission.SALE_VIEW, Permission.SALE_CREATE);
    }

    @Test
    @DisplayName("a role still assigned to users cannot be deleted")
    void roleInUseCannotBeDeleted() {
        when(roleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(customRole()));
        when(roleRepository.countHolders(2L)).thenReturn(3L);

        assertThatThrownBy(() -> roleService.delete(2L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("3 user(s)");

        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("an unused custom role is deleted")
    void unusedRoleIsDeleted() {
        Role role = customRole();
        when(roleRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(role));
        when(roleRepository.countHolders(2L)).thenReturn(0L);

        roleService.delete(2L);

        verify(roleRepository).delete(role);
    }

    @Test
    @DisplayName("a role of another company is not found")
    void roleOfAnotherCompanyIsNotFound() {
        when(roleRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Catalogue -----

    @Test
    @DisplayName("the catalogue exposes every permission, grouped by module")
    void catalogueCoversEveryPermission() {
        List<PermissionModuleResponse> catalogue = roleService.permissionCatalogue();

        long exposed = catalogue.stream().mapToLong(module -> module.permissions().size()).sum();
        assertThat(exposed).isEqualTo(Permission.values().length);
        assertThat(catalogue).hasSize(PermissionModule.values().length);
        assertThat(catalogue).allSatisfy(module -> assertThat(module.permissions()).isNotEmpty());
    }

    // ----- System role seeding -----

    @Test
    @DisplayName("registration seeds one copy of every standard role for the company")
    void createSystemRolesSeedsThemAll() {
        roleService.createSystemRoles(company);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(SystemRole.values().length)).save(captor.capture());

        assertThat(captor.getAllValues())
                .allSatisfy(role -> {
                    assertThat(role.isSystem()).isTrue();
                    assertThat(role.getCompany()).isSameAs(company);
                })
                .anySatisfy(role -> {
                    assertThat(role.getName()).isEqualTo(SystemRole.COMPANY_ADMIN.name());
                    assertThat(role.getPermissions()).containsAll(EnumSet.allOf(Permission.class));
                });
    }

    @Test
    @DisplayName("startup re-applies the standard definitions to existing companies")
    void syncRepairsDriftedSystemRoles() {
        Role drifted = systemRole();
        drifted.setPermissions(EnumSet.of(Permission.USER_VIEW));
        when(roleRepository.findByNameAndSystemTrue(anyString())).thenReturn(List.of());
        when(roleRepository.findByNameAndSystemTrue(SystemRole.COMPANY_ADMIN.name()))
                .thenReturn(List.of(drifted));

        roleService.syncSystemRoles();

        assertThat(drifted.getPermissions()).isEqualTo(SystemRole.COMPANY_ADMIN.getPermissions());
    }
}
