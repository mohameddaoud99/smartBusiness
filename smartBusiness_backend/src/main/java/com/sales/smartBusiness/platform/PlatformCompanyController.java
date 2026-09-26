package com.sales.smartBusiness.platform;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Every endpoint here reaches a company by id — the one deliberate exception to
 * "never trust an id from the client for tenant data". It is safe specifically because
 * the caller is a platform admin, not a member of any company, and every method is
 * guarded by the single authority a platform token can ever carry.
 */
@RestController
@RequestMapping("/api/platform/companies")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class PlatformCompanyController {

    private final PlatformCompanyService platformCompanyService;

    @GetMapping
    public Page<PlatformCompanyResponse> search(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return platformCompanyService.search(search, pageable);
    }

    @GetMapping("/{id}")
    public PlatformCompanyResponse findById(@PathVariable Long id) {
        return platformCompanyService.findById(id);
    }

    @PutMapping("/{id}/modules")
    public PlatformCompanyResponse updateModules(@PathVariable Long id,
                                                 @Valid @RequestBody UpdateCompanyModulesRequest request) {
        return platformCompanyService.updateModules(id, request.getModules());
    }
}
