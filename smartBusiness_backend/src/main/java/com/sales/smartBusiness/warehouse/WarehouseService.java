package com.sales.smartBusiness.warehouse;

import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final CompanyService companyService;
    private final WarehouseMapper warehouseMapper;
    private final CurrentUser currentUser;

    /** Not paginated: a company has a handful of warehouses. */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll() {
        return warehouseRepository.findByCompanyIdOrderByDefaultWarehouseDescNameAsc(currentUser.companyId())
                .stream()
                .map(warehouseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(Long id) {
        return warehouseMapper.toResponse(getWarehouse(id));
    }

    public WarehouseResponse create(WarehouseRequest request) {
        checkNameIsFree(request.getName(), null);

        Warehouse warehouse = warehouseMapper.toEntity(request);
        warehouse.setCompany(companyService.currentReference());
        warehouseRepository.save(warehouse);

        return warehouseMapper.toResponse(warehouse);
    }

    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = getWarehouse(id);
        checkNameIsFree(request.getName(), id);
        if (warehouse.isDefaultWarehouse() && !request.isActive()) {
            throw new BusinessRuleException("The default warehouse cannot be deactivated");
        }

        warehouseMapper.updateEntity(request, warehouse);
        return warehouseMapper.toResponse(warehouse);
    }

    public void delete(Long id) {
        Warehouse warehouse = getWarehouse(id);
        if (warehouse.isDefaultWarehouse()) {
            throw new BusinessRuleException("The default warehouse cannot be deleted");
        }
        long movements = warehouseRepository.countMovementsUsing(id);
        if (movements > 0) {
            throw new BusinessRuleException(
                    "This warehouse has " + movements + " stock movement(s) and cannot be deleted. "
                            + "Deactivate it instead.");
        }
        warehouseRepository.delete(warehouse);
    }

    /**
     * A warehouse a stock movement wants to point at. 422 rather than 404: it is a value in
     * the caller's form, not the resource of the request. An inactive one is refused too.
     */
    @Transactional(readOnly = true)
    public Warehouse getAssignable(Long id) {
        Warehouse warehouse = warehouseRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new BusinessRuleException("The selected warehouse does not exist"));
        if (!warehouse.isActive()) {
            throw new BusinessRuleException("The warehouse \"" + warehouse.getName() + "\" is not active");
        }
        return warehouse;
    }

    /** The warehouse documents use when they do not name one. */
    @Transactional(readOnly = true)
    public Warehouse getDefault() {
        return warehouseRepository.findByCompanyIdAndDefaultWarehouseTrue(currentUser.companyId())
                .orElseThrow(() -> new BusinessRuleException("The company has no default warehouse"));
    }

    /** The default warehouse of a brand new company. */
    public void createDefaults(Company company) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCompany(company);
        warehouse.setName("Default warehouse");
        warehouse.setDefaultWarehouse(true);
        warehouseRepository.save(warehouse);
    }

    private Warehouse getWarehouse(Long id) {
        return warehouseRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Warehouse", id));
    }

    private void checkNameIsFree(String name, Long excludedId) {
        Long companyId = currentUser.companyId();
        boolean taken = excludedId == null
                ? warehouseRepository.existsByCompanyIdAndNameIgnoreCase(companyId, name)
                : warehouseRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(companyId, name, excludedId);
        if (taken) {
            throw new DuplicateResourceException("A warehouse with this name already exists");
        }
    }
}
