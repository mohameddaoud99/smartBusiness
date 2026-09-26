package com.sales.smartBusiness.purchase;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PurchaseDocumentResponse {

    private Long id;
    private PurchaseDocumentType type;
    private PurchaseDocumentStatus status;
    private String reference;
    private Long supplierId;
    private String supplierName;
    private Long warehouseId;
    private String warehouseName;
    private Long sourceId;
    private String sourceReference;
    /** What the source is - an invoice can come from a purchase order or a goods receipt. */
    private PurchaseDocumentType sourceType;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private BigDecimal total;
    /** Invoice only: what has been paid, and what is left. Zero on the other documents. */
    private BigDecimal paidAmount;
    /** Invoice only: what its credit notes take off. */
    private BigDecimal creditedAmount;
    private BigDecimal balance;
    private String notes;
    private String terms;
    private List<PurchaseDocumentLineResponse> lines = new ArrayList<>();
    private List<PurchaseDocumentTaxResponse> taxes = new ArrayList<>();

    /** The documents made from this one (the purchase order a purchase order became). */
    private List<PurchaseDocumentSummaryResponse> derived = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
