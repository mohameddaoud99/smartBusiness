package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The platform-admin world and the company world must never overlap: a platform token
 * has exactly one authority ({@code PLATFORM_ADMIN}), a company token never has it, and
 * neither can be swapped in for the other's endpoints. A mistake here would let one
 * account type reach every company at once, so this is tested on its own.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformAuthorizationTest extends IntegrationTest {

    private String platformToken;
    private String companyToken;
    private long companyId;

    @BeforeAll
    void setUp() throws Exception {
        platformToken = platformLogin();
        companyToken = registerCompany("Platform Test Co");
        companyId = number(getJson("/api/auth/me", companyToken)
                .andReturn().getResponse().getContentAsString(), "$.companyId");
    }

    @Test
    @DisplayName("the bootstrap platform admin can sign in")
    void platformAdminSignsIn() throws Exception {
        getJson("/api/platform/auth/me", platformToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("platform-admin@test.local"));
    }

    @Test
    @DisplayName("wrong platform password is refused")
    void wrongPlatformPasswordRefused() throws Exception {
        postJson("/api/platform/auth/login", null, """
                {"email":"platform-admin@test.local","password":"WrongPassword"}
                """)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("anonymous cannot reach any platform endpoint")
    void anonymousCannotReachPlatform() throws Exception {
        getJson("/api/platform/companies", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a platform token cannot reach a single company endpoint")
    void platformTokenCannotReachCompanyEndpoints() throws Exception {
        getJson("/api/users", platformToken).andExpect(status().isForbidden());
        getJson("/api/company", platformToken).andExpect(status().isForbidden());
        getJson("/api/roles", platformToken).andExpect(status().isForbidden());
        getJson("/api/branches", platformToken).andExpect(status().isForbidden());
        getJson("/api/audit-logs", platformToken).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a company token cannot reach any platform endpoint")
    void companyTokenCannotReachPlatform() throws Exception {
        getJson("/api/platform/companies", companyToken).andExpect(status().isForbidden());
        getJson("/api/platform/companies/" + companyId, companyToken).andExpect(status().isForbidden());
        getJson("/api/platform/auth/me", companyToken).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a brand new company starts with every business module enabled")
    void newCompanyStartsWithAllModules() throws Exception {
        getJson("/api/platform/companies/" + companyId, platformToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledModules.length()").value(4));
    }

    @Test
    @DisplayName("the platform admin can narrow a company down to the modules it needs")
    void platformAdminNarrowsModules() throws Exception {
        // Its own company: narrowing modules must not affect newCompanyStartsWithAllModules
        String token = registerCompany("Purchases And Inventory Co");
        long id = number(getJson("/api/auth/me", token)
                .andReturn().getResponse().getContentAsString(), "$.companyId");

        // One toggle each — Purchases carries Suppliers with it, Inventory carries Products
        putJson("/api/platform/companies/" + id + "/modules", platformToken, """
                {"modules":["PURCHASES","INVENTORY"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledModules.length()").value(2))
                .andExpect(jsonPath("$.enabledModules", org.hamcrest.Matchers.containsInAnyOrder(
                        "PURCHASES", "INVENTORY")));

        // Visible from the company's own audit log, attributed to the platform admin
        getJson("/api/audit-logs?size=1", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("[platform] platform-admin@test.local"))
                .andExpect(jsonPath("$.content[0].actionLabel").value("Modules changed by platform admin"));
    }

    @Test
    @DisplayName("an administrative module does not even exist in this vocabulary")
    void administrativeModuleIsRejected() throws Exception {
        // USERS is not a BusinessModule at all — an unknown enum constant is a malformed
        // request, not a business rule to enforce
        putJson("/api/platform/companies/" + companyId + "/modules", platformToken, """
                {"modules":["USERS"]}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unknown company is not found")
    void unknownCompanyIsNotFound() throws Exception {
        getJson("/api/platform/companies/999999", platformToken)
                .andExpect(status().isNotFound());
    }
}
