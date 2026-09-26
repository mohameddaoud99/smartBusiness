package com.sales.smartBusiness;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack for quotes and sales orders: the totals with the real tax rows, the workflow,
 * numbering at issue time, the quote → order conversion and the delete protection of the
 * customer and the product — everything a Mockito test cannot see.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesIntegrationTest extends IntegrationTest {

    private static final String DOCUMENTS = "/api/sales-documents";

    private String token;
    private long customerId;
    private long productId;
    private long vat19;
    private long vat0;
    private long fodec;
    private long stamp;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Sales Test Co");

        String taxes = getJson("/api/settings/taxes", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        vat19 = taxId(taxes, "TVA 19%");
        vat0 = taxId(taxes, "TVA 0%");
        fodec = taxId(taxes, "FODEC");
        stamp = taxId(taxes, "Timbre fiscal");

        customerId = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Client Vente"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        productId = number(postJson("/api/products", token, """
                {"name":"Pantalon","kind":"GOOD","purpose":"SALE","unit":"PIECE","salePrice":30.000}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static long taxId(String taxes, String name) {
        List<Number> ids = JsonPath.read(taxes, "$[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }

    /** The Finco reference case: 30.000 + FODEC + VAT 19% + stamp = 37.057. */
    private String fincoDocument(String type) {
        return """
                {"type":"%s","customerId":%d,"issueDate":"2026-09-20","taxIds":[%d,%d],
                 "notes":"Thank you",
                 "lines":[{"productId":%d,"quantity":1,"vatTaxId":%d},
                          {"designation":"Free line","quantity":5,"unitPrice":0,"vatTaxId":%d}]}
                """.formatted(type, customerId, fodec, stamp, productId, vat19, vat19);
    }

    private long createDraft(String type) throws Exception {
        return number(postJson(DOCUMENTS, token, fincoDocument(type))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Totals -----

    @Test
    @DisplayName("a draft carries the totals of the Finco reference case, with the tax breakdown")
    void draftHasComputedTotals() throws Exception {
        postJson(DOCUMENTS, token, fincoDocument("QUOTE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").value(nullValue()))
                .andExpect(jsonPath("$.customerName").value("Client Vente"))
                .andExpect(jsonPath("$.subtotal").value(30.0))
                .andExpect(jsonPath("$.total").value(37.057))
                .andExpect(jsonPath("$.taxes", hasSize(3)))
                .andExpect(jsonPath("$.taxes[0].name").value("FODEC"))
                .andExpect(jsonPath("$.taxes[0].amount").value(0.3))
                .andExpect(jsonPath("$.taxes[1].name").value("VAT 19%"))
                .andExpect(jsonPath("$.taxes[1].base").value(30.3))
                .andExpect(jsonPath("$.taxes[1].amount").value(5.757))
                .andExpect(jsonPath("$.taxes[2].name").value("Timbre fiscal"))
                // the product line took its name, code and price from the catalogue
                .andExpect(jsonPath("$.lines[0].designation").value("Pantalon"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(30.0))
                .andExpect(jsonPath("$.lines[0].reference").value(matchesPattern("P-\\d{4}")));
    }

    @Test
    @DisplayName("the preview gives the same totals and creates nothing")
    void previewMatchesAndSavesNothing() throws Exception {
        String before = getJson(DOCUMENTS + "?type=SALES_ORDER&size=1", token)
                .andReturn().getResponse().getContentAsString();

        postJson(DOCUMENTS + "/preview", token, fincoDocument("SALES_ORDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nullValue()))
                .andExpect(jsonPath("$.total").value(37.057));

        String after = getJson(DOCUMENTS + "?type=SALES_ORDER&size=1", token)
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat((Number) JsonPath.read(after, "$.totalElements"))
                .isEqualTo(JsonPath.read(before, "$.totalElements"));
    }

    @Test
    @DisplayName("each VAT rate gets its own row")
    void severalVatRates() throws Exception {
        postJson(DOCUMENTS + "/preview", token, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":100,"vatTaxId":%d},
                          {"designation":"B","quantity":1,"unitPrice":200,"vatTaxId":%d}]}
                """.formatted(customerId, vat19, vat0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxes", hasSize(2)))
                .andExpect(jsonPath("$.taxes[0].name").value("VAT 0%"))
                .andExpect(jsonPath("$.taxes[0].base").value(200.0))
                .andExpect(jsonPath("$.taxes[1].name").value("VAT 19%"))
                .andExpect(jsonPath("$.taxes[1].amount").value(19.0))
                .andExpect(jsonPath("$.total").value(319.0));
    }

    @Test
    @DisplayName("a VAT rate cannot be used as a whole-document tax, nor a surcharge as a line VAT")
    void wrongTaxKindRefused() throws Exception {
        postJson(DOCUMENTS, token, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20","taxIds":[%d],
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1}]}
                """.formatted(customerId, vat19))
                .andExpect(status().isUnprocessableEntity());

        postJson(DOCUMENTS, token, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1,"vatTaxId":%d}]}
                """.formatted(customerId, fodec))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("not a VAT rate")));
    }

    // ----- Workflow -----

    @Test
    @DisplayName("issuing numbers the quote and freezes it")
    void issueNumbersAndFreezes() throws Exception {
        long id = createDraft("QUOTE");

        postJson(DOCUMENTS + "/" + id + "/issue", token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.reference").value(matchesPattern("QUO-\\d{4}-\\d{5}")));

        putJson(DOCUMENTS + "/" + id, token, fincoDocument("QUOTE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only a draft")));

        deleteJson(DOCUMENTS + "/" + id, token)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a draft can be edited, then deleted, and leaves no gap in the numbering")
    void draftEditAndDelete() throws Exception {
        long id = createDraft("QUOTE");

        putJson(DOCUMENTS + "/" + id, token, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-21",
                 "lines":[{"designation":"Only line","quantity":2,"unitPrice":10}]}
                """.formatted(customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.taxes[0].name").value("VAT 0%"))
                .andExpect(jsonPath("$.total").value(20.0));

        deleteJson(DOCUMENTS + "/" + id, token).andExpect(status().isNoContent());
        getJson(DOCUMENTS + "/" + id, token).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a quote is accepted, turned into a draft order once, and the order runs its own workflow")
    void quoteToOrderWorkflow() throws Exception {
        long quoteId = createDraft("QUOTE");
        postJson(DOCUMENTS + "/" + quoteId + "/issue", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + quoteId + "/status", token, "{\"status\":\"ACCEPTED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        String order = postJson(DOCUMENTS + "/" + quoteId + "/convert-to-order", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SALES_ORDER"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceId").value(quoteId))
                .andExpect(jsonPath("$.sourceReference").value(matchesPattern("QUO-.*")))
                .andExpect(jsonPath("$.total").value(37.057))
                .andExpect(jsonPath("$.taxes", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        long orderId = number(order, "$.id");

        // the quote now points at its order, and cannot be converted twice
        getJson(DOCUMENTS + "/" + quoteId, token)
                .andExpect(jsonPath("$.derived", hasSize(1)))
                .andExpect(jsonPath("$.derived[0].id").value(orderId));
        postJson(DOCUMENTS + "/" + quoteId + "/convert-to-order", token, "")
                .andExpect(status().isUnprocessableEntity());

        // the order: issue, confirm, cancel — then it stays cancelled
        postJson(DOCUMENTS + "/" + orderId + "/issue", token, "")
                .andExpect(jsonPath("$.reference").value(matchesPattern("SO-\\d{4}-\\d{5}")));
        postJson(DOCUMENTS + "/" + orderId + "/status", token, "{\"status\":\"CONFIRMED\"}")
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        postJson(DOCUMENTS + "/" + orderId + "/cancel", token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        postJson(DOCUMENTS + "/" + orderId + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity());
        postJson(DOCUMENTS + "/" + orderId + "/status", token, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isUnprocessableEntity());

        // a cancelled order frees the quote to be converted again
        postJson(DOCUMENTS + "/" + quoteId + "/convert-to-order", token, "")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a quote cannot be cancelled, and a draft cannot change status")
    void impossibleTransitions() throws Exception {
        long id = createDraft("QUOTE");

        postJson(DOCUMENTS + "/" + id + "/status", token, "{\"status\":\"ACCEPTED\"}")
                .andExpect(status().isUnprocessableEntity());

        postJson(DOCUMENTS + "/" + id + "/issue", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + id + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity());
    }

    // ----- Listing -----

    @Test
    @DisplayName("the list is filtered by type and by status")
    void listFilters() throws Exception {
        long id = createDraft("SALES_ORDER");
        postJson(DOCUMENTS + "/" + id + "/issue", token, "").andExpect(status().isOk());

        getJson(DOCUMENTS + "?type=SALES_ORDER&status=ISSUED&search=vente", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("SALES_ORDER"))
                .andExpect(jsonPath("$.content[0].customerName").value("Client Vente"))
                .andExpect(jsonPath("$.content[0].reference").value(matchesPattern("SO-.*")));

        getJson(DOCUMENTS + "?type=SALES_ORDER&status=REJECTED", token)
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ----- Protection of the customer and the product -----

    @Test
    @DisplayName("a customer or a product that appears on a document cannot be deleted")
    void deleteProtection() throws Exception {
        String customer = postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Client Protected"}
                """).andReturn().getResponse().getContentAsString();
        long protectedCustomer = number(customer, "$.id");
        String product = postJson("/api/products", token, """
                {"name":"Protected Item","kind":"GOOD","purpose":"SALE","unit":"PIECE"}
                """).andReturn().getResponse().getContentAsString();
        long protectedProduct = number(product, "$.id");

        long draftId = number(postJson(DOCUMENTS, token, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":5}]}
                """.formatted(protectedCustomer, protectedProduct))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        deleteJson("/api/customers/" + protectedCustomer, token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("sales document")));
        deleteJson("/api/products/" + protectedProduct, token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("sales document line")));

        // once the draft is gone, both can go
        deleteJson(DOCUMENTS + "/" + draftId, token).andExpect(status().isNoContent());
        deleteJson("/api/customers/" + protectedCustomer, token).andExpect(status().isNoContent());
        deleteJson("/api/products/" + protectedProduct, token).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a purchase-only product cannot be sold")
    void purchaseOnlyProductRefused() throws Exception {
        long purchaseOnly = number(postJson("/api/products", token, """
                {"name":"Raw material","kind":"GOOD","purpose":"PURCHASE","unit":"KILOGRAM"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        postJson(DOCUMENTS, token, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":5}]}
                """.formatted(customerId, purchaseOnly))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("purchase-only")));
    }

    // ----- Permissions -----

    @Test
    @DisplayName("a read-only sales role can list but neither create, issue nor cancel")
    void permissionsAreEnforced() throws Exception {
        long viewerRole = number(postJson("/api/roles", token, """
                {"label":"Sales viewer","permissions":["SALE_VIEW"]}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String email = uniqueEmail("viewer");
        createUser(token, email, viewerRole);
        String viewer = login(email, "Password123");
        long draftId = createDraft("QUOTE");

        getJson(DOCUMENTS + "?type=QUOTE", viewer).andExpect(status().isOk());
        getJson(DOCUMENTS + "/" + draftId, viewer).andExpect(status().isOk());
        postJson(DOCUMENTS + "/preview", viewer, fincoDocument("QUOTE")).andExpect(status().isOk());

        postJson(DOCUMENTS, viewer, fincoDocument("QUOTE")).andExpect(status().isForbidden());
        putJson(DOCUMENTS + "/" + draftId, viewer, fincoDocument("QUOTE")).andExpect(status().isForbidden());
        postJson(DOCUMENTS + "/" + draftId + "/issue", viewer, "").andExpect(status().isForbidden());
        postJson(DOCUMENTS + "/" + draftId + "/cancel", viewer, "").andExpect(status().isForbidden());
        deleteJson(DOCUMENTS + "/" + draftId, viewer).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a sales agent can read the tax list to build a document, but not change a tax")
    void salesAgentReadsTaxesButCannotWriteThem() throws Exception {
        String email = uniqueEmail("agent");
        createUser(token, email, roleId(token, "SALES_AGENT"));
        String agent = login(email, "Password123");

        getJson("/api/settings/taxes", agent).andExpect(status().isOk());
        getJson("/api/settings/taxes/" + vat19, agent).andExpect(status().isForbidden());
        postJson("/api/settings/taxes", agent, """
                {"name":"Sneaky","kind":"PERCENTAGE_SURCHARGE","rate":5}
                """).andExpect(status().isForbidden());
        postJson(DOCUMENTS, agent, fincoDocument("QUOTE")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an anonymous caller is refused")
    void anonymousRefused() throws Exception {
        getJson(DOCUMENTS + "?type=QUOTE", null).andExpect(status().isUnauthorized());
    }
}
