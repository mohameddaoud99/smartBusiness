package com.sales.smartBusiness.supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
@WebMvcTest(SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
class SupplierControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SupplierService supplierService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SupplierRequest validRequest() {
        SupplierRequest request = new SupplierRequest();
        request.setType(SupplierType.COMPANY);
        request.setName("HAMMAMET SUD");
        request.setEmail("contact@hammamet-sud.tn");
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(supplierService.create(any())).thenReturn(new SupplierResponse());

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 when required fields are missing")
    void createReturns400OnInvalidPayload() throws Exception {
        SupplierRequest request = validRequest();
        request.setName("");
        request.setType(null);

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray());

        verify(supplierService, never()).create(any());
    }

    @Test
    @DisplayName("POST returns 409 when the reference is taken")
    void createReturns409OnDuplicate() throws Exception {
        when(supplierService.create(any()))
                .thenThrow(new DuplicateResourceException("A supplier with this reference already exists"));

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET by id returns 404 for a supplier of another company")
    void findByIdReturns404() throws Exception {
        when(supplierService.findById(99L)).thenThrow(ResourceNotFoundException.of("Supplier", 99L));

        mockMvc.perform(get("/api/suppliers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("DELETE returns 204")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/suppliers/1"))
                .andExpect(status().isNoContent());

        verify(supplierService).delete(1L);
    }

    @Test
    @DisplayName("an unknown supplier type in the query string is a 400, not a 500")
    void unknownTypeFilterReturns400() throws Exception {
        mockMvc.perform(get("/api/suppliers").param("type", "CUSTOMER"))
                .andExpect(status().isBadRequest());

        verify(supplierService, never()).search(any(), any(), any());
    }
}
