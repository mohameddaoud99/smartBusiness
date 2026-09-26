package com.sales.smartBusiness.user;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the HTTP contract: status codes and the error shape produced by
 * GlobalExceptionHandler. The service is mocked and the filter chain is switched off —
 * authorization is covered by its own test, not by this slice.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserService userService;

    /** Local instance — the @WebMvcTest slice does not expose an ObjectMapper bean. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserRequest validRequest() {
        UserRequest request = new UserRequest();
        request.setFirstName("Sonia");
        request.setLastName("Trabelsi");
        request.setUsername("s.trabelsi");
        request.setEmail("sonia@example.com");
        request.setStatus(UserStatus.ACTIVE);
        request.setPassword("Password123");
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @DisplayName("POST returns 201 when the payload is valid")
    void createReturns201() throws Exception {
        when(userService.create(any())).thenReturn(new UserResponse());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST returns 400 with field messages when validation fails")
    void createReturns400OnInvalidPayload() throws Exception {
        UserRequest request = validRequest();
        request.setEmail("not-an-email");
        request.setFirstName("");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray());

        verify(userService, never()).create(any());
    }

    @Test
    @DisplayName("POST returns 409 when the username is taken")
    void createReturns409OnDuplicate() throws Exception {
        when(userService.create(any()))
                .thenThrow(new DuplicateResourceException("This username is already taken"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This username is already taken"));
    }

    @Test
    @DisplayName("GET by id returns 404 for a user of another company")
    void findByIdReturns404() throws Exception {
        when(userService.findById(99L))
                .thenThrow(ResourceNotFoundException.of("User", 99L));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PATCH deactivate returns 200")
    void deactivateReturns200() throws Exception {
        when(userService.deactivate(1L)).thenReturn(new UserResponse());

        mockMvc.perform(patch("/api/users/1/deactivate"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH deactivate returns 422 when the business rule blocks it")
    void deactivateReturns422WhenBlocked() throws Exception {
        when(userService.deactivate(anyLong()))
                .thenThrow(new BusinessRuleException("You cannot deactivate your own account"));

        mockMvc.perform(patch("/api/users/1/deactivate"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("You cannot deactivate your own account"));
    }

    @Test
    @DisplayName("PATCH reset-password rejects a password that is too short")
    void resetPasswordRejectsShortPassword() throws Exception {
        mockMvc.perform(patch("/api/users/1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).resetPassword(anyLong(), any());
    }
}
