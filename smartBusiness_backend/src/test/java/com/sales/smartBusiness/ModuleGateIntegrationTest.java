package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A business module a platform admin switched off is refused by the SERVER, on every one of its endpoints - not
 * just hidden in the menu. The caller's token is the same before and after: what they may do is re-read on each
 * request, so the switch applies at once, both ways. Each test builds its own company, so none depends on another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModuleGateIntegrationTest extends IntegrationTest {

    private String platformToken;

    @BeforeAll
    void setUp() throws Exception {
        platformToken = platformLogin();
    }

    // ----- Fixtures -----

    private long companyIdOf(String token) throws Exception {
        return number(getJson("/api/auth/me", token).andReturn().getResponse().getContentAsString(), "$.companyId");
    }

    private void setModules(long companyId, String modulesJson) throws Exception {
        putJson("/api/platform/companies/" + companyId + "/modules", platformToken,
                "{\"modules\":" + modulesJson + "}").andExpect(status().isOk());
    }

    private void expectAllowed(String token, String... urls) throws Exception {
        for (String url : urls) {
            getJson(url, token).andExpect(status().isOk());
        }
    }

    private void expectRefused(String token, String... urls) throws Exception {
        for (String url : urls) {
            getJson(url, token).andExpect(status().isForbidden());
        }
    }

    private static final String CUSTOMERS = "/api/customers";
    private static final String SUPPLIERS = "/api/suppliers";
    private static final String PRODUCTS = "/api/products";
    private static final String STOCK = "/api/stock/levels";
    private static final String WAREHOUSES = "/api/warehouses";
    private static final String SALES = "/api/sales-documents?type=QUOTE";
    private static final String PAYMENTS = "/api/payments";
    private static final String PURCHASES = "/api/purchase-documents?type=PURCHASE_ORDER";
    private static final String SUPPLIER_PAYMENTS = "/api/supplier-payments";

    /** A document that passes validation: (type, name of the party field). */
    private static final String DOCUMENT_BODY = """
            {"type":"%s","%s":1,"issueDate":"2026-09-20",
             "lines":[{"designation":"Item","quantity":1,"unitPrice":1}]}
            """;

    // ----- Tests -----

    @Test
    @DisplayName("a new company can reach every module")
    void everythingIsOnByDefault() throws Exception {
        String token = registerCompany("Gate Everything Co");

        expectAllowed(token, CUSTOMERS, SUPPLIERS, PRODUCTS, STOCK, WAREHOUSES, SALES, PAYMENTS, PURCHASES, SUPPLIER_PAYMENTS);
    }

    @Test
    @DisplayName("switching Sales off refuses its endpoints and its payments, and leaves the other modules alone")
    void salesOff() throws Exception {
        String token = registerCompany("Gate Sales Co");
        setModules(companyIdOf(token), "[\"CUSTOMERS\",\"PURCHASES\",\"INVENTORY\"]");

        expectRefused(token, SALES, PAYMENTS);
        // A valid body: validation runs before the permission check, so an empty one would be a 400 either way
        postJson("/api/sales-documents", token, DOCUMENT_BODY.formatted("QUOTE", "customerId")).andExpect(status().isForbidden());
        postJson("/api/payments", token, """
                {"invoiceId":1,"amount":1,"paymentDate":"2026-09-21","method":"CASH"}
                """).andExpect(status().isForbidden());
        expectAllowed(token, CUSTOMERS, SUPPLIERS, PRODUCTS, STOCK, PURCHASES, SUPPLIER_PAYMENTS);
    }

    @Test
    @DisplayName("switching Purchases off refuses purchase documents, supplier payments AND suppliers together")
    void purchasesOff() throws Exception {
        String token = registerCompany("Gate Purchases Co");
        setModules(companyIdOf(token), "[\"CUSTOMERS\",\"SALES\",\"INVENTORY\"]");

        expectRefused(token, PURCHASES, SUPPLIER_PAYMENTS, SUPPLIERS);
        postJson("/api/purchase-documents", token, DOCUMENT_BODY.formatted("PURCHASE_ORDER", "supplierId"))
                .andExpect(status().isForbidden());
        expectAllowed(token, CUSTOMERS, SALES, PAYMENTS, PRODUCTS, STOCK);
    }

    @Test
    @DisplayName("switching Inventory off refuses products, stock and warehouses together")
    void inventoryOff() throws Exception {
        String token = registerCompany("Gate Inventory Co");
        setModules(companyIdOf(token), "[\"CUSTOMERS\",\"SALES\",\"PURCHASES\"]");

        expectRefused(token, PRODUCTS, STOCK, WAREHOUSES);
        expectAllowed(token, CUSTOMERS, SUPPLIERS, SALES, PURCHASES);
    }

    @Test
    @DisplayName("switching Customers off refuses the customers only")
    void customersOff() throws Exception {
        String token = registerCompany("Gate Customers Co");
        setModules(companyIdOf(token), "[\"SALES\",\"PURCHASES\",\"INVENTORY\"]");

        expectRefused(token, CUSTOMERS);
        postJson(CUSTOMERS, token, "{\"type\":\"COMPANY\",\"name\":\"Nobody\"}").andExpect(status().isForbidden());
        expectAllowed(token, SALES, PURCHASES, PRODUCTS);
    }

    @Test
    @DisplayName("a company with no business module keeps its administration: users, roles, branches, company, audit")
    void administrationStays() throws Exception {
        String token = registerCompany("Gate Nothing Co");
        setModules(companyIdOf(token), "[]");

        expectRefused(token, CUSTOMERS, SUPPLIERS, PRODUCTS, STOCK, WAREHOUSES, SALES, PAYMENTS, PURCHASES, SUPPLIER_PAYMENTS);
        expectAllowed(token, "/api/users", "/api/roles", "/api/branches", "/api/company", "/api/audit-logs", "/api/auth/me");
    }

    @Test
    @DisplayName("the same token is refused, then accepted again, as the module is switched off and on: nothing is cached")
    void switchAppliesAtOnce() throws Exception {
        String token = registerCompany("Gate Toggle Co");
        long id = companyIdOf(token);
        expectAllowed(token, SALES);

        setModules(id, "[\"CUSTOMERS\",\"PURCHASES\",\"INVENTORY\"]");
        expectRefused(token, SALES);

        setModules(id, "[\"CUSTOMERS\",\"SALES\",\"PURCHASES\",\"INVENTORY\"]");
        expectAllowed(token, SALES);
    }

    @Test
    @DisplayName("the session tells the frontend the same thing: no permission of a module the company may not use")
    void sessionFollowsTheGate() throws Exception {
        String token = registerCompany("Gate Session Co");
        setModules(companyIdOf(token), "[\"CUSTOMERS\",\"INVENTORY\"]");

        getJson("/api/auth/me", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledModules", containsInAnyOrder("CUSTOMERS", "INVENTORY")))
                .andExpect(jsonPath("$.permissions", hasItem("CUSTOMER_VIEW")))
                .andExpect(jsonPath("$.permissions", hasItem("PRODUCT_VIEW")))
                .andExpect(jsonPath("$.permissions", hasItem("USER_VIEW")))
                .andExpect(jsonPath("$.permissions", not(hasItem("SALE_VIEW"))))
                .andExpect(jsonPath("$.permissions", not(hasItem("PURCHASE_VIEW"))))
                .andExpect(jsonPath("$.permissions", not(hasItem("SUPPLIER_VIEW"))));
    }

    @Test
    @DisplayName("switching a module off for one company changes nothing for another")
    void otherCompaniesAreUntouched() throws Exception {
        String off = registerCompany("Gate Off Co");
        String on = registerCompany("Gate On Co");

        setModules(companyIdOf(off), "[]");

        expectRefused(off, SALES);
        expectAllowed(on, SALES, PURCHASES, CUSTOMERS);
    }

    @Test
    @DisplayName("a user cannot be given a role, nor a role a permission, of a module the company may not use")
    void cannotGrantWhatTheCompanyCannotUse() throws Exception {
        String token = registerCompany("Gate Grant Co");
        setModules(companyIdOf(token), "[\"CUSTOMERS\"]");

        // The administrator no longer holds SALE_VIEW, so they cannot hand it out either
        postJson("/api/roles", token, """
                {"name":"SALES_ONLY","label":"Sales only","description":"x","permissions":["SALE_VIEW"]}
                """).andExpect(status().isUnprocessableEntity());
    }
}
