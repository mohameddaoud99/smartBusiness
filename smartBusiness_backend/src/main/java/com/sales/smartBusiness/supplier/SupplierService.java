package com.sales.smartBusiness.supplier;

import com.sales.smartBusiness.common.SearchPattern;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.numbering.DocumentType;
import com.sales.smartBusiness.numbering.NumberingService;
import com.sales.smartBusiness.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final CompanyService companyService;
    private final NumberingService numberingService;
    private final SupplierMapper supplierMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public Page<SupplierResponse> search(String search, SupplierType type, Pageable pageable) {
        return supplierRepository.search(currentUser.companyId(), SearchPattern.like(search), type, pageable)
                .map(supplierMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse findById(Long id) {
        return supplierMapper.toResponse(getSupplier(id));
    }

    public SupplierResponse create(SupplierRequest request) {
        Supplier supplier = supplierMapper.toEntity(request);
        supplier.setCompany(companyService.currentReference());
        supplier.setReference(isBlank(request.getReference())
                ? nextFreeReference()
                : checkedReference(request.getReference(), null));
        dropEmptyAddresses(supplier);

        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = getSupplier(id);
        supplierMapper.updateEntity(request, supplier);
        dropEmptyAddresses(supplier);

        if (!isBlank(request.getReference())
                && !request.getReference().trim().equalsIgnoreCase(supplier.getReference())) {
            supplier.setReference(checkedReference(request.getReference(), id));
        }

        return supplierMapper.toResponse(supplier);
    }

    public void delete(Long id) {
        Supplier supplier = getSupplier(id);

        long documents = supplierRepository.countPurchaseDocumentsUsing(id);
        if (documents > 0) {
            throw new BusinessRuleException(
                    "This supplier appears on " + documents + " purchase document(s) and cannot be deleted.");
        }
        supplierRepository.delete(supplier);
    }

    /**
     * A supplier another record wants to point at (a purchase document). 422 rather than 404:
     * the supplier is a value in the caller's form, not the resource of the request.
     */
    @Transactional(readOnly = true)
    public Supplier getAssignable(Long id) {
        return supplierRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new BusinessRuleException("The selected supplier does not exist"));
    }

    private Supplier getSupplier(Long id) {
        return supplierRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Supplier", id));
    }

    /** An address the caller left blank is stored as no address, not a row of empty strings. */
    private void dropEmptyAddresses(Supplier supplier) {
        if (supplier.getBillingAddress() != null && supplier.getBillingAddress().isEmpty()) {
            supplier.setBillingAddress(null);
        }
        if (supplier.getShippingAddress() != null && supplier.getShippingAddress().isEmpty()) {
            supplier.setShippingAddress(null);
        }
    }

    /** A code the caller typed: kept as is, provided no other supplier of the company uses it. */
    private String checkedReference(String requested, Long excludedId) {
        String reference = requested.trim();
        Long companyId = currentUser.companyId();
        boolean taken = excludedId == null
                ? supplierRepository.existsByCompanyIdAndReferenceIgnoreCase(companyId, reference)
                : supplierRepository.existsByCompanyIdAndReferenceIgnoreCaseAndIdNot(companyId, reference, excludedId);
        if (taken) {
            throw new DuplicateResourceException("A supplier with this reference already exists");
        }
        return reference;
    }

    /**
     * The next code from the SUPPLIER numbering settings. The sequence row is locked, so
     * two concurrent creations never get the same code; a code someone already typed by
     * hand is skipped.
     */
    private String nextFreeReference() {
        String candidate;
        do {
            candidate = numberingService.allocate(DocumentType.SUPPLIER);
        } while (supplierRepository.existsByCompanyIdAndReferenceIgnoreCase(currentUser.companyId(), candidate));
        return candidate;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
