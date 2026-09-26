package com.sales.smartBusiness.supplier;

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
class SupplierServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private SupplierRepository supplierRepository;
    @Mock private CompanyService companyService;
    @Mock private NumberingService numberingService;
    @Mock private SupplierMapper supplierMapper;
    @Mock private CurrentUser currentUser;

    @InjectMocks private SupplierService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(supplierMapper.toResponse(any())).thenReturn(new SupplierResponse());
    }

    private SupplierRequest request() {
        SupplierRequest request = new SupplierRequest();
        request.setType(SupplierType.COMPANY);
        request.setName("HAMMAMET SUD");
        return request;
    }

    @Test
    @DisplayName("a blank reference comes from the SUPPLIER numbering sequence")
    void generatesReferenceWhenBlank() {
        Supplier mapped = new Supplier();
        when(supplierMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(numberingService.allocate(DocumentType.SUPPLIER)).thenReturn("F-0005");
        when(supplierRepository.save(mapped)).thenReturn(mapped);

        service.create(request());

        assertThat(mapped.getReference()).isEqualTo("F-0005");
        assertThat(mapped.getCompany()).isNotNull();
    }

    @Test
    @DisplayName("a generated reference skips one somebody already typed by hand")
    void skipsTakenGeneratedReference() {
        Supplier mapped = new Supplier();
        when(supplierMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(numberingService.allocate(DocumentType.SUPPLIER)).thenReturn("F-0001", "F-0002");
        when(supplierRepository.existsByCompanyIdAndReferenceIgnoreCase(COMPANY_ID, "F-0001")).thenReturn(true);
        when(supplierRepository.save(mapped)).thenReturn(mapped);

        service.create(request());

        assertThat(mapped.getReference()).isEqualTo("F-0002");
    }

    @Test
    @DisplayName("an explicit reference already in use is refused")
    void refusesDuplicateExplicitReference() {
        SupplierRequest request = request();
        request.setReference("VIP-1");
        when(supplierMapper.toEntity(any())).thenReturn(new Supplier());
        when(supplierRepository.existsByCompanyIdAndReferenceIgnoreCase(COMPANY_ID, "VIP-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("reference");

        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("a supplier of another company is not found")
    void supplierOfAnotherCompanyIsNotFound() {
        when(supplierRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updating without touching the reference leaves it unchanged")
    void keepsReferenceWhenNotSent() {
        Supplier existing = new Supplier();
        existing.setReference("F-0009");
        when(supplierRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        service.update(1L, request());

        assertThat(existing.getReference()).isEqualTo("F-0009");
        verify(supplierRepository, never()).existsByCompanyIdAndReferenceIgnoreCaseAndIdNot(any(), any(), any());
    }

    @Test
    @DisplayName("an address left entirely blank is stored as no address")
    void blankAddressBecomesNull() {
        Supplier mapped = new Supplier();
        mapped.setBillingAddress(new Address());
        mapped.setShippingAddress(new Address());
        when(supplierMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(numberingService.allocate(DocumentType.SUPPLIER)).thenReturn("F-0001");
        when(supplierRepository.save(mapped)).thenReturn(mapped);

        service.create(request());

        assertThat(mapped.getBillingAddress()).isNull();
        assertThat(mapped.getShippingAddress()).isNull();
    }

    @Test
    @DisplayName("search turns a blank term into a match-all pattern")
    void searchNormalisesBlankTerm() {
        when(supplierRepository.search(any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.search("  ", null, org.springframework.data.domain.Pageable.unpaged());

        verify(supplierRepository).search(eq(COMPANY_ID), eq("%"), isNull(), any());
    }
}
