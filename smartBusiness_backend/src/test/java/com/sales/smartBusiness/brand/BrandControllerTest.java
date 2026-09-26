package com.sales.smartBusiness.brand;

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

@WebMvcTest(BrandController.class)
@AutoConfigureMockMvc(addFilters = false)
class BrandControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BrandService brandService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BrandRequest validRequest() {
        BrandRequest request = new BrandRequest();
        request.setName("Samsung");
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(brandService.create(any())).thenReturn(new BrandResponse());

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 when the name is missing")
    void createReturns400OnInvalidPayload() throws Exception {
        BrandRequest request = validRequest();
        request.setName("");

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest());

        verify(brandService, never()).create(any());
    }

    @Test
    @DisplayName("POST returns 409 when the name is taken")
    void createReturns409OnDuplicate() throws Exception {
        when(brandService.create(any()))
                .thenThrow(new DuplicateResourceException("A brand with this name already exists"));

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET by id returns 404 for a brand of another company")
    void findByIdReturns404() throws Exception {
        when(brandService.findById(99L)).thenThrow(ResourceNotFoundException.of("Brand", 99L));

        mockMvc.perform(get("/api/brands/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE returns 204")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/brands/1"))
                .andExpect(status().isNoContent());

        verify(brandService).delete(1L);
    }
}
