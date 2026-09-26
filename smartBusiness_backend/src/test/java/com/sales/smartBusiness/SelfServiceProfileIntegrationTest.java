package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account holder editing their own profile and password from the settings screen —
 * no administrative permission involved, only an authenticated session.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SelfServiceProfileIntegrationTest extends IntegrationTest {

    private String token;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Self Service Co", uniqueEmail("holder"));
    }

    @Test
    @DisplayName("the account holder updates their own name and phone")
    void updatesOwnProfile() throws Exception {
        patchJson("/api/auth/me", token, """
                {"firstName":"Mohamed","lastName":"Daoud","phone":"+216 54 040 502"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Mohamed"))
                .andExpect(jsonPath("$.lastName").value("Daoud"))
                .andExpect(jsonPath("$.fullName").value("Mohamed Daoud"))
                .andExpect(jsonPath("$.phone").value("+216 54 040 502"));

        getJson("/api/auth/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Mohamed Daoud"));
    }

    @Test
    @DisplayName("a blank name is rejected")
    void rejectsBlankName() throws Exception {
        patchJson("/api/auth/me", token, """
                {"firstName":"","lastName":"Daoud"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("First name")));
    }

    @Test
    @DisplayName("the wrong current password is refused")
    void refusesWrongCurrentPassword() throws Exception {
        patchJson("/api/auth/change-password", token, """
                {"currentPassword":"not-the-password","newPassword":"brand-new-secret"}
                """)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the holder changes their password and signs in with the new one")
    void changesPasswordThenSignsIn() throws Exception {
        String email = uniqueEmail("rotator");
        String freshToken = registerCompany("Rotator Co", email);

        patchJson("/api/auth/change-password", freshToken, """
                {"currentPassword":"Password123","newPassword":"a-fresh-password"}
                """)
                .andExpect(status().isNoContent());

        login(email, "a-fresh-password");
    }
}
