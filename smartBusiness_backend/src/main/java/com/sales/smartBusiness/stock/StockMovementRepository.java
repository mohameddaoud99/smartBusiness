package com.sales.smartBusiness.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * The register, newest first by default. Company-scoped; the three filters are ids and
     * an enum, which Hibernate binds with a known type, so they may be null.
     */
    @Query(value = """
            SELECT m FROM StockMovement m JOIN FETCH m.product JOIN FETCH m.warehouse
            WHERE m.company.id = :companyId
              AND (:productId IS NULL OR m.product.id = :productId)
              AND (:warehouseId IS NULL OR m.warehouse.id = :warehouseId)
              AND (:type IS NULL OR m.type = :type)
            """,
            countQuery = """
            SELECT COUNT(m) FROM StockMovement m
            WHERE m.company.id = :companyId
              AND (:productId IS NULL OR m.product.id = :productId)
              AND (:warehouseId IS NULL OR m.warehouse.id = :warehouseId)
              AND (:type IS NULL OR m.type = :type)
            """)
    Page<StockMovement> search(@Param("companyId") Long companyId,
                               @Param("productId") Long productId,
                               @Param("warehouseId") Long warehouseId,
                               @Param("type") StockMovementType type,
                               Pageable pageable);

    /**
     * What the stock is worth: each physical movement (signed: an exit counts negative) at the purchase price of its
     * product, a product with no purchase price counting for nothing. Reservations are promises, not goods.
     */
    @Query("""
            SELECT COALESCE(SUM(m.quantity * COALESCE(m.product.purchasePrice, 0)), 0) FROM StockMovement m
            WHERE m.company.id = :companyId AND m.type IN :physicalTypes
            """)
    BigDecimal stockValue(@Param("companyId") Long companyId,
                          @Param("physicalTypes") Collection<StockMovementType> physicalTypes);

    /**
     * Sum of the movements per product and type — the building block of every level.
     * A null warehouse means all of them.
     */
    @Query("""
            SELECT m.product.id, m.type, SUM(m.quantity) FROM StockMovement m
            WHERE m.company.id = :companyId
              AND m.product.id IN :productIds
              AND (:warehouseId IS NULL OR m.warehouse.id = :warehouseId)
            GROUP BY m.product.id, m.type
            """)
    List<Object[]> sumByProductAndType(@Param("companyId") Long companyId,
                                       @Param("productIds") Collection<Long> productIds,
                                       @Param("warehouseId") Long warehouseId);

    /** What a document released out of ANOTHER document's reservation (a delivery note out of an order's). */
    List<StockMovement> findByCompanyIdAndSourceTypeAndOriginIdAndTypeIn(
            Long companyId, StockSource sourceType, Long originId, Collection<StockMovementType> types);

    /** The reservations and releases a document already caused — what a cancellation must undo. */
    List<StockMovement> findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(
            Long companyId, StockSource sourceType, Long sourceId, Collection<StockMovementType> types);
}
