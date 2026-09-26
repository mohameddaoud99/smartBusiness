package com.sales.smartBusiness.dashboard;

import com.sales.smartBusiness.common.AmountSummary;
import com.sales.smartBusiness.common.MonthAmount;
import com.sales.smartBusiness.purchase.PurchaseDocumentService;
import com.sales.smartBusiness.purchase.PurchaseFigures;
import com.sales.smartBusiness.sales.SalesDocumentService;
import com.sales.smartBusiness.sales.SalesFigures;
import com.sales.smartBusiness.stock.StockFigures;
import com.sales.smartBusiness.stock.StockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract only: the services are mocked and the filter chain is off.
 */
@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SalesDocumentService salesDocumentService;
    @MockitoBean private PurchaseDocumentService purchaseDocumentService;
    @MockitoBean private StockService stockService;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    @DisplayName("GET sales answers with the figures of the sales service")
    void sales() throws Exception {
        when(salesDocumentService.figures(any())).thenReturn(new SalesFigures(
                new BigDecimal("80.000"), new BigDecimal("130.000"), new AmountSummary(3L, new BigDecimal("140.000")),
                new AmountSummary(1L, new BigDecimal("30.000")), List.of(), List.of(new MonthAmount("2026-09", ZERO))));

        mockMvc.perform(get("/api/dashboard/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(80.0))
                .andExpect(jsonPath("$.unpaid.count").value(3))
                .andExpect(jsonPath("$.overdue.amount").value(30.0))
                .andExpect(jsonPath("$.months[0].month").value("2026-09"));
    }

    @Test
    @DisplayName("GET purchases answers with the figures of the purchase service")
    void purchases() throws Exception {
        when(purchaseDocumentService.figures(any())).thenReturn(new PurchaseFigures(
                ZERO, new BigDecimal("60.000"), new AmountSummary(0L, ZERO), new AmountSummary(0L, ZERO), List.of(), List.of()));

        mockMvc.perform(get("/api/dashboard/purchases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(60.0));
    }

    @Test
    @DisplayName("GET stock answers with the figures of the stock service")
    void stock() throws Exception {
        when(stockService.figures()).thenReturn(new StockFigures(new BigDecimal("210.000"), 1, List.of()));

        mockMvc.perform(get("/api/dashboard/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(210.0))
                .andExpect(jsonPath("$.lowStockCount").value(1));
    }

    @Test
    @DisplayName("the dashboard is read only")
    void readOnly() throws Exception {
        mockMvc.perform(post("/api/dashboard/sales")).andExpect(status().isMethodNotAllowed());
    }
}
