package com.sales.smartBusiness.sales;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A document as a list row, or as a link from another document — no lines, no tax breakdown. */
@Getter
@Setter
public class SalesDocumentSummaryResponse {

    private Long id;
    private SalesDocumentType type;
    private SalesDocumentStatus status;
    private String reference;
    private Long customerId;
    private String customerName;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal total;
    private BigDecimal paidAmount;
    private BigDecimal creditedAmount;
    private BigDecimal balance;
    private LocalDateTime createdAt;
}
