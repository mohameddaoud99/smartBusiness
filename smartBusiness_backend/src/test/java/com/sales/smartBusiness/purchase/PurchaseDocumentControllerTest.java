package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
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

/** HTTP contract only — the service is mocked and the filter chain is off. */
@WebMvcTest(PurchaseDocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PurchaseDocumentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PurchaseDocumentService purchaseDocumentService;

    private static final String LINE = """
            {"designation":"Fabric","quantity":1,"unitPrice":30.000}""";

    private static String body(String type, String supplierId, String lines) {
        return """
                {"type":%s,"supplierId":%s,"issueDate":"2026-09-20","lines":%s}
                """.formatted(type, supplierId, lines);
    }

    private static String validJson() {
        return body("\"PURCHASE_ORDER\"", "3", "[" + LINE + "]");
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(purchaseDocumentService.create(any())).thenReturn(new PurchaseDocumentResponse());

        mockMvc.perform(post("/api/purchase-documents")
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 when there is no line, no supplier or no type")
    void createReturns400() throws Exception {
        mockMvc.perform(post("/api/purchase-documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"PURCHASE_ORDER\"", "3", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
        mockMvc.perform(post("/api/purchase-documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("null", "null", "[" + LINE + "]")))
                .andExpect(status().isBadRequest());

        verify(purchaseDocumentService, never()).create(any());
    }

    @Test
    @DisplayName("POST returns 400 for an unknown document type")
    void createReturns400OnUnknownType() throws Exception {
        mockMvc.perform(post("/api/purchase-documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"SALES_ORDER\"", "3", "[" + LINE + "]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST convert-to-invoice returns 201, and a refusal is a 422")
    void convertToInvoice() throws Exception {
        when(purchaseDocumentService.convertToInvoice(5L)).thenReturn(new PurchaseDocumentResponse());
        mockMvc.perform(post("/api/purchase-documents/5/convert-to-invoice")).andExpect(status().isCreated());

        when(purchaseDocumentService.convertToInvoice(6L))
                .thenThrow(new BusinessRuleException("This purchase order is already invoiced"));
        mockMvc.perform(post("/api/purchase-documents/6/convert-to-invoice"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This purchase order is already invoiced"));
    }

    @Test
    @DisplayName("POST convert-to-credit-note returns 201, and a refusal is a 422")
    void convertToCreditNote() throws Exception {
        when(purchaseDocumentService.convertToCreditNote(5L)).thenReturn(new PurchaseDocumentResponse());
        mockMvc.perform(post("/api/purchase-documents/5/convert-to-credit-note")).andExpect(status().isCreated());

        when(purchaseDocumentService.convertToCreditNote(6L))
                .thenThrow(new BusinessRuleException("This invoice is already credited in full"));
        mockMvc.perform(post("/api/purchase-documents/6/convert-to-credit-note"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This invoice is already credited in full"));
    }

    @Test
    @DisplayName("POST convert-to-return-note returns 201, and a refusal is a 422")
    void convertToReturnNote() throws Exception {
        when(purchaseDocumentService.convertToReturnNote(5L)).thenReturn(new PurchaseDocumentResponse());
        mockMvc.perform(post("/api/purchase-documents/5/convert-to-return-note")).andExpect(status().isCreated());

        when(purchaseDocumentService.convertToReturnNote(6L))
                .thenThrow(new BusinessRuleException("Only a validated goods receipt can be returned"));
        mockMvc.perform(post("/api/purchase-documents/6/convert-to-return-note"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only a validated goods receipt can be returned"));
    }

    @Test
    @DisplayName("GET list requires the document type")
    void listNeedsType() throws Exception {
        mockMvc.perform(get("/api/purchase-documents")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET list filters by type and returns a page")
    void listReturnsPage() throws Exception {
        when(purchaseDocumentService.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/purchase-documents").param("type", "GOODS_RECEIPT").param("status", "VALIDATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET returns 404 for an unknown document")
    void getReturns404() throws Exception {
        when(purchaseDocumentService.findById(99L))
                .thenThrow(ResourceNotFoundException.of("Purchase document", 99L));

        mockMvc.perform(get("/api/purchase-documents/99")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT returns 422 when the document is no longer a draft")
    void updateReturns422() throws Exception {
        when(purchaseDocumentService.update(eq(5L), any()))
                .thenThrow(new BusinessRuleException("Only a draft can be edited"));

        mockMvc.perform(put("/api/purchase-documents/5")
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only a draft can be edited"));
    }

    @Test
    @DisplayName("preview, validate and cancel answer 200; delete answers 204; convert answers 201")
    void workflowEndpoints() throws Exception {
        when(purchaseDocumentService.preview(any())).thenReturn(new PurchaseDocumentResponse());
        when(purchaseDocumentService.validate(5L)).thenReturn(new PurchaseDocumentResponse());
        when(purchaseDocumentService.cancel(5L)).thenReturn(new PurchaseDocumentResponse());
        when(purchaseDocumentService.convertToReceipt(5L)).thenReturn(new PurchaseDocumentResponse());

        mockMvc.perform(post("/api/purchase-documents/preview")
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/purchase-documents/5/validate")).andExpect(status().isOk());
        mockMvc.perform(post("/api/purchase-documents/5/cancel")).andExpect(status().isOk());
        mockMvc.perform(post("/api/purchase-documents/5/convert-to-receipt")).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/purchase-documents/5")).andExpect(status().isNoContent());

        verify(purchaseDocumentService).delete(5L);
        verify(purchaseDocumentService, never()).create(any());
    }
}
