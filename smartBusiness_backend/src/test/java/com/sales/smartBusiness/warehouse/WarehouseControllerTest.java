package com.sales.smartBusiness.warehouse;

import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WarehouseController.class)
@AutoConfigureMockMvc(addFilters = false)
class WarehouseControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private WarehouseService warehouseService;

    @Test
    @DisplayName("GET returns the list")
    void listReturns200() throws Exception {
        when(warehouseService.findAll()).thenReturn(List.of(new WarehouseResponse()));

        mockMvc.perform(get("/api/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(warehouseService.create(any())).thenReturn(new WarehouseResponse());

        mockMvc.perform(post("/api/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Annex\",\"address\":\"Sfax\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 when the name is blank")
    void createReturns400() throws Exception {
        mockMvc.perform(post("/api/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(warehouseService, never()).create(any());
    }

    @Test
    @DisplayName("POST returns 409 when the name is taken")
    void createReturns409() throws Exception {
        when(warehouseService.create(any())).thenThrow(new DuplicateResourceException("A warehouse with this name already exists"));

        mockMvc.perform(post("/api/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Annex\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE returns 204, or 422 when the warehouse cannot go")
    void deleteEndpoint() throws Exception {
        mockMvc.perform(delete("/api/warehouses/3")).andExpect(status().isNoContent());

        doThrow(new BusinessRuleException("The default warehouse cannot be deleted"))
                .when(warehouseService).delete(1L);
        mockMvc.perform(delete("/api/warehouses/1")).andExpect(status().isUnprocessableEntity());
    }
}
