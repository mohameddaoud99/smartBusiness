package com.sales.smartBusiness.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class PurchaseDocumentRequest {

    @NotNull(message = "Type is required")
    private PurchaseDocumentType type;

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    /** Where a goods receipt or an invoice puts the stock. Required for those, ignored for an order. */
    private Long warehouseId;

    @NotNull(message = "Issue date is required")
    private LocalDate issueDate;

    private LocalDate dueDate;

    @Size(max = 4000, message = "Notes must not exceed 4000 characters")
    private String notes;

    @Size(max = 4000, message = "Terms must not exceed 4000 characters")
    private String terms;

    /** The surcharges and flat charges (FODEC, stamp duty...) put on the whole document. */
    private Set<Long> taxIds;

    @NotEmpty(message = "A document needs at least one line")
    @Size(max = 200, message = "A document cannot have more than 200 lines")
    @Valid
    private List<PurchaseDocumentLineRequest> lines;
}
