package com.sales.smartBusiness.dashboard;

import com.sales.smartBusiness.purchase.PurchaseDocumentService;
import com.sales.smartBusiness.purchase.PurchaseFigures;
import com.sales.smartBusiness.sales.SalesDocumentService;
import com.sales.smartBusiness.sales.SalesFigures;
import com.sales.smartBusiness.stock.StockFigures;
import com.sales.smartBusiness.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The figures of the home page, one endpoint per business area. Each one asks the feature that owns the data and is
 * guarded by that feature's own right: a person sees on their dashboard what they may see in its module, no more
 * (and a module the company switched off answers 403, like the rest of its endpoints). The dashboard has no data of
 * its own - which is why it has no entity, no repository and no service: there is nothing here to add to what the
 * features already compute.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SalesDocumentService salesDocumentService;
    private final PurchaseDocumentService purchaseDocumentService;
    private final StockService stockService;

    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public SalesFigures sales() {
        return salesDocumentService.figures(LocalDate.now());
    }

    @GetMapping("/purchases")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public PurchaseFigures purchases() {
        return purchaseDocumentService.figures(LocalDate.now());
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public StockFigures stock() {
        return stockService.figures();
    }
}
