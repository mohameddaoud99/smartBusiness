package com.sales.smartBusiness.sales;

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

/**
 * HTTP contract only — the service is mocked and the filter chain is off.
 */
@WebMvcTest(SalesDocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class SalesDocumentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SalesDocumentService salesDocumentService;

    private static final String LINE = """
            {"designation":"Pantalon","quantity":1,"unitPrice":30.000}""";

    private static String body(String type, String customerId, String lines) {
        return """
                {"type":%s,"customerId":%s,"issueDate":"2026-09-20","lines":%s}
                """.formatted(type, customerId, lines);
    }

    private static String validJson() {
        return body("\"QUOTE\"", "3", "[" + LINE + "]");
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(salesDocumentService.create(any())).thenReturn(new SalesDocumentResponse());

        mockMvc.perform(post("/api/sales-documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 when a document has no line")
    void createReturns400WithoutLines() throws Exception {
        mockMvc.perform(post("/api/sales-documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"QUOTE\"", "3", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verify(salesDocumentService, never()).create(any());
    }

    @Test
    @DisplayName("POST returns 400 when a line has no quantity or a negative price")
    void createReturns400OnInvalidLine() throws Exception {
        mockMvc.perform(post("/api/sales-documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"QUOTE\"", "3", "[{\"designation\":\"X\",\"unitPrice\":-1}]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST returns 400 when the customer or the type is missing")
    void createReturns400WithoutCustomer() throws Exception {
        mockMvc.perform(post("/api/sales-documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("null", "null", "[" + LINE + "]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST convert-to-credit-note returns 201, and a refusal is a 422")
    void convertToCreditNote() throws Exception {
        when(salesDocumentService.convertToCreditNote(5L)).thenReturn(new SalesDocumentResponse());
        mockMvc.perform(post("/api/sales-documents/5/convert-to-credit-note")).andExpect(status().isCreated());

        when(salesDocumentService.convertToCreditNote(6L))
                .thenThrow(new BusinessRuleException("This invoice is already credited in full"));
        mockMvc.perform(post("/api/sales-documents/6/convert-to-credit-note"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This invoice is already credited in full"));
    }

    @Test
    @DisplayName("POST convert-to-return-note returns 201, and a refusal is a 422")
    void convertToReturnNote() throws Exception {
        when(salesDocumentService.convertToReturnNote(5L)).thenReturn(new SalesDocumentResponse());
        mockMvc.perform(post("/api/sales-documents/5/convert-to-return-note")).andExpect(status().isCreated());

        when(salesDocumentService.convertToReturnNote(6L))
                .thenThrow(new BusinessRuleException("Only a delivered delivery note can be returned"));
        mockMvc.perform(post("/api/sales-documents/6/convert-to-return-note"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only a delivered delivery note can be returned"));
    }

    @Test
    @DisplayName("GET list requires the document type")
    void listNeedsType() throws Exception {
        mockMvc.perform(get("/api/sales-documents"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET list filters by type and returns a page")
    void listReturnsPage() throws Exception {
        when(salesDocumentService.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/sales-documents").param("type", "QUOTE").param("status", "ISSUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET returns 404 for an unknown document")
    void getReturns404() throws Exception {
        when(salesDocumentService.findById(99L)).thenThrow(ResourceNotFoundException.of("Sales document", 99L));

        mockMvc.perform(get("/api/sales-documents/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /preview answers 200 without creating anything")
    void previewReturns200() throws Exception {
        when(salesDocumentService.preview(any())).thenReturn(new SalesDocumentResponse());

        mockMvc.perform(post("/api/sales-documents/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());

        verify(salesDocumentService, never()).create(any());
    }

    @Test
    @DisplayName("PUT returns 422 when the document is no longer a draft")
    void updateReturns422WhenNotDraft() throws Exception {
        when(salesDocumentService.update(eq(5L), any()))
                .thenThrow(new BusinessRuleException("Only a draft can be edited"));

        mockMvc.perform(put("/api/sales-documents/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only a draft can be edited"));
    }

    @Test
    @DisplayName("DELETE returns 204")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/sales-documents/5"))
                .andExpect(status().isNoContent());

        verify(salesDocumentService).delete(5L);
    }

    @Test
    @DisplayName("POST /issue, /status and /cancel answer 200")
    void workflowEndpoints() throws Exception {
        when(salesDocumentService.issue(5L)).thenReturn(new SalesDocumentResponse());
        when(salesDocumentService.changeStatus(5L, SalesDocumentStatus.ACCEPTED)).thenReturn(new SalesDocumentResponse());
        when(salesDocumentService.cancel(5L)).thenReturn(new SalesDocumentResponse());

        mockMvc.perform(post("/api/sales-documents/5/issue")).andExpect(status().isOk());
        mockMvc.perform(post("/api/sales-documents/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sales-documents/5/cancel")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /status returns 400 for an unknown status value")
    void statusRejectsUnknownValue() throws Exception {
        mockMvc.perform(post("/api/sales-documents/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /convert-to-order returns 201")
    void convertReturns201() throws Exception {
        when(salesDocumentService.convertToOrder(5L)).thenReturn(new SalesDocumentResponse());

        mockMvc.perform(post("/api/sales-documents/5/convert-to-order"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /convert-to-delivery-note returns 201")
    void convertToDeliveryNoteReturns201() throws Exception {
        when(salesDocumentService.convertToDeliveryNote(5L)).thenReturn(new SalesDocumentResponse());

        mockMvc.perform(post("/api/sales-documents/5/convert-to-delivery-note"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /status accepts DELIVERED, and the list accepts the delivery-note type")
    void deliveredStatusAndType() throws Exception {
        when(salesDocumentService.changeStatus(5L, SalesDocumentStatus.DELIVERED)).thenReturn(new SalesDocumentResponse());
        when(salesDocumentService.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(post("/api/sales-documents/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/sales-documents").param("type", "DELIVERY_NOTE"))
                .andExpect(status().isOk());
    }
}
