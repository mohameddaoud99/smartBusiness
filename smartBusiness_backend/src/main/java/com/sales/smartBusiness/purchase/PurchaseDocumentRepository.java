package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.common.AmountSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PurchaseDocumentRepository extends JpaRepository<PurchaseDocument, Long> {

    Optional<PurchaseDocument> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndTypeAndReferenceIgnoreCase(Long companyId, PurchaseDocumentType type, String reference);

    List<PurchaseDocument> findBySourceIdAndCompanyIdOrderByIdAsc(Long sourceId, Long companyId);

    // ----- Quantities followed line by line -----

    /** The source document's row, locked until the end of the transaction: two drafts made for one remainder are checked one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d.id FROM PurchaseDocument d WHERE d.id = :id AND d.company.id = :companyId")
    Optional<Long> lockById(@Param("id") Long id, @Param("companyId") Long companyId);

    /**
     * For each source line, how much the documents of a type and status have taken of it (one document left out - the
     * one being checked). Rows are (source line id, quantity).
     */
    @Query("""
            SELECT l.sourceLine.id, SUM(l.quantity) FROM PurchaseDocumentLine l
            WHERE l.document.company.id = :companyId AND l.sourceLine.id IN :sourceLineIds
              AND l.document.type = :type AND l.document.status IN :statuses AND l.document.id <> :excludedId
            GROUP BY l.sourceLine.id
            """)
    List<Object[]> quantitiesTaken(@Param("companyId") Long companyId, @Param("sourceLineIds") Collection<Long> sourceLineIds,
                                   @Param("type") PurchaseDocumentType type,
                                   @Param("statuses") Collection<PurchaseDocumentStatus> statuses,
                                   @Param("excludedId") Long excludedId);

    // ----- Dashboard -----

    /** The total of the documents of a type and status issued between two dates - the building block of the figures. */
    @Query("""
            SELECT COALESCE(SUM(d.total), 0) FROM PurchaseDocument d
            WHERE d.company.id = :companyId AND d.type = :type AND d.status IN :statuses
              AND d.issueDate BETWEEN :from AND :to
            """)
    BigDecimal sumTotal(@Param("companyId") Long companyId, @Param("type") PurchaseDocumentType type,
                        @Param("statuses") Collection<PurchaseDocumentStatus> statuses,
                        @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** How many invoices still have something to pay, and how much (total - credited - paid). */
    @Query("""
            SELECT new com.sales.smartBusiness.common.AmountSummary(COUNT(d),
                   COALESCE(SUM(d.total - d.creditedAmount - d.paidAmount), 0))
            FROM PurchaseDocument d
            WHERE d.company.id = :companyId AND d.type = :type AND d.status IN :statuses
            """)
    AmountSummary unpaid(@Param("companyId") Long companyId, @Param("type") PurchaseDocumentType type,
                         @Param("statuses") Collection<PurchaseDocumentStatus> statuses);

    /** The same, for the ones whose due date has passed. */
    @Query("""
            SELECT new com.sales.smartBusiness.common.AmountSummary(COUNT(d),
                   COALESCE(SUM(d.total - d.creditedAmount - d.paidAmount), 0))
            FROM PurchaseDocument d
            WHERE d.company.id = :companyId AND d.type = :type AND d.status IN :statuses AND d.dueDate < :today
            """)
    AmountSummary overdue(@Param("companyId") Long companyId, @Param("type") PurchaseDocumentType type,
                          @Param("statuses") Collection<PurchaseDocumentStatus> statuses, @Param("today") LocalDate today);

    /** The oldest late invoices first: the ones to pay. */
    @Query("""
            SELECT d FROM PurchaseDocument d JOIN FETCH d.supplier
            WHERE d.company.id = :companyId AND d.type = :type AND d.status IN :statuses AND d.dueDate < :today
            ORDER BY d.dueDate ASC, d.id ASC
            """)
    List<PurchaseDocument> overdueInvoices(@Param("companyId") Long companyId, @Param("type") PurchaseDocumentType type,
                                           @Param("statuses") Collection<PurchaseDocumentStatus> statuses,
                                           @Param("today") LocalDate today, Pageable pageable);

    /** What the live credit notes of an invoice take off it: the VALIDATED ones, cancelled ones do not count. */
    @Query("""
            SELECT COALESCE(SUM(d.total), 0) FROM PurchaseDocument d
            WHERE d.company.id = :companyId AND d.source.id = :invoiceId
              AND d.type = :type AND d.status = :status
            """)
    BigDecimal sumByInvoice(@Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId,
                            @Param("type") PurchaseDocumentType type, @Param("status") PurchaseDocumentStatus status);

    /**
     * List screen: free-text search on number and supplier, plus optional status and
     * supplier filters, always scoped to the caller's company and one document type.
     *
     * {@code search} is always a lower-case LIKE pattern ("%" when nothing was typed) —
     * PostgreSQL cannot infer the type of a NULL parameter inside LOWER(). The status and
     * supplier id are safe to leave nullable: Hibernate binds them with a known type.
     */
    @Query(value = """
            SELECT d FROM PurchaseDocument d JOIN FETCH d.supplier c
            WHERE d.company.id = :companyId
              AND d.type = :type
              AND LOWER(CONCAT(COALESCE(d.reference, ''), ' ', c.name)) LIKE :search
              AND (:status IS NULL OR d.status = :status)
              AND (:supplierId IS NULL OR c.id = :supplierId)
            """,
            countQuery = """
            SELECT COUNT(d) FROM PurchaseDocument d JOIN d.supplier c
            WHERE d.company.id = :companyId
              AND d.type = :type
              AND LOWER(CONCAT(COALESCE(d.reference, ''), ' ', c.name)) LIKE :search
              AND (:status IS NULL OR d.status = :status)
              AND (:supplierId IS NULL OR c.id = :supplierId)
            """)
    Page<PurchaseDocument> search(@Param("companyId") Long companyId,
                               @Param("type") PurchaseDocumentType type,
                               @Param("search") String search,
                               @Param("status") PurchaseDocumentStatus status,
                               @Param("supplierId") Long supplierId,
                               Pageable pageable);
}
