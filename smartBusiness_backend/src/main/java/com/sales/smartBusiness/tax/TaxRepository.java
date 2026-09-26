package com.sales.smartBusiness.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

interface TaxRepository extends JpaRepository<Tax, Long> {

    List<Tax> findByCompanyIdOrderByKindAscNameAsc(Long companyId);

    Optional<Tax> findByIdAndCompanyId(Long id, Long companyId);

    /** Resolves the default taxes a product form asks for, scoped to the caller's company. */
    List<Tax> findByCompanyIdAndIdIn(Long companyId, Set<Long> ids);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);
}
