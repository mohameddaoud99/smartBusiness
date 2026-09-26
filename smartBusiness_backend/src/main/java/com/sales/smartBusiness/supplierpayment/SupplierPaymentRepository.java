package com.sales.smartBusiness.supplierpayment;

import com.sales.smartBusiness.payment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    @Query("""
            SELECT p FROM SupplierPayment p JOIN FETCH p.invoice i JOIN FETCH i.supplier
            WHERE p.id = :id AND p.company.id = :companyId
            """)
    Optional<SupplierPayment> findByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    /** What has really been paid on an invoice: cancelled payments do not count. */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) FROM SupplierPayment p
            WHERE p.company.id = :companyId AND p.invoice.id = :invoiceId AND p.status = :status
            """)
    BigDecimal sumByInvoice(@Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId,
                            @Param("status") PaymentStatus status);

    /** The invoice id may be left null: Hibernate binds an id with a known type. */
    @Query(value = """
            SELECT p FROM SupplierPayment p JOIN FETCH p.invoice i JOIN FETCH i.supplier
            WHERE p.company.id = :companyId AND (:invoiceId IS NULL OR i.id = :invoiceId)
            """,
            countQuery = """
            SELECT COUNT(p) FROM SupplierPayment p
            WHERE p.company.id = :companyId AND (:invoiceId IS NULL OR p.invoice.id = :invoiceId)
            """)
    Page<SupplierPayment> search(@Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId,
                                 Pageable pageable);
}
