package com.sales.smartBusiness.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndCompanyId(Long id, Long companyId);

    /** The product row, locked until the end of the transaction: what serializes two movements on the same good. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.company.id = :companyId")
    Optional<Product> lockByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    boolean existsByCompanyIdAndReferenceIgnoreCase(Long companyId, String reference);

    boolean existsByCompanyIdAndReferenceIgnoreCaseAndIdNot(Long companyId, String reference, Long id);

    /** Resolves the products a document line asks for, scoped to the caller's company. */
    List<Product> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);

    /**
     * The stock screen: goods only, optionally just the ones that have fallen to their
     * minimum. A product's available quantity is the sum of its movements, the reserved
     * ones counting against it — the same figure StockService shows. The types are compared
     * as text so this feature does not import the stock one.
     */
    @Query(value = """
            SELECT p FROM Product p
            WHERE p.company.id = :companyId
              AND p.kind = com.sales.smartBusiness.product.ProductKind.GOOD
              AND LOWER(CONCAT(p.name, ' ', COALESCE(p.reference, ''), ' ',
                        COALESCE(p.barcode, ''))) LIKE :search
              AND (:lowOnly = false OR (p.minStock IS NOT NULL AND
                    (SELECT COALESCE(SUM(CASE WHEN CAST(m.type AS string) IN ('RESERVE', 'RELEASE')
                                              THEN -m.quantity ELSE m.quantity END), 0)
                     FROM StockMovement m WHERE m.product = p) <= p.minStock))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.company.id = :companyId
              AND p.kind = com.sales.smartBusiness.product.ProductKind.GOOD
              AND LOWER(CONCAT(p.name, ' ', COALESCE(p.reference, ''), ' ',
                        COALESCE(p.barcode, ''))) LIKE :search
              AND (:lowOnly = false OR (p.minStock IS NOT NULL AND
                    (SELECT COALESCE(SUM(CASE WHEN CAST(m.type AS string) IN ('RESERVE', 'RELEASE')
                                              THEN -m.quantity ELSE m.quantity END), 0)
                     FROM StockMovement m WHERE m.product = p) <= p.minStock))
            """)
    Page<Product> searchGoods(@Param("companyId") Long companyId,
                              @Param("search") String search,
                              @Param("lowOnly") boolean lowOnly,
                              Pageable pageable);

    /** Blocks deletion while a purchase document line still points here (same trick as the sales lines). */
    @Query("SELECT COUNT(l) FROM PurchaseDocumentLine l WHERE l.product.id = :productId")
    long countPurchaseDocumentLinesUsing(@Param("productId") Long productId);

    /** Blocks deletion while stock movements still point here (same trick as the sales lines). */
    @Query("SELECT COUNT(m) FROM StockMovement m WHERE m.product.id = :productId")
    long countStockMovementsUsing(@Param("productId") Long productId);

    /**
     * Blocks deletion while a sales document line still points here. Lives here rather
     * than in the sales repository so the product feature never depends on sales.
     */
    @Query("SELECT COUNT(l) FROM SalesDocumentLine l WHERE l.product.id = :productId")
    long countSalesDocumentLinesUsing(@Param("productId") Long productId);

    /**
     * List screen: free-text search plus optional kind and category filters, always
     * scoped to the caller's company.
     *
     * {@code search} is always a lower-case LIKE pattern ("%" when nothing was typed) —
     * PostgreSQL cannot infer the type of a NULL parameter inside LOWER(), so the
     * service normalises it rather than passing null here. {@code kind} and
     * {@code categoryId} are safe to leave nullable: Hibernate binds them with a known type.
     */
    @Query(value = """
            SELECT p FROM Product p
            WHERE p.company.id = :companyId
              AND LOWER(CONCAT(p.name, ' ', COALESCE(p.reference, ''), ' ',
                        COALESCE(p.barcode, ''))) LIKE :search
              AND (:kind IS NULL OR p.kind = :kind)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
            """,
            countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.company.id = :companyId
              AND LOWER(CONCAT(p.name, ' ', COALESCE(p.reference, ''), ' ',
                        COALESCE(p.barcode, ''))) LIKE :search
              AND (:kind IS NULL OR p.kind = :kind)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
            """)
    Page<Product> search(@Param("companyId") Long companyId,
                         @Param("search") String search,
                         @Param("kind") ProductKind kind,
                         @Param("categoryId") Long categoryId,
                         Pageable pageable);
}
