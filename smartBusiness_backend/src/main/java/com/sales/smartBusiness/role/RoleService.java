package com.sales.smartBusiness.role;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final CompanyService companyService;
    private final RoleMapper roleMapper;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    /**
     * Not paginated: a company has a handful of roles and the user form needs them all.
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findByCompanyIdOrderByLabelAsc(currentUser.companyId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(Long id) {
        return toResponse(getRole(id));
    }

    /** The catalogue the permission matrix is drawn from. */
    @Transactional(readOnly = true)
    public List<PermissionModuleResponse> permissionCatalogue() {
        return Arrays.stream(PermissionModule.values())
                .map(module -> new PermissionModuleResponse(
                        module.name(),
                        module.getLabel(),
                        Arrays.stream(Permission.values())
                                .filter(permission -> permission.getModule() == module)
                                .map(permission -> new PermissionModuleResponse.PermissionResponse(
                                        permission.name(), permission.getLabel()))
                                .toList()))
                .toList();
    }

    public RoleResponse create(RoleRequest request) {
        checkPermissionsAreGrantable(request.getPermissions());

        String name = nameFrom(request.getLabel());
        if (roleRepository.existsByCompanyIdAndNameIgnoreCase(currentUser.companyId(), name)) {
            throw new DuplicateResourceException("A role with this name already exists");
        }

        Role role = new Role();
        role.setCompany(companyService.currentReference());
        role.setName(name);
        role.setLabel(request.getLabel().trim());
        role.setDescription(request.getDescription());
        role.setSystem(false);
        role.setPermissions(copyOf(request.getPermissions()));
        roleRepository.save(role);

        auditService.record(AuditAction.ROLE_CREATED, AuditEntity.ROLE, role.getId(),
                "Role \"" + role.getLabel() + "\" created with "
                        + role.getPermissions().size() + " permission(s)");

        return toResponse(role);
    }

    /**
     * The stable name is fixed at creation; renaming only changes the displayed label.
     */
    public RoleResponse update(Long id, RoleRequest request) {
        Role role = getRole(id);
        if (role.isSystem()) {
            throw new BusinessRuleException("Standard roles cannot be modified");
        }
        checkPermissionsAreGrantable(request.getPermissions());

        int previousCount = role.getPermissions().size();
        role.setLabel(request.getLabel().trim());
        role.setDescription(request.getDescription());
        role.setPermissions(copyOf(request.getPermissions()));

        auditService.record(AuditAction.ROLE_UPDATED, AuditEntity.ROLE, role.getId(),
                "Role \"" + role.getLabel() + "\" now has " + role.getPermissions().size()
                        + " permission(s), was " + previousCount);

        return toResponse(role);
    }

    public void delete(Long id) {
        Role role = getRole(id);
        if (role.isSystem()) {
            throw new BusinessRuleException("Standard roles cannot be deleted");
        }

        long holders = roleRepository.countHolders(id);
        if (holders > 0) {
            throw new BusinessRuleException(
                    "This role is still assigned to " + holders
                            + " user(s). Remove it from them before deleting it.");
        }

        roleRepository.delete(role);

        auditService.record(AuditAction.ROLE_DELETED, AuditEntity.ROLE, id,
                "Role \"" + role.getLabel() + "\" deleted");
    }

    /**
     * The roles a user form asks to assign: all of them must belong to the caller's
     * company, and together they must not hand out more than the caller holds.
     */
    @Transactional(readOnly = true)
    public Set<Role> resolveAssignable(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Role> roles = roleRepository.findByCompanyIdAndIdIn(currentUser.companyId(), roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessRuleException("One of the selected roles does not exist");
        }

        checkPermissionsAreGrantable(roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class))));

        return new HashSet<>(roles);
    }

    /**
     * A standard role of a given company, by id rather than through CurrentUser: at
     * registration nobody is signed in yet.
     */
    @Transactional(readOnly = true)
    public Role getSystemRole(Long companyId, SystemRole systemRole) {
        return roleRepository.findByCompanyIdAndName(companyId, systemRole.name())
                .orElseThrow(() -> new IllegalStateException(
                        "System role " + systemRole + " is missing for company " + companyId));
    }

    /** Called once per company, at registration. */
    public void createSystemRoles(Company company) {
        for (SystemRole definition : SystemRole.values()) {
            Role role = new Role();
            role.setCompany(company);
            role.setName(definition.name());
            role.setLabel(definition.getLabel());
            role.setDescription(definition.getDescription());
            role.setSystem(true);
            role.setPermissions(EnumSet.copyOf(definition.getPermissions()));
            roleRepository.save(role);
        }
    }

    /**
     * Re-applies the SystemRole definitions to every company at startup, so that a
     * permission added to a new module reaches the existing companies without a
     * data migration. Custom roles are never touched.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncSystemRoles() {
        for (SystemRole definition : SystemRole.values()) {
            for (Role role : roleRepository.findByNameAndSystemTrue(definition.name())) {
                if (!role.getPermissions().equals(definition.getPermissions())) {
                    role.setPermissions(EnumSet.copyOf(definition.getPermissions()));
                }
            }
        }
    }

    private Role getRole(Long id) {
        return roleRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Role", id));
    }

    /** No privilege escalation: a role cannot hand out more than its author holds. */
    private void checkPermissionsAreGrantable(Set<Permission> permissions) {
        if (!currentUser.permissions().containsAll(permissions)) {
            throw new BusinessRuleException(
                    "You cannot grant permissions that you do not have yourself");
        }
    }

    /**
     * Stable key derived from the label: "Responsable dépôt" becomes RESPONSABLE_DEPOT.
     * Accents are stripped so a French label does not turn into underscores.
     */
    private String nameFrom(String label) {
        String withoutAccents = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String name = withoutAccents.toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");

        if (name.isEmpty()) {
            throw new BusinessRuleException("The role name must contain at least one letter or digit");
        }
        return name.length() > 50 ? name.substring(0, 50) : name;
    }

    private Set<Permission> copyOf(Set<Permission> permissions) {
        return permissions.isEmpty()
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(permissions);
    }

    private RoleResponse toResponse(Role role) {
        RoleResponse response = roleMapper.toResponse(role);
        response.setUserCount(roleRepository.countHolders(role.getId()));
        return response;
    }
}
