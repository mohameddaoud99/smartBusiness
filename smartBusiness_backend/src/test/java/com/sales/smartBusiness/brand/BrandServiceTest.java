package com.sales.smartBusiness.brand;

import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private BrandRepository brandRepository;
    @Mock private CompanyService companyService;
    @Mock private BrandMapper brandMapper;
    @Mock private CurrentUser currentUser;

    @InjectMocks private BrandService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(brandMapper.toResponse(any())).thenReturn(new BrandResponse());
    }

    private BrandRequest request(String name) {
        BrandRequest request = new BrandRequest();
        request.setName(name);
        return request;
    }

    @Test
    @DisplayName("a new brand is attached to the caller's company")
    void createAttachesCompany() {
        Brand mapped = new Brand();
        when(brandMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(brandRepository.countProductsUsing(any())).thenReturn(0L);

        service.create(request("Samsung"));

        assertThat(mapped.getCompany()).isNotNull();
    }

    @Test
    @DisplayName("a name colliding with an existing brand is refused")
    void duplicateNameIsRefused() {
        when(brandRepository.existsByCompanyIdAndNameIgnoreCase(COMPANY_ID, "Samsung")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("Samsung")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(brandRepository, never()).save(any());
    }

    @Test
    @DisplayName("a brand of another company is not found")
    void brandOfAnotherCompanyIsNotFound() {
        when(brandRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a brand still used by products cannot be deleted")
    void brandInUseCannotBeDeleted() {
        Brand existing = new Brand();
        existing.setId(1L);
        when(brandRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(brandRepository.countProductsUsing(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 product(s)");

        verify(brandRepository, never()).delete(any());
    }

    @Test
    @DisplayName("an unused brand is deleted")
    void unusedBrandIsDeleted() {
        Brand existing = new Brand();
        existing.setId(1L);
        when(brandRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(brandRepository.countProductsUsing(1L)).thenReturn(0L);

        service.delete(1L);

        verify(brandRepository).delete(existing);
    }
}
