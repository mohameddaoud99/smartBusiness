package com.sales.smartBusiness.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sales.smartBusiness.exception.BusinessRuleException;
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

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CategoryService categoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CategoryRequest validRequest() {
        CategoryRequest request = new CategoryRequest();
        request.setName("Electronics");
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(categoryService.create(any())).thenReturn(new CategoryResponse());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 when the name is missing")
    void createReturns400OnInvalidPayload() throws Exception {
        CategoryRequest request = validRequest();
        request.setName("");

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).create(any());
    }

    @Test
    @DisplayName("POST returns 409 when the name is taken")
    void createReturns409OnDuplicate() throws Exception {
        when(categoryService.create(any()))
                .thenThrow(new DuplicateResourceException("A category with this name already exists"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET by id returns 404 for a category of another company")
    void findByIdReturns404() throws Exception {
        when(categoryService.findById(99L)).thenThrow(ResourceNotFoundException.of("Category", 99L));

        mockMvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE returns 422 when the category is still in use")
    void deleteReturns422WhenInUse() throws Exception {
        doThrow(new BusinessRuleException("This category is still used by 3 product(s)."))
                .when(categoryService).delete(1L);

        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("DELETE returns 204")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(1L);
    }
}
