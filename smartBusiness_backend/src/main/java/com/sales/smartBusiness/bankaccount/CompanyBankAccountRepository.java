package com.sales.smartBusiness.bankaccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every method carries the company: an account is only ever reachable from inside
 * the company it belongs to.
 */
public interface CompanyBankAccountRepository extends JpaRepository<CompanyBankAccount, Long> {

    List<CompanyBankAccount> findByCompanyIdOrderByLabelAsc(Long companyId);

    Optional<CompanyBankAccount> findByIdAndCompanyId(Long id, Long companyId);
}
