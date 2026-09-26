package com.sales.smartBusiness.supplierpayment;

import com.sales.smartBusiness.payment.PaymentMethod;
import com.sales.smartBusiness.payment.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class SupplierPaymentResponse {

    private Long id;
    private Long invoiceId;
    private String invoiceReference;
    private String supplierName;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private PaymentMethod method;
    private String reference;
    private String notes;
    private PaymentStatus status;
    private LocalDateTime createdAt;
}
