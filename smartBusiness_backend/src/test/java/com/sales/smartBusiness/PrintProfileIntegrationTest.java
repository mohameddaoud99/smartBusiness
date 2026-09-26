package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The company data a printed document needs, read by people who cannot open the company
 * settings — and only ever the caller's own company, and only what is meant to be printed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrintProfileIntegrationTest extends IntegrationTest {

    private static final String PROFILE = "/api/print-profile";

    private String tokenA;
    private String tokenB;

    @BeforeAll
    void setUp() throws Exception {
        tokenA = registerCompany("Print Company A");
        tokenB = registerCompany("Print Company B");

        putJson("/api/company", tokenA, """
                {"name":"Print Company A","email":"contact@a.tn","phone":"+216 71 000 000",
                 "address":"12 Rue de Carthage","postalCode":"1002","city":"Tunis",
                 "taxId":"1234567A/A/M/000","currency":"TND"}
                """).andExpect(status().isOk());

        postJson("/api/settings/bank-accounts", tokenA, """
                {"label":"Shown account","bankName":"BIAT","rib":"07000000000000000001","currency":"TND","showOnDocuments":true}
                """).andExpect(status().isCreated());
        postJson("/api/settings/bank-accounts", tokenA, """
                {"label":"Private account","bankName":"STB","rib":"10000000000000000002","currency":"TND","showOnDocuments":false}
                """).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("gives the identity of the company and only the bank accounts it shows on documents")
    void profileOfOwnCompany() throws Exception {
        getJson(PROFILE, tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Print Company A"))
                .andExpect(jsonPath("$.taxId").value("1234567A/A/M/000"))
                .andExpect(jsonPath("$.city").value("Tunis"))
                .andExpect(jsonPath("$.currency").value("TND"))
                .andExpect(jsonPath("$.logoDataUri").value(nullValue()))
                .andExpect(jsonPath("$.bankAccounts", hasSize(1)))
                .andExpect(jsonPath("$.bankAccounts[0].label").value("Shown account"))
                .andExpect(jsonPath("$.bankAccounts[0].rib").value("07000000000000000001"));
    }

    @Test
    @DisplayName("another company gets its own profile, never a neighbour's data")
    void profileIsScopedToTheCompany() throws Exception {
        getJson(PROFILE, tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Print Company B"))
                .andExpect(jsonPath("$.taxId").value(nullValue()))
                .andExpect(jsonPath("$.bankAccounts", hasSize(0)));
    }

    @Test
    @DisplayName("a sales agent and a purchase manager can read it without any company-settings right")
    void salesAndPurchaseUsersCanRead() throws Exception {
        String agentEmail = uniqueEmail("agent");
        createUser(tokenA, agentEmail, roleId(tokenA, "SALES_AGENT"));
        String buyerEmail = uniqueEmail("buyer");
        createUser(tokenA, buyerEmail, roleId(tokenA, "PURCHASE_MANAGER"));

        getJson(PROFILE, login(agentEmail, "Password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Print Company A"));
        getJson(PROFILE, login(buyerEmail, "Password123")).andExpect(status().isOk());
        // …yet the settings themselves stay closed to them
        getJson("/api/company", login(agentEmail, "Password123")).andExpect(status().isForbidden());
        getJson("/api/settings/bank-accounts", login(agentEmail, "Password123")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("someone with no sales, purchase or company right is refused, and so is an anonymous caller")
    void othersAreRefused() throws Exception {
        long stockOnlyRole = number(postJson("/api/roles", tokenA, """
                {"label":"Stock only","permissions":["STOCK_VIEW"]}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String email = uniqueEmail("stock");
        createUser(tokenA, email, stockOnlyRole);

        getJson(PROFILE, login(email, "Password123")).andExpect(status().isForbidden());
        getJson(PROFILE, null).andExpect(status().isUnauthorized());
    }
}
