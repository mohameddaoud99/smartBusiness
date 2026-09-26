package com.sales.smartBusiness.purchase;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A document as a list row, or as a link from another document — no lines, no tax breakdown. */
@Getter
@Setter
public class PurchaseDocumentSummaryResponse {

    private Long id;
    private PurchaseDocumentType type;
    private PurchaseDocumentStatus status;
    private String reference;
    private Long supplierId;
    private String supplierName;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal total;
    private BigDecimal paidAmount;
    private BigDecimal creditedAmount;
    private BigDecimal balance;
    private LocalDateTime createdAt;
}
