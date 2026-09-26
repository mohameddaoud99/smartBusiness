package com.sales.smartBusiness.bankaccount;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyBankAccountServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private CompanyBankAccountRepository bankAccountRepository;
    @Mock private CompanyService companyService;
    @Mock private CompanyBankAccountMapper bankAccountMapper;
    @Mock private CurrentUser currentUser;
    @Mock private AuditService auditService;

    @InjectMocks private CompanyBankAccountService service;

    private CompanyBankAccount account;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(COMPANY_ID);

        account = new CompanyBankAccount();
        account.setId(1L);
        account.setCompany(company);
        account.setLabel("Main account");
        account.setRib("07000000000000000000");
        account.setCurrency("TND");

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(bankAccountMapper.toResponse(any())).thenReturn(new CompanyBankAccountResponse());
    }

    private CompanyBankAccountRequest request() {
        CompanyBankAccountRequest request = new CompanyBankAccountRequest();
        request.setLabel("Second account");
        request.setRib("08111111111111111111");
        request.setCurrency("EUR");
        return request;
    }

    @Test
    @DisplayName("a new account is attached to the caller's company and audited")
    void createAttachesCompany() {
        CompanyBankAccount created = new CompanyBankAccount();
        when(bankAccountMapper.toEntity(any())).thenReturn(created);
        when(companyService.currentReference()).thenReturn(new Company());
        when(bankAccountRepository.save(created)).thenReturn(created);

        service.create(request());

        assertThat(created.getCompany()).isNotNull();
        verify(auditService).record(eq(AuditAction.BANK_ACCOUNT_CREATED),
                eq(AuditEntity.BANK_ACCOUNT), any(), any());
    }

    @Test
    @DisplayName("an account of another company is not found")
    void accountOfAnotherCompanyIsNotFound() {
        when(bankAccountRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(bankAccountMapper, never()).updateEntity(any(), any());
    }

    @Test
    @DisplayName("deleting removes the row and writes an audit line")
    void deleteRemovesAndAudits() {
        when(bankAccountRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(account));

        service.delete(1L);

        verify(bankAccountRepository).delete(account);
        verify(auditService).record(eq(AuditAction.BANK_ACCOUNT_DELETED),
                eq(AuditEntity.BANK_ACCOUNT), eq(1L), any());
    }
}
