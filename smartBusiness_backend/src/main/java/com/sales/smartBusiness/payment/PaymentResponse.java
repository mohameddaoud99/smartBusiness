package com.sales.smartBusiness.payment;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class PaymentResponse {

    private Long id;
    private Long invoiceId;
    private String invoiceReference;
    private String customerName;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private PaymentMethod method;
    private String reference;
    private String notes;
    private PaymentStatus status;
    private LocalDateTime createdAt;
}
