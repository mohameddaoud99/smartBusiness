package com.sales.smartBusiness.warehouse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Reading is for anyone who sees stock; changing a warehouse is a stock adjustment right. */
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public List<WarehouseResponse> findAll() {
        return warehouseService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public WarehouseResponse findById(@PathVariable Long id) {
        return warehouseService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public void delete(@PathVariable Long id) {
        warehouseService.delete(id);
    }
}
