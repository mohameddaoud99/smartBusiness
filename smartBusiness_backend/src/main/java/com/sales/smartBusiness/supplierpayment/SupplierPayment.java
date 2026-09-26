package com.sales.smartBusiness.supplierpayment;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.payment.PaymentMethod;
import com.sales.smartBusiness.payment.PaymentStatus;
import com.sales.smartBusiness.purchase.PurchaseDocument;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money paid to a supplier against one purchase invoice - the mirror of a customer payment. Reachable only
 * from inside its own company. A register like the stock movements: rows are added, and cancelled, never edited.
 */
@Entity
@Table(name = "supplier_payments")
@Getter
@Setter
@NoArgsConstructor
public class SupplierPayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private PurchaseDocument invoice;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    /** What identifies it: a cheque number, a transfer reference. */
    @Column(length = 100)
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.ACTIVE;
}
