package com.sales.smartBusiness.payment;

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
 * Payments have no permission of their own: they belong to the sales documents, so they follow the
 * sales rights. No PUT and no DELETE: a payment is cancelled, never edited or erased.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public Page<PaymentResponse> search(
            @RequestParam(required = false) Long invoiceId,
            @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.search(invoiceId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_UPDATE')")
    public PaymentResponse create(@Valid @RequestBody PaymentRequest request) {
        return paymentService.create(request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALE_CANCEL')")
    public PaymentResponse cancel(@PathVariable Long id) {
        return paymentService.cancel(id);
    }
}
