package com.sales.smartBusiness.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByIdAndCompanyId(Long id, Long companyId);

    /** The default warehouse first, then alphabetical. */
    List<Warehouse> findByCompanyIdOrderByDefaultWarehouseDescNameAsc(Long companyId);

    Optional<Warehouse> findByCompanyIdAndDefaultWarehouseTrue(Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    /**
     * Blocks deletion while a movement still points here. Lives here rather than in the
     * stock repository so the warehouse feature never depends on the stock feature
     * (stock depends on warehouses, not the other way round).
     */
    @Query("SELECT COUNT(m) FROM StockMovement m WHERE m.warehouse.id = :warehouseId")
    long countMovementsUsing(@Param("warehouseId") Long warehouseId);
}
