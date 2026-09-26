package com.sales.smartBusiness.product;

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
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProductService productService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductRequest validRequest() {
        ProductRequest request = new ProductRequest();
        request.setName("USB-C Cable");
        request.setKind(ProductKind.GOOD);
        request.setPurpose(ProductPurpose.SALE);
        request.setUnit(ProductUnit.PIECE);
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(productService.create(any())).thenReturn(new ProductResponse());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 when required fields are missing")
    void createReturns400OnInvalidPayload() throws Exception {
        ProductRequest request = validRequest();
        request.setName("");
        request.setKind(null);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray());

        verify(productService, never()).create(any());
    }

    @Test
    @DisplayName("POST returns 409 when the reference is taken")
    void createReturns409OnDuplicate() throws Exception {
        when(productService.create(any()))
                .thenThrow(new DuplicateResourceException("A product with this reference already exists"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET by id returns 404 for a product of another company")
    void findByIdReturns404() throws Exception {
        when(productService.findById(99L)).thenThrow(ResourceNotFoundException.of("Product", 99L));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE returns 204")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).delete(1L);
    }

    @Test
    @DisplayName("an unknown kind in the query string is a 400, not a 500")
    void unknownKindFilterReturns400() throws Exception {
        mockMvc.perform(get("/api/products").param("kind", "NOT_A_KIND"))
                .andExpect(status().isBadRequest());

        verify(productService, never()).search(any(), any(), any(), any());
    }
}
