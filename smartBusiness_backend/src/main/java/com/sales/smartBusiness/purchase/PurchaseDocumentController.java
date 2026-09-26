package com.sales.smartBusiness.purchase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-documents")
@RequiredArgsConstructor
public class PurchaseDocumentController {

    private final PurchaseDocumentService purchaseDocumentService;

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public Page<PurchaseDocumentSummaryResponse> search(
            @RequestParam PurchaseDocumentType type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PurchaseDocumentStatus status,
            @RequestParam(required = false) Long supplierId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return purchaseDocumentService.search(type, search, status, supplierId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public PurchaseDocumentResponse findById(@PathVariable Long id) {
        return purchaseDocumentService.findById(id);
    }

    /** The totals a document would have, computed without saving — for the live editor. */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public PurchaseDocumentResponse preview(@Valid @RequestBody PurchaseDocumentRequest request) {
        return purchaseDocumentService.preview(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseDocumentResponse create(@Valid @RequestBody PurchaseDocumentRequest request) {
        return purchaseDocumentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_UPDATE')")
    public PurchaseDocumentResponse update(@PathVariable Long id, @Valid @RequestBody PurchaseDocumentRequest request) {
        return purchaseDocumentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PURCHASE_UPDATE')")
    public void delete(@PathVariable Long id) {
        purchaseDocumentService.delete(id);
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('PURCHASE_UPDATE')")
    public PurchaseDocumentResponse validate(@PathVariable Long id) {
        return purchaseDocumentService.validate(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_CANCEL')")
    public PurchaseDocumentResponse cancel(@PathVariable Long id) {
        return purchaseDocumentService.cancel(id);
    }

    /** A draft invoice from a validated purchase order or goods receipt. */
    @PostMapping("/{id}/convert-to-invoice")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseDocumentResponse convertToInvoice(@PathVariable Long id) {
        return purchaseDocumentService.convertToInvoice(id);
    }

    /** A draft return note from a validated goods receipt or invoice, to lower to what really goes back. */
    @PostMapping("/{id}/convert-to-return-note")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseDocumentResponse convertToReturnNote(@PathVariable Long id) {
        return purchaseDocumentService.convertToReturnNote(id);
    }

    /** A draft supplier credit note from a validated invoice, to adjust to what is really credited. */
    @PostMapping("/{id}/convert-to-credit-note")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseDocumentResponse convertToCreditNote(@PathVariable Long id) {
        return purchaseDocumentService.convertToCreditNote(id);
    }

    @PostMapping("/{id}/convert-to-receipt")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseDocumentResponse convertToReceipt(@PathVariable Long id) {
        return purchaseDocumentService.convertToReceipt(id);
    }
}
