package com.sales.smartBusiness.stock;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /** The inventory: one row per good with what is there, what is promised and what is left. */
    @GetMapping("/levels")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public Page<StockLevelResponse> levels(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "false") boolean lowOnly,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return stockService.levels(search, warehouseId, lowOnly, pageable);
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public Page<StockMovementResponse> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) StockMovementType type,
            @PageableDefault(size = 10, sort = {"occurredAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return stockService.searchMovements(productId, warehouseId, type, pageable);
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockMovementResponse record(@Valid @RequestBody StockMovementRequest request) {
        return stockService.record(request);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_TRANSFER')")
    public List<StockMovementResponse> transfer(@Valid @RequestBody StockTransferRequest request) {
        return stockService.transfer(request);
    }
}
