package com.sales.smartBusiness.platform;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.common.SearchPattern;
import com.sales.smartBusiness.company.BusinessModule;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyRepository;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.role.PermissionModule;
import com.sales.smartBusiness.security.CurrentPlatformAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one place in the application allowed to reach a company by id alone, with no
 * {@code currentUser.companyId()} involved — because the caller here is a platform
 * admin, who by design stands outside every company.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlatformCompanyService {

    private final CompanyRepository companyRepository;
    private final CurrentPlatformAdmin currentPlatformAdmin;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<PlatformCompanyResponse> search(String search, Pageable pageable) {
        return companyRepository.search(SearchPattern.like(search), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PlatformCompanyResponse findById(Long id) {
        return toResponse(getCompany(id));
    }

    /**
     * Replaces the whole set in one go: toggling a module always moves every
     * {@link PermissionModule} it stands for together, never a partial one.
     */
    public PlatformCompanyResponse updateModules(Long id, Set<BusinessModule> modules) {
        Company company = getCompany(id);

        Set<PermissionModule> newModules = modules.stream()
                .flatMap(module -> module.getPermissionModules().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionModule.class)));
        company.setEnabledModules(newModules);

        auditService.recordForPlatform(company.getId(), currentPlatformAdmin.email(),
                AuditAction.COMPANY_MODULES_CHANGED, AuditEntity.COMPANY, company.getId(),
                "Modules set to " + describe(modules));

        return toResponse(company);
    }

    /** Never scoped by a caller's company — the whole point of this service. */
    private Company getCompany(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Company", id));
    }

    private String describe(Set<BusinessModule> modules) {
        if (modules.isEmpty()) {
            return "none";
        }
        return modules.stream()
                .map(BusinessModule::getLabel)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private PlatformCompanyResponse toResponse(Company company) {
        PlatformCompanyResponse response = new PlatformCompanyResponse();
        response.setId(company.getId());
        response.setName(company.getName());
        response.setEmail(company.getEmail());
        response.setStatus(company.getStatus());
        response.setEnabledModules(BusinessModule.enabledAmong(company.getEnabledModules()));
        response.setCreatedAt(company.getCreatedAt());
        return response;
    }
}
