package com.sales.smartBusiness.bankaccount;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Company-wide configuration, guarded by the same permissions as the company profile:
 * whoever may configure the company may configure its bank accounts.
 */
@RestController
@RequestMapping("/api/settings/bank-accounts")
@RequiredArgsConstructor
public class CompanyBankAccountController {

    private final CompanyBankAccountService bankAccountService;

    @GetMapping
    @PreAuthorize("hasAuthority('COMPANY_VIEW')")
    public List<CompanyBankAccountResponse> findAll() {
        return bankAccountService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_VIEW')")
    public CompanyBankAccountResponse findById(@PathVariable Long id) {
        return bankAccountService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public CompanyBankAccountResponse create(@Valid @RequestBody CompanyBankAccountRequest request) {
        return bankAccountService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public CompanyBankAccountResponse update(@PathVariable Long id,
                                             @Valid @RequestBody CompanyBankAccountRequest request) {
        return bankAccountService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public void delete(@PathVariable Long id) {
        bankAccountService.delete(id);
    }
}
