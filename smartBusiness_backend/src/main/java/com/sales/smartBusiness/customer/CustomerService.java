package com.sales.smartBusiness.customer;

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
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CompanyService companyService;
    private final NumberingService numberingService;
    private final CustomerMapper customerMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String search, CustomerType type, Pageable pageable) {
        return customerRepository.search(currentUser.companyId(), SearchPattern.like(search), type, pageable)
                .map(customerMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return customerMapper.toResponse(getCustomer(id));
    }

    public CustomerResponse create(CustomerRequest request) {
        Customer customer = customerMapper.toEntity(request);
        customer.setCompany(companyService.currentReference());
        customer.setReference(isBlank(request.getReference())
                ? nextFreeReference()
                : checkedReference(request.getReference(), null));
        dropEmptyAddresses(customer);

        return customerMapper.toResponse(customerRepository.save(customer));
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = getCustomer(id);
        customerMapper.updateEntity(request, customer);
        dropEmptyAddresses(customer);

        if (!isBlank(request.getReference())
                && !request.getReference().trim().equalsIgnoreCase(customer.getReference())) {
            customer.setReference(checkedReference(request.getReference(), id));
        }

        return customerMapper.toResponse(customer);
    }

    public void delete(Long id) {
        Customer customer = getCustomer(id);

        long documents = customerRepository.countSalesDocumentsUsing(id);
        if (documents > 0) {
            throw new BusinessRuleException(
                    "This customer appears on " + documents + " sales document(s) and cannot be deleted.");
        }
        customerRepository.delete(customer);
    }

    /**
     * A customer another record wants to point at (a sales document's buyer). 422 rather
     * than 404: the customer is a value in the caller's form, not the resource of the request.
     */
    @Transactional(readOnly = true)
    public Customer getAssignable(Long id) {
        return customerRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new BusinessRuleException("The selected customer does not exist"));
    }

    private Customer getCustomer(Long id) {
        return customerRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
    }

    /** An address the caller left blank is stored as no address, not a row of empty strings. */
    private void dropEmptyAddresses(Customer customer) {
        if (customer.getBillingAddress() != null && customer.getBillingAddress().isEmpty()) {
            customer.setBillingAddress(null);
        }
        if (customer.getShippingAddress() != null && customer.getShippingAddress().isEmpty()) {
            customer.setShippingAddress(null);
        }
    }

    /** A code the caller typed: kept as is, provided no other customer of the company uses it. */
    private String checkedReference(String requested, Long excludedId) {
        String reference = requested.trim();
        Long companyId = currentUser.companyId();
        boolean taken = excludedId == null
                ? customerRepository.existsByCompanyIdAndReferenceIgnoreCase(companyId, reference)
                : customerRepository.existsByCompanyIdAndReferenceIgnoreCaseAndIdNot(companyId, reference, excludedId);
        if (taken) {
            throw new DuplicateResourceException("A customer with this reference already exists");
        }
        return reference;
    }

    /**
     * The next code from the CUSTOMER numbering settings. The sequence row is locked, so
     * two concurrent creations never get the same code; a code someone already typed by
     * hand is skipped.
     */
    private String nextFreeReference() {
        String candidate;
        do {
            candidate = numberingService.allocate(DocumentType.CUSTOMER);
        } while (customerRepository.existsByCompanyIdAndReferenceIgnoreCase(currentUser.companyId(), candidate));
        return candidate;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
