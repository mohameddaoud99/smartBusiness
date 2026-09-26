package com.sales.smartBusiness.branch;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BranchService {

    private final BranchRepository branchRepository;
    private final CompanyService companyService;
    private final BranchMapper branchMapper;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<BranchResponse> findAll(Pageable pageable) {
        return branchRepository.findByCompanyId(currentUser.companyId(), pageable)
                .map(branchMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BranchResponse findById(Long id) {
        return branchMapper.toResponse(getBranch(id));
    }

    public BranchResponse create(BranchRequest request) {
        checkCodeIsFree(request.getCode(), null);

        Branch branch = branchMapper.toEntity(request);
        branch.setCompany(companyService.currentReference());
        branch.setStatus(BranchStatus.ACTIVE);
        branchRepository.save(branch);

        record(AuditAction.BRANCH_CREATED, branch, "Branch \"" + branch.getName() + "\" created");
        return branchMapper.toResponse(branch);
    }

    public BranchResponse update(Long id, BranchRequest request) {
        Branch branch = getBranch(id);
        checkCodeIsFree(request.getCode(), id);

        branchMapper.updateEntity(request, branch);

        record(AuditAction.BRANCH_UPDATED, branch, "Branch \"" + branch.getName() + "\" updated");
        return branchMapper.toResponse(branch);
    }

    public BranchResponse activate(Long id) {
        Branch branch = getBranch(id);
        if (branch.getStatus() == BranchStatus.ACTIVE) {
            throw new BusinessRuleException("This branch is already active");
        }
        branch.setStatus(BranchStatus.ACTIVE);

        record(AuditAction.BRANCH_ACTIVATED, branch, "Branch \"" + branch.getName() + "\" activated");
        return branchMapper.toResponse(branch);
    }

    public BranchResponse deactivate(Long id) {
        Branch branch = getBranch(id);
        if (branch.getStatus() == BranchStatus.INACTIVE) {
            throw new BusinessRuleException("This branch is already inactive");
        }

        long othersStillActive = branchRepository.countByCompanyIdAndStatusAndIdNot(
                currentUser.companyId(), BranchStatus.ACTIVE, id);
        if (othersStillActive == 0) {
            throw new BusinessRuleException("The company must keep at least one active branch");
        }

        branch.setStatus(BranchStatus.INACTIVE);

        record(AuditAction.BRANCH_DISABLED, branch, "Branch \"" + branch.getName() + "\" deactivated");
        return branchMapper.toResponse(branch);
    }

    /** Every company has at least one site, so registration creates this one. */
    public void createMain(Company company) {
        branchRepository.save(Branch.main(company));
    }

    /**
     * A branch another record wants to point at (e.g. a user's site). 422 rather than 404:
     * the branch is a value in the caller's form, not the resource of the request.
     */
    @Transactional(readOnly = true)
    public Branch getAssignable(Long id) {
        return branchRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new BusinessRuleException("The selected branch does not exist"));
    }

    private void record(AuditAction action, Branch branch, String detail) {
        auditService.record(action, AuditEntity.BRANCH, branch.getId(), detail);
    }

    private Branch getBranch(Long id) {
        return branchRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Branch", id));
    }

    private void checkCodeIsFree(String code, Long excludedId) {
        Long companyId = currentUser.companyId();
        boolean taken = excludedId == null
                ? branchRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)
                : branchRepository.existsByCompanyIdAndCodeIgnoreCaseAndIdNot(companyId, code, excludedId);
        if (taken) {
            throw new DuplicateResourceException("This branch code is already used");
        }
    }
}
