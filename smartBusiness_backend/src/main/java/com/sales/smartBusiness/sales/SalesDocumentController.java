package com.sales.smartBusiness.sales;

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
@RequestMapping("/api/sales-documents")
@RequiredArgsConstructor
public class SalesDocumentController {

    private final SalesDocumentService salesDocumentService;

    @GetMapping
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public Page<SalesDocumentSummaryResponse> search(
            @RequestParam SalesDocumentType type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SalesDocumentStatus status,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return salesDocumentService.search(type, search, status, customerId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public SalesDocumentResponse findById(@PathVariable Long id) {
        return salesDocumentService.findById(id);
    }

    /** The totals a document would have, computed without saving — for the live editor. */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public SalesDocumentResponse preview(@Valid @RequestBody SalesDocumentRequest request) {
        return salesDocumentService.preview(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public SalesDocumentResponse create(@Valid @RequestBody SalesDocumentRequest request) {
        return salesDocumentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SALE_UPDATE')")
    public SalesDocumentResponse update(@PathVariable Long id, @Valid @RequestBody SalesDocumentRequest request) {
        return salesDocumentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SALE_UPDATE')")
    public void delete(@PathVariable Long id) {
        salesDocumentService.delete(id);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('SALE_UPDATE')")
    public SalesDocumentResponse issue(@PathVariable Long id) {
        return salesDocumentService.issue(id);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SALE_UPDATE')")
    public SalesDocumentResponse changeStatus(@PathVariable Long id,
                                              @Valid @RequestBody SalesDocumentStatusRequest request) {
        return salesDocumentService.changeStatus(id, request.getStatus());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALE_CANCEL')")
    public SalesDocumentResponse cancel(@PathVariable Long id) {
        return salesDocumentService.cancel(id);
    }

    @PostMapping("/{id}/convert-to-delivery-note")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public SalesDocumentResponse convertToDeliveryNote(@PathVariable Long id) {
        return salesDocumentService.convertToDeliveryNote(id);
    }

    /** A draft invoice from an issued or accepted quote, a confirmed sales order or a delivered note. */
    @PostMapping("/{id}/convert-to-invoice")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public SalesDocumentResponse convertToInvoice(@PathVariable Long id) {
        return salesDocumentService.convertToInvoice(id);
    }

    /** A draft return note from a delivered delivery note or an issued invoice, to lower to what really comes back. */
    @PostMapping("/{id}/convert-to-return-note")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public SalesDocumentResponse convertToReturnNote(@PathVariable Long id) {
        return salesDocumentService.convertToReturnNote(id);
    }

    /** A draft credit note from an issued invoice, to adjust to what is really credited. */
    @PostMapping("/{id}/convert-to-credit-note")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public SalesDocumentResponse convertToCreditNote(@PathVariable Long id) {
        return salesDocumentService.convertToCreditNote(id);
    }

    @PostMapping("/{id}/convert-to-order")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public SalesDocumentResponse convertToOrder(@PathVariable Long id) {
        return salesDocumentService.convertToOrder(id);
    }
}
