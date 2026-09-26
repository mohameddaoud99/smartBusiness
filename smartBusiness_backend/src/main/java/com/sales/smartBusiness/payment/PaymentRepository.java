package com.sales.smartBusiness.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
            SELECT p FROM Payment p JOIN FETCH p.invoice i JOIN FETCH i.customer
            WHERE p.id = :id AND p.company.id = :companyId
            """)
    Optional<Payment> findByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    /** What has really been paid on an invoice: cancelled payments do not count. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
            WHERE p.company.id = :companyId AND p.invoice.id = :invoiceId AND p.status = :status
            """)
    BigDecimal sumByInvoice(@Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId,
                            @Param("status") PaymentStatus status);

    /** The invoice id may be left null: Hibernate binds an id with a known type. */
    @Query(value = """
            SELECT p FROM Payment p JOIN FETCH p.invoice i JOIN FETCH i.customer
            WHERE p.company.id = :companyId AND (:invoiceId IS NULL OR i.id = :invoiceId)
            """,
            countQuery = """
            SELECT COUNT(p) FROM Payment p
            WHERE p.company.id = :companyId AND (:invoiceId IS NULL OR p.invoice.id = :invoiceId)
            """)
    Page<Payment> search(@Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId, Pageable pageable);
}
