package com.sales.smartBusiness.branch;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Branches are business data, never a permission scope: what a user may do applies
 * to every branch of their company.
 */
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @PreAuthorize("hasAuthority('BRANCH_VIEW')")
    public Page<BranchResponse> findAll(
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return branchService.findAll(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BRANCH_VIEW')")
    public BranchResponse findById(@PathVariable Long id) {
        return branchService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BRANCH_CREATE')")
    public BranchResponse create(@Valid @RequestBody BranchRequest request) {
        return branchService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BRANCH_UPDATE')")
    public BranchResponse update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return branchService.update(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('BRANCH_DISABLE')")
    public BranchResponse activate(@PathVariable Long id) {
        return branchService.activate(id);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('BRANCH_DISABLE')")
    public BranchResponse deactivate(@PathVariable Long id) {
        return branchService.deactivate(id);
    }
}
