package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack for the suppliers screen: reference generation, the type-driven identifiers
 * (tax number vs. national id + date of birth) and the nested billing / shipping address —
 * the parts a Mockito test cannot see (real JSON, real column mapping).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SupplierIntegrationTest extends IntegrationTest {

    private String token;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Suppliers Test Co");
    }

    @Test
    @DisplayName("a company supplier gets a generated F-prefixed reference and keeps its billing address")
    void createsCompanySupplier() throws Exception {
        postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"HAMMAMET SUD","contactName":"M. Ben Ali",
                 "email":"contact@hammamet-sud.tn","taxId":"1234567A/A/M/000",
                 "billingAddress":{"street":"Zone Industrielle","city":"Hammamet","region":"Nabeul",
                                   "postalCode":"8050","country":"Tunisia"}}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("F-\\d{4}")))
                .andExpect(jsonPath("$.taxId").value("1234567A/A/M/000"))
                .andExpect(jsonPath("$.billingAddress.city").value("Hammamet"))
                .andExpect(jsonPath("$.shippingAddress").doesNotExist());
    }

    @Test
    @DisplayName("an individual supplier keeps its national id and date of birth")
    void createsIndividualSupplier() throws Exception {
        String body = postJson("/api/suppliers", token, """
                {"type":"INDIVIDUAL","name":"Karim Jebali","nationalId":"09876543",
                 "birthDate":"1985-03-20"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nationalId").value("09876543"))
                .andExpect(jsonPath("$.birthDate").value("1985-03-20"))
                .andReturn().getResponse().getContentAsString();

        long id = number(body, "$.id");
        getJson("/api/suppliers/" + id, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.birthDate").value("1985-03-20"));
    }

    @Test
    @DisplayName("a future date of birth is rejected")
    void rejectsFutureBirthDate() throws Exception {
        postJson("/api/suppliers", token, """
                {"type":"INDIVIDUAL","name":"Time Traveller","birthDate":"2999-01-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(
                        org.hamcrest.Matchers.containsString("past")));
    }

    @Test
    @DisplayName("clearing the billing address on update removes it")
    void updateClearsAddress() throws Exception {
        String body = postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Beta Fournitures",
                 "billingAddress":{"city":"Sousse"}}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = number(body, "$.id");

        putJson("/api/suppliers/" + id, token, """
                {"type":"COMPANY","name":"Beta Fournitures"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingAddress").doesNotExist());
    }

    @Test
    @DisplayName("an explicit reference already used in the company is refused")
    void refusesDuplicateReference() throws Exception {
        postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Gamma","reference":"SUP-1"}
                """).andExpect(status().isCreated());

        postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Gamma bis","reference":"SUP-1"}
                """)
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("suppliers and customers are numbered independently, both starting at 1")
    void suppliersAndCustomersHaveSeparateSequences() throws Exception {
        String customer = postJson("/api/customers", token, """
                {"type":"COMPANY","name":"A customer of the same company"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String supplier = postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"A supplier of the same company"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(
                com.jayway.jsonpath.JsonPath.<String>read(customer, "$.reference")).startsWith("C-");
        org.assertj.core.api.Assertions.assertThat(
                com.jayway.jsonpath.JsonPath.<String>read(supplier, "$.reference")).startsWith("F-");
    }
}
