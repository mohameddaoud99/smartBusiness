package com.sales.smartBusiness.warehouse;

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
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private WarehouseRepository warehouseRepository;
    @Mock private CompanyService companyService;
    @Mock private CurrentUser currentUser;
    @Spy private WarehouseMapper warehouseMapper = Mappers.getMapper(WarehouseMapper.class);

    @InjectMocks private WarehouseService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(companyService.currentReference()).thenReturn(new Company());
    }

    private Warehouse existing(boolean isDefault, boolean active) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(3L);
        warehouse.setName("Annex");
        warehouse.setDefaultWarehouse(isDefault);
        warehouse.setActive(active);
        lenient().when(warehouseRepository.findByIdAndCompanyId(3L, COMPANY_ID)).thenReturn(Optional.of(warehouse));
        return warehouse;
    }

    private WarehouseRequest request(String name, boolean active) {
        WarehouseRequest request = new WarehouseRequest();
        request.setName(name);
        request.setActive(active);
        return request;
    }

    @Test
    @DisplayName("a new warehouse belongs to the caller's company and is never the default")
    void createIsNeverDefault() {
        service.create(request("Annex", true));

        ArgumentCaptor<Warehouse> saved = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(saved.capture());
        assertThat(saved.getValue().isDefaultWarehouse()).isFalse();
        assertThat(saved.getValue().getCompany()).isNotNull();
    }

    @Test
    @DisplayName("a name already used, whatever its case, is refused")
    void duplicateName() {
        when(warehouseRepository.existsByCompanyIdAndNameIgnoreCase(COMPANY_ID, "annex")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("annex", true)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("an update cannot turn a warehouse into the default one")
    void updateKeepsDefaultFlag() {
        Warehouse warehouse = existing(false, true);

        service.update(3L, request("Annex 2", true));

        assertThat(warehouse.getName()).isEqualTo("Annex 2");
        assertThat(warehouse.isDefaultWarehouse()).isFalse();
    }

    @Test
    @DisplayName("the default warehouse can be renamed but not deactivated")
    void defaultCannotBeDeactivated() {
        Warehouse warehouse = existing(true, true);

        service.update(3L, request("Main", true));
        assertThat(warehouse.getName()).isEqualTo("Main");

        assertThatThrownBy(() -> service.update(3L, request("Main", false)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("the default warehouse cannot be deleted")
    void defaultCannotBeDeleted() {
        existing(true, true);

        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(BusinessRuleException.class);
        verify(warehouseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a warehouse that has movements cannot be deleted")
    void warehouseWithMovementsCannotBeDeleted() {
        existing(false, true);
        when(warehouseRepository.countMovementsUsing(3L)).thenReturn(4L);

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("4 stock movement(s)");
        verify(warehouseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("an unused warehouse is deleted")
    void unusedWarehouseIsDeleted() {
        Warehouse warehouse = existing(false, true);

        service.delete(3L);

        verify(warehouseRepository).delete(warehouse);
    }

    @Test
    @DisplayName("an unknown or foreign warehouse is a 404")
    void unknownIs404() {
        when(warehouseRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a warehouse offered to a stock movement must exist and be active")
    void assignableMustBeActive() {
        existing(false, false);
        when(warehouseRepository.findByIdAndCompanyId(98L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAssignable(3L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not active");
        assertThatThrownBy(() -> service.getAssignable(98L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a brand new company gets its default warehouse")
    void createDefaults() {
        Company company = new Company();

        service.createDefaults(company);

        ArgumentCaptor<Warehouse> saved = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(saved.capture());
        assertThat(saved.getValue().isDefaultWarehouse()).isTrue();
        assertThat(saved.getValue().getCompany()).isSameAs(company);
    }

    @Test
    @DisplayName("a company without a default warehouse is a business error, not a null")
    void noDefault() {
        when(warehouseRepository.findByCompanyIdAndDefaultWarehouseTrue(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDefault()).isInstanceOf(BusinessRuleException.class);
    }
}
