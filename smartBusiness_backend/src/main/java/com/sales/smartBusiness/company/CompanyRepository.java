package com.sales.smartBusiness.company;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    /**
     * Every company, regardless of tenant — only {@code PlatformCompanyService} may call
     * this. Every other service reaches a company by {@code currentUser.companyId()},
     * never through an unscoped list.
     *
     * `search` is always a lower-case LIKE pattern ("%" when blank) — PostgreSQL cannot
     * infer the type of a NULL parameter inside LOWER().
     */
    @Query("SELECT c FROM Company c WHERE LOWER(c.name) LIKE :search")
    Page<Company> search(@Param("search") String search, Pageable pageable);
}
