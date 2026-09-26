package com.sales.smartBusiness.branch;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private BranchRepository branchRepository;
    @Mock private CompanyService companyService;
    @Mock private BranchMapper branchMapper;
    @Mock private CurrentUser currentUser;
    @Mock private AuditService auditService;

    @InjectMocks private BranchService branchService;

    private Branch branch;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(COMPANY_ID);

        branch = new Branch();
        branch.setId(1L);
        branch.setCompany(company);
        branch.setCode("TUNIS");
        branch.setName("Tunis");
        branch.setStatus(BranchStatus.ACTIVE);

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(branchMapper.toResponse(any())).thenReturn(new BranchResponse());
    }

    private BranchRequest request() {
        BranchRequest request = new BranchRequest();
        request.setCode("SFAX");
        request.setName("Sfax");
        return request;
    }

    @Test
    @DisplayName("a new branch is attached to the caller's company and starts active")
    void createAttachesCompanyAndActivates() {
        Branch created = new Branch();
        when(branchMapper.toEntity(any())).thenReturn(created);
        when(companyService.currentReference()).thenReturn(new Company());
        when(branchRepository.save(created)).thenReturn(created);

        branchService.create(request());

        assertThat(created.getStatus()).isEqualTo(BranchStatus.ACTIVE);
        assertThat(created.getCompany()).isNotNull();
    }

    @Test
    @DisplayName("a code already used in the company is refused")
    void duplicateCodeIsRefused() {
        when(branchRepository.existsByCompanyIdAndCodeIgnoreCase(COMPANY_ID, "SFAX")).thenReturn(true);

        assertThatThrownBy(() -> branchService.create(request()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("code");

        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("a branch of another company is not found")
    void branchOfAnotherCompanyIsNotFound() {
        when(branchRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a branch of another company cannot be assigned to a record")
    void branchOfAnotherCompanyIsNotAssignable() {
        when(branchRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.getAssignable(99L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("the last active branch cannot be deactivated")
    void lastActiveBranchIsProtected() {
        when(branchRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(branch));
        when(branchRepository.countByCompanyIdAndStatusAndIdNot(COMPANY_ID, BranchStatus.ACTIVE, 1L))
                .thenReturn(0L);

        assertThatThrownBy(() -> branchService.deactivate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one active branch");

        assertThat(branch.getStatus()).isEqualTo(BranchStatus.ACTIVE);
    }

    @Test
    @DisplayName("a branch is deactivated while another one stays active")
    void deactivateWorksWhenAnotherBranchRemains() {
        when(branchRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(branch));
        when(branchRepository.countByCompanyIdAndStatusAndIdNot(COMPANY_ID, BranchStatus.ACTIVE, 1L))
                .thenReturn(2L);

        branchService.deactivate(1L);

        assertThat(branch.getStatus()).isEqualTo(BranchStatus.INACTIVE);
    }

    @Test
    @DisplayName("deactivating an already inactive branch is refused")
    void deactivateRejectsAlreadyInactive() {
        branch.setStatus(BranchStatus.INACTIVE);
        when(branchRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(branch));

        assertThatThrownBy(() -> branchService.deactivate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already inactive");
    }
}
