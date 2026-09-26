package com.sales.smartBusiness.tax;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxService {

    private final TaxRepository taxRepository;
    private final CompanyService companyService;
    private final TaxMapper taxMapper;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<TaxResponse> findAll() {
        return taxRepository.findByCompanyIdOrderByKindAscNameAsc(currentUser.companyId())
                .stream()
                .map(taxMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaxResponse findById(Long id) {
        return taxMapper.toResponse(getTax(id));
    }

    public TaxResponse create(TaxRequest request) {
        checkNameIsFree(request.getName(), null);

        Tax tax = taxMapper.toEntity(request);
        tax.setCompany(companyService.currentReference());
        tax.setSystem(false);
        normalise(tax);
        validateAmounts(tax);
        taxRepository.save(tax);

        auditService.record(AuditAction.TAX_CREATED, AuditEntity.TAX, tax.getId(),
                "Tax \"" + tax.getName() + "\" created");
        return taxMapper.toResponse(tax);
    }

    public TaxResponse update(Long id, TaxRequest request) {
        Tax tax = getTax(id);
        if (request.getKind() != tax.getKind()) {
            throw new BusinessRuleException("A tax's type cannot be changed");
        }
        checkNameIsFree(request.getName(), id);

        taxMapper.updateEntity(request, tax);
        normalise(tax);
        validateAmounts(tax);

        auditService.record(AuditAction.TAX_UPDATED, AuditEntity.TAX, tax.getId(),
                "Tax \"" + tax.getName() + "\" updated");
        return taxMapper.toResponse(tax);
    }

    public void delete(Long id) {
        Tax tax = getTax(id);
        if (tax.isSystem()) {
            throw new BusinessRuleException(
                    "This is a standard tax. Deactivate it instead of deleting it.");
        }
        taxRepository.delete(tax);

        auditService.record(AuditAction.TAX_DELETED, AuditEntity.TAX, id,
                "Tax \"" + tax.getName() + "\" deleted");
    }

    /**
     * The taxes a product form picks as defaults: all of them must belong to the
     * caller's company. Unlike a role's permissions, a tax carries no grant to check —
     * any tax of the company is fair game.
     */
    @Transactional(readOnly = true)
    public Set<Tax> resolveAssignable(Set<Long> taxIds) {
        if (taxIds == null || taxIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Tax> taxes = taxRepository.findByCompanyIdAndIdIn(currentUser.companyId(), taxIds);
        if (taxes.size() != taxIds.size()) {
            throw new BusinessRuleException("One of the selected taxes does not exist");
        }
        return new HashSet<>(taxes);
    }

    /** The standard Tunisian set, seeded for a brand new company. */
    public void createDefaults(Company company) {
        taxRepository.saveAll(List.of(
                systemTax(company, "TVA 19%", TaxKind.VAT_RATE, bd("19.000"), null, false, true, true),
                systemTax(company, "TVA 13%", TaxKind.VAT_RATE, bd("13.000"), null, false, false, false),
                systemTax(company, "TVA 7%", TaxKind.VAT_RATE, bd("7.000"), null, false, false, false),
                systemTax(company, "TVA 0%", TaxKind.VAT_RATE, bd("0.000"), null, false, false, true),
                systemTax(company, "FODEC", TaxKind.PERCENTAGE_SURCHARGE, bd("1.000"), null, true, true, true),
                systemTax(company, "Timbre fiscal", TaxKind.FIXED_PER_DOCUMENT, null, bd("1.000"), false, true, true)
        ));
    }

    private Tax systemTax(Company company, String name, TaxKind kind, BigDecimal rate, BigDecimal amount,
                          boolean includedInVatBase, boolean activeByDefault, boolean active) {
        Tax tax = new Tax();
        tax.setCompany(company);
        tax.setName(name);
        tax.setKind(kind);
        tax.setRate(rate);
        tax.setAmount(amount);
        tax.setIncludedInVatBase(includedInVatBase);
        tax.setActiveByDefault(activeByDefault);
        tax.setActive(active);
        tax.setSystem(true);
        return tax;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /** A surcharge is the only kind that carries a rate and can sit in the VAT base. */
    private void normalise(Tax tax) {
        if (tax.getKind() == TaxKind.FIXED_PER_DOCUMENT) {
            tax.setRate(null);
        } else {
            tax.setAmount(null);
        }
        if (tax.getKind() != TaxKind.PERCENTAGE_SURCHARGE) {
            tax.setIncludedInVatBase(false);
        }
    }

    private void validateAmounts(Tax tax) {
        if (tax.getKind() == TaxKind.FIXED_PER_DOCUMENT) {
            if (tax.getAmount() == null) {
                throw new BusinessRuleException("A fixed tax needs an amount");
            }
        } else if (tax.getRate() == null) {
            throw new BusinessRuleException("A VAT rate or a surcharge needs a percentage");
        }
    }

    private Tax getTax(Long id) {
        return taxRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Tax", id));
    }

    private void checkNameIsFree(String name, Long excludedId) {
        Long companyId = currentUser.companyId();
        boolean taken = excludedId == null
                ? taxRepository.existsByCompanyIdAndNameIgnoreCase(companyId, name)
                : taxRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(companyId, name, excludedId);
        if (taken) {
            throw new DuplicateResourceException("A tax with this name already exists");
        }
    }
}
