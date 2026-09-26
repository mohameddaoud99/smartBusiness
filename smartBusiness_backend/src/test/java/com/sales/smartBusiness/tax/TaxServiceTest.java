package com.sales.smartBusiness.tax;

import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private TaxRepository taxRepository;
    @Mock private CompanyService companyService;
    @Mock private TaxMapper taxMapper;
    @Mock private CurrentUser currentUser;
    @Mock private AuditService auditService;

    @InjectMocks private TaxService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(taxMapper.toResponse(any())).thenReturn(new TaxResponse());
    }

    private TaxRequest request(TaxKind kind) {
        TaxRequest request = new TaxRequest();
        request.setName("My tax");
        request.setKind(kind);
        return request;
    }

    private Tax entity(TaxKind kind, BigDecimal rate, BigDecimal amount) {
        Tax tax = new Tax();
        tax.setName("My tax");
        tax.setKind(kind);
        tax.setRate(rate);
        tax.setAmount(amount);
        return tax;
    }

    @Test
    @DisplayName("createDefaults seeds the six standard taxes, all marked system")
    void createDefaultsSeedsStandardSet() {
        service.createDefaults(new Company());

        ArgumentCaptor<List<Tax>> captor = ArgumentCaptor.forClass(List.class);
        verify(taxRepository).saveAll(captor.capture());

        List<Tax> seeded = captor.getValue();
        assertThat(seeded).hasSize(6).allMatch(Tax::isSystem);
        assertThat(seeded).extracting(Tax::getName)
                .contains("TVA 19%", "FODEC", "Timbre fiscal");
    }

    @Test
    @DisplayName("a VAT rate without a percentage is refused")
    void vatRateNeedsAPercentage() {
        when(taxMapper.toEntity(any())).thenReturn(entity(TaxKind.VAT_RATE, null, null));

        assertThatThrownBy(() -> service.create(request(TaxKind.VAT_RATE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("percentage");

        verify(taxRepository, never()).save(any());
    }

    @Test
    @DisplayName("a fixed tax without an amount is refused")
    void fixedTaxNeedsAnAmount() {
        when(taxMapper.toEntity(any())).thenReturn(entity(TaxKind.FIXED_PER_DOCUMENT, null, null));

        assertThatThrownBy(() -> service.create(request(TaxKind.FIXED_PER_DOCUMENT)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("a surcharge stored with an amount has it dropped and its rate kept")
    void normalisesAmountAwayForASurcharge() {
        Tax mapped = entity(TaxKind.PERCENTAGE_SURCHARGE, new BigDecimal("1.000"), new BigDecimal("5.000"));
        when(taxMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(taxRepository.save(mapped)).thenReturn(mapped);

        service.create(request(TaxKind.PERCENTAGE_SURCHARGE));

        assertThat(mapped.getAmount()).isNull();
        assertThat(mapped.getRate()).isEqualByComparingTo("1.000");
    }

    @Test
    @DisplayName("a standard tax cannot be deleted")
    void systemTaxCannotBeDeleted() {
        Tax systemTax = entity(TaxKind.VAT_RATE, new BigDecimal("19.000"), null);
        systemTax.setSystem(true);
        when(taxRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(systemTax));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("standard tax");

        verify(taxRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a tax's type cannot be changed on update")
    void kindIsImmutable() {
        Tax existing = entity(TaxKind.VAT_RATE, new BigDecimal("19.000"), null);
        when(taxRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(1L, request(TaxKind.FIXED_PER_DOCUMENT)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("type cannot be changed");
    }

    @Test
    @DisplayName("a tax of another company is not found")
    void taxOfAnotherCompanyIsNotFound() {
        when(taxRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
