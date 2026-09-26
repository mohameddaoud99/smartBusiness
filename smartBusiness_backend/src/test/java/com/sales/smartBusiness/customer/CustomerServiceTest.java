package com.sales.smartBusiness.customer;

import com.sales.smartBusiness.common.Address;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.numbering.DocumentType;
import com.sales.smartBusiness.numbering.NumberingService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private CustomerRepository customerRepository;
    @Mock private CompanyService companyService;
    @Mock private NumberingService numberingService;
    @Mock private CustomerMapper customerMapper;
    @Mock private CurrentUser currentUser;

    @InjectMocks private CustomerService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(customerMapper.toResponse(any())).thenReturn(new CustomerResponse());
    }

    private CustomerRequest request() {
        CustomerRequest request = new CustomerRequest();
        request.setType(CustomerType.COMPANY);
        request.setName("Société Alpha");
        return request;
    }

    @Test
    @DisplayName("a blank reference comes from the CUSTOMER numbering sequence")
    void generatesReferenceWhenBlank() {
        Customer mapped = new Customer();
        when(customerMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(numberingService.allocate(DocumentType.CUSTOMER)).thenReturn("C-0005");
        when(customerRepository.save(mapped)).thenReturn(mapped);

        service.create(request());

        assertThat(mapped.getReference()).isEqualTo("C-0005");
        assertThat(mapped.getCompany()).isNotNull();
    }

    @Test
    @DisplayName("a generated reference skips one somebody already typed by hand")
    void skipsTakenGeneratedReference() {
        Customer mapped = new Customer();
        when(customerMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(numberingService.allocate(DocumentType.CUSTOMER)).thenReturn("C-0001", "C-0002");
        when(customerRepository.existsByCompanyIdAndReferenceIgnoreCase(COMPANY_ID, "C-0001")).thenReturn(true);
        when(customerRepository.save(mapped)).thenReturn(mapped);

        service.create(request());

        assertThat(mapped.getReference()).isEqualTo("C-0002");
    }

    @Test
    @DisplayName("an explicit reference already in use is refused")
    void refusesDuplicateExplicitReference() {
        CustomerRequest request = request();
        request.setReference("VIP-1");
        when(customerMapper.toEntity(any())).thenReturn(new Customer());
        when(customerRepository.existsByCompanyIdAndReferenceIgnoreCase(COMPANY_ID, "VIP-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("reference");

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("a customer of another company is not found")
    void customerOfAnotherCompanyIsNotFound() {
        when(customerRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updating without touching the reference leaves it unchanged")
    void keepsReferenceWhenNotSent() {
        Customer existing = new Customer();
        existing.setReference("C-0009");
        when(customerRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        service.update(1L, request());

        assertThat(existing.getReference()).isEqualTo("C-0009");
        verify(customerRepository, never()).existsByCompanyIdAndReferenceIgnoreCaseAndIdNot(any(), any(), any());
    }

    @Test
    @DisplayName("an address left entirely blank is stored as no address")
    void blankAddressBecomesNull() {
        Customer mapped = new Customer();
        mapped.setBillingAddress(new Address());
        mapped.setShippingAddress(new Address());
        when(customerMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(numberingService.allocate(DocumentType.CUSTOMER)).thenReturn("C-0001");
        when(customerRepository.save(mapped)).thenReturn(mapped);

        service.create(request());

        assertThat(mapped.getBillingAddress()).isNull();
        assertThat(mapped.getShippingAddress()).isNull();
    }

    @Test
    @DisplayName("search turns a blank term into a match-all pattern")
    void searchNormalisesBlankTerm() {
        when(customerRepository.search(any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.search("  ", null, org.springframework.data.domain.Pageable.unpaged());

        verify(customerRepository).search(eq(COMPANY_ID), eq("%"), isNull(), any());
    }
}
