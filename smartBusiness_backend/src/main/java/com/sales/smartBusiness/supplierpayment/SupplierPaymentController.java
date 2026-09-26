package com.sales.smartBusiness.supplierpayment;

import com.sales.smartBusiness.payment.PaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Supplier payments have no permission of their own: they belong to the purchase documents, so they follow the
 * purchase rights. No PUT and no DELETE: a payment is cancelled, never edited or erased.
 */
@RestController
@RequestMapping("/api/supplier-payments")
@RequiredArgsConstructor
public class SupplierPaymentController {

    private final SupplierPaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public Page<SupplierPaymentResponse> search(
            @RequestParam(required = false) Long invoiceId,
            @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.search(invoiceId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_UPDATE')")
    public SupplierPaymentResponse create(@Valid @RequestBody PaymentRequest request) {
        return paymentService.create(request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_CANCEL')")
    public SupplierPaymentResponse cancel(@PathVariable Long id) {
        return paymentService.cancel(id);
    }
}
