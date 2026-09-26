package com.sales.smartBusiness.tax;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Company-wide fiscal configuration, guarded by the company-settings permissions.
 */
@RestController
@RequestMapping("/api/settings/taxes")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    /**
     * Also open to whoever puts taxes on a document or a product form: a sales agent has no
     * business reading the company settings, but cannot write a quote or a purchase order without the tax list.
     * Reading the list only — one tax by id, and every write, stay on the settings permissions.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPANY_VIEW', 'SALE_CREATE', 'SALE_UPDATE', "
            + "'PURCHASE_CREATE', 'PURCHASE_UPDATE', 'PRODUCT_CREATE', 'PRODUCT_UPDATE')")
    public List<TaxResponse> findAll() {
        return taxService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_VIEW')")
    public TaxResponse findById(@PathVariable Long id) {
        return taxService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public TaxResponse create(@Valid @RequestBody TaxRequest request) {
        return taxService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public TaxResponse update(@PathVariable Long id, @Valid @RequestBody TaxRequest request) {
        return taxService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public void delete(@PathVariable Long id) {
        taxService.delete(id);
    }
}
