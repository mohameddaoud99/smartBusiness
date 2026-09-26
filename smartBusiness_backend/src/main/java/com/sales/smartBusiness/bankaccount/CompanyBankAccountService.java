package com.sales.smartBusiness.bankaccount;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyBankAccountService {

    private final CompanyBankAccountRepository bankAccountRepository;
    private final CompanyService companyService;
    private final CompanyBankAccountMapper bankAccountMapper;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CompanyBankAccountResponse> findAll() {
        return bankAccountRepository.findByCompanyIdOrderByLabelAsc(currentUser.companyId())
                .stream()
                .map(bankAccountMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyBankAccountResponse findById(Long id) {
        return bankAccountMapper.toResponse(getAccount(id));
    }

    public CompanyBankAccountResponse create(CompanyBankAccountRequest request) {
        CompanyBankAccount account = bankAccountMapper.toEntity(request);
        account.setCompany(companyService.currentReference());
        bankAccountRepository.save(account);

        auditService.record(AuditAction.BANK_ACCOUNT_CREATED, AuditEntity.BANK_ACCOUNT,
                account.getId(), "Bank account \"" + account.getLabel() + "\" added");
        return bankAccountMapper.toResponse(account);
    }

    public CompanyBankAccountResponse update(Long id, CompanyBankAccountRequest request) {
        CompanyBankAccount account = getAccount(id);
        bankAccountMapper.updateEntity(request, account);

        auditService.record(AuditAction.BANK_ACCOUNT_UPDATED, AuditEntity.BANK_ACCOUNT,
                account.getId(), "Bank account \"" + account.getLabel() + "\" updated");
        return bankAccountMapper.toResponse(account);
    }

    public void delete(Long id) {
        CompanyBankAccount account = getAccount(id);
        bankAccountRepository.delete(account);

        auditService.record(AuditAction.BANK_ACCOUNT_DELETED, AuditEntity.BANK_ACCOUNT,
                id, "Bank account \"" + account.getLabel() + "\" removed");
    }

    private CompanyBankAccount getAccount(Long id) {
        return bankAccountRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Bank account", id));
    }
}
