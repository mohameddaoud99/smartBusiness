package com.sales.smartBusiness.branch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Every method carries the company: a branch is only ever reachable from inside
 * the company it belongs to.
 */
public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByIdAndCompanyId(Long id, Long companyId);

    Page<Branch> findByCompanyId(Long companyId, Pageable pageable);

    boolean existsByCompanyIdAndCodeIgnoreCase(Long companyId, String code);

    boolean existsByCompanyIdAndCodeIgnoreCaseAndIdNot(Long companyId, String code, Long id);

    /** Lock-out guard: a company must keep at least one site it can operate from. */
    long countByCompanyIdAndStatusAndIdNot(Long companyId, BranchStatus status, Long id);
}
