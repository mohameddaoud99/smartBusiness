package com.sales.smartBusiness.stock;

import com.sales.smartBusiness.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP contract only — the service is mocked and the filter chain is off. */
@WebMvcTest(StockController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private StockService stockService;

    private static final String MOVEMENT = """
            {"type":"ENTRY","productId":20,"warehouseId":1,"quantity":10,"reason":"Delivery"}""";

    @Test
    @DisplayName("POST /movements returns 201 when the payload is valid")
    void recordReturns201() throws Exception {
        when(stockService.record(any())).thenReturn(new StockMovementResponse());

        mockMvc.perform(post("/api/stock/movements")
                        .contentType(MediaType.APPLICATION_JSON).content(MOVEMENT))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /movements returns 400 when the product, warehouse or quantity is missing, or the quantity negative")
    void recordReturns400OnInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/stock/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ENTRY\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());

        verify(stockService, never()).record(any());
    }

    @Test
    @DisplayName("POST /movements returns 400 for an unknown movement type")
    void recordReturns400OnUnknownType() throws Exception {
        mockMvc.perform(post("/api/stock/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOVEMENT.replace("ENTRY", "TELEPORT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /movements returns 422 when a business rule blocks it")
    void recordReturns422() throws Exception {
        when(stockService.record(any())).thenThrow(new BusinessRuleException("Not enough stock"));

        mockMvc.perform(post("/api/stock/movements")
                        .contentType(MediaType.APPLICATION_JSON).content(MOVEMENT))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Not enough stock"));
    }

    @Test
    @DisplayName("POST /transfers returns 201 with both legs")
    void transferReturns201() throws Exception {
        when(stockService.transfer(any())).thenReturn(List.of(new StockMovementResponse(), new StockMovementResponse()));

        mockMvc.perform(post("/api/stock/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":20,\"fromWarehouseId\":1,\"toWarehouseId\":2,\"quantity\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("POST /transfers returns 400 for a zero quantity")
    void transferReturns400() throws Exception {
        mockMvc.perform(post("/api/stock/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":20,\"fromWarehouseId\":1,\"toWarehouseId\":2,\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /levels and /movements return a page")
    void readsReturnPages() throws Exception {
        when(stockService.levels(any(), any(), anyBoolean(), any())).thenReturn(Page.empty());
        when(stockService.searchMovements(any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/stock/levels").param("lowOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/api/stock/movements").param("type", "ENTRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
