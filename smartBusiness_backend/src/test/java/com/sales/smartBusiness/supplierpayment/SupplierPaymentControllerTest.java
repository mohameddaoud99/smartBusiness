package com.sales.smartBusiness.supplierpayment;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP contract only: the service is mocked and the filter chain is off.
 */
@WebMvcTest(SupplierPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class SupplierPaymentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SupplierPaymentService paymentService;

    private static String body(String invoiceId, String amount, String date, String method) {
        return """
                {"invoiceId":%s,"amount":%s,"paymentDate":%s,"method":%s}
                """.formatted(invoiceId, amount, date, method);
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(paymentService.create(any())).thenReturn(new SupplierPaymentResponse());

        mockMvc.perform(post("/api/supplier-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("5", "10.500", "\"2026-09-21\"", "\"CASH\"")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 when the amount is zero, negative or has too many decimals")
    void createReturns400OnBadAmount() throws Exception {
        for (String amount : new String[]{"0", "-3", "1.0001"}) {
            mockMvc.perform(post("/api/supplier-payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("5", amount, "\"2026-09-21\"", "\"CASH\"")))
                    .andExpect(status().isBadRequest());
        }
        verify(paymentService, never()).create(any());
    }

    @Test
    @DisplayName("a business rule surfaces as 422 with its message")
    void businessRuleIs422() throws Exception {
        when(paymentService.create(any())).thenThrow(new BusinessRuleException("This invoice is already paid in full"));

        mockMvc.perform(post("/api/supplier-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("5", "1", "\"2026-09-21\"", "\"CASH\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This invoice is already paid in full"));
    }

    @Test
    @DisplayName("GET lists the payments, optionally of one invoice")
    void listReturns200() throws Exception {
        when(paymentService.search(eq(5L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/supplier-payments").param("invoiceId", "5")).andExpect(status().isOk());
        verify(paymentService).search(eq(5L), any());
    }

    @Test
    @DisplayName("POST cancel returns 200")
    void cancelReturns200() throws Exception {
        when(paymentService.cancel(30L)).thenReturn(new SupplierPaymentResponse());

        mockMvc.perform(post("/api/supplier-payments/30/cancel")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a payment can be neither edited nor deleted")
    void noPutNoDelete() throws Exception {
        mockMvc.perform(put("/api/supplier-payments/30").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/supplier-payments/30")).andExpect(status().isNotFound());
    }
}
