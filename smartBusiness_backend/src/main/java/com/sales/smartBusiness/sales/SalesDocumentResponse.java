package com.sales.smartBusiness.sales;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SalesDocumentResponse {

    private Long id;
    private SalesDocumentType type;
    private SalesDocumentStatus status;
    private String reference;
    private Long customerId;
    private String customerName;
    private Long warehouseId;
    private String warehouseName;
    private Long sourceId;
    /** What the source is - an invoice can come from a quote, an order or a delivery note. */
    private SalesDocumentType sourceType;
    private String sourceReference;
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
    private List<SalesDocumentLineResponse> lines = new ArrayList<>();
    private List<SalesDocumentTaxResponse> taxes = new ArrayList<>();

    /** The documents made from this one (the sales order a quote became). */
    private List<SalesDocumentSummaryResponse> derived = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
