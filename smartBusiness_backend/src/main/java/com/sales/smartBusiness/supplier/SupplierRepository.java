package com.sales.smartBusiness.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndReferenceIgnoreCase(Long companyId, String reference);

    boolean existsByCompanyIdAndReferenceIgnoreCaseAndIdNot(Long companyId, String reference, Long id);

    /**
     * Blocks deletion while a purchase document still points here. Lives here rather than in
     * the purchase repository so the supplier feature never depends on the purchase feature.
     */
    @Query("SELECT COUNT(d) FROM PurchaseDocument d WHERE d.supplier.id = :supplierId")
    long countPurchaseDocumentsUsing(@Param("supplierId") Long supplierId);

    /**
     * List screen: free-text search plus an optional type filter, always scoped to the
     * caller's company.
     *
     * {@code search} is always a lower-case LIKE pattern ("%" when nothing was typed) —
     * PostgreSQL cannot infer the type of a NULL parameter inside LOWER(), so the
     * service normalises it rather than passing null here. The {@code type} enum is
     * safe to leave nullable: Hibernate binds it with a known type.
     */
    @Query(value = """
            SELECT s FROM Supplier s
            WHERE s.company.id = :companyId
              AND LOWER(CONCAT(s.name, ' ', COALESCE(s.reference, ''), ' ',
                        COALESCE(s.email, ''), ' ', COALESCE(s.contactName, ''))) LIKE :search
              AND (:type IS NULL OR s.type = :type)
            """,
            countQuery = """
            SELECT COUNT(s) FROM Supplier s
            WHERE s.company.id = :companyId
              AND LOWER(CONCAT(s.name, ' ', COALESCE(s.reference, ''), ' ',
                        COALESCE(s.email, ''), ' ', COALESCE(s.contactName, ''))) LIKE :search
              AND (:type IS NULL OR s.type = :type)
            """)
    Page<Supplier> search(@Param("companyId") Long companyId,
                          @Param("search") String search,
                          @Param("type") SupplierType type,
                          Pageable pageable);
}
