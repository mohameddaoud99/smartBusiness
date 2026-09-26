package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack for the customers screen: reference generation, the type-driven identifiers
 * (tax number vs. national id + date of birth) and the nested billing / shipping address —
 * the parts a Mockito test cannot see (real JSON, real column mapping).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerIntegrationTest extends IntegrationTest {

    private String token;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Customers Test Co");
    }

    @Test
    @DisplayName("a company customer gets a generated reference and keeps its billing address")
    void createsCompanyCustomer() throws Exception {
        postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Société Alpha","contactName":"Mme Trabelsi",
                 "email":"contact@alpha.tn","taxId":"1234567A/A/M/000",
                 "billingAddress":{"street":"12 Rue de Carthage","city":"Tunis","region":"Tunis",
                                   "postalCode":"1002","country":"Tunisia"}}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("C-\\d{4}")))
                .andExpect(jsonPath("$.taxId").value("1234567A/A/M/000"))
                .andExpect(jsonPath("$.billingAddress.city").value("Tunis"))
                .andExpect(jsonPath("$.shippingAddress").doesNotExist());
    }

    @Test
    @DisplayName("an individual customer keeps its national id and date of birth")
    void createsIndividualCustomer() throws Exception {
        String body = postJson("/api/customers", token, """
                {"type":"INDIVIDUAL","name":"Amine Ben Salah","nationalId":"09876543",
                 "birthDate":"1990-05-14"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nationalId").value("09876543"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-14"))
                .andReturn().getResponse().getContentAsString();

        long id = number(body, "$.id");
        getJson("/api/customers/" + id, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-14"));
    }

    @Test
    @DisplayName("a future date of birth is rejected")
    void rejectsFutureBirthDate() throws Exception {
        postJson("/api/customers", token, """
                {"type":"INDIVIDUAL","name":"Time Traveller","birthDate":"2999-01-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(
                        org.hamcrest.Matchers.containsString("past")));
    }

    @Test
    @DisplayName("clearing the billing address on update removes it")
    void updateClearsAddress() throws Exception {
        String body = postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Beta SARL",
                 "billingAddress":{"city":"Sousse"}}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = number(body, "$.id");

        putJson("/api/customers/" + id, token, """
                {"type":"COMPANY","name":"Beta SARL"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingAddress").doesNotExist());
    }

    @Test
    @DisplayName("an explicit reference already used in the company is refused")
    void refusesDuplicateReference() throws Exception {
        postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Gamma","reference":"VIP-1"}
                """).andExpect(status().isCreated());

        postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Gamma bis","reference":"VIP-1"}
                """)
                .andExpect(status().isConflict());
    }
}
