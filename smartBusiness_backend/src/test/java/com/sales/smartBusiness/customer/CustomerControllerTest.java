package com.sales.smartBusiness.customer;

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
@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CustomerService customerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CustomerRequest validRequest() {
        CustomerRequest request = new CustomerRequest();
        request.setType(CustomerType.COMPANY);
        request.setName("Société Alpha");
        request.setEmail("contact@alpha.tn");
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(customerService.create(any())).thenReturn(new CustomerResponse());

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 when required fields are missing")
    void createReturns400OnInvalidPayload() throws Exception {
        CustomerRequest request = validRequest();
        request.setName("");
        request.setType(null);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray());

        verify(customerService, never()).create(any());
    }

    @Test
    @DisplayName("POST returns 409 when the reference is taken")
    void createReturns409OnDuplicate() throws Exception {
        when(customerService.create(any()))
                .thenThrow(new DuplicateResourceException("A customer with this reference already exists"));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET by id returns 404 for a customer of another company")
    void findByIdReturns404() throws Exception {
        when(customerService.findById(99L)).thenThrow(ResourceNotFoundException.of("Customer", 99L));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("DELETE returns 204")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isNoContent());

        verify(customerService).delete(1L);
    }

    @Test
    @DisplayName("an unknown customer type in the query string is a 400, not a 500")
    void unknownTypeFilterReturns400() throws Exception {
        mockMvc.perform(get("/api/customers").param("type", "SUPPLIER"))
                .andExpect(status().isBadRequest());

        verify(customerService, never()).search(any(), any(), any());
    }
}
