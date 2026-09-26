package com.sales.smartBusiness.printprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrintProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class PrintProfileControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PrintProfileService printProfileService;

    @Test
    @DisplayName("GET returns the printable profile")
    void returnsProfile() throws Exception {
        PrintProfileResponse profile = new PrintProfileResponse();
        profile.setName("ABC Distribution");
        when(printProfileService.find()).thenReturn(profile);

        mockMvc.perform(get("/api/print-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ABC Distribution"))
                .andExpect(jsonPath("$.bankAccounts").isArray());
    }
}
