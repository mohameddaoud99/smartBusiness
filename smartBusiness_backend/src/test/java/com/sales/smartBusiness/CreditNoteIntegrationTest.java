package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack for the credit note: made from an invoice, adjusted while a draft, then issued against that
 * invoice - lowering what it asks for, together with the payments - and cancelled to give it back.
 * Every test builds its own product and invoice, so none depends on another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreditNoteIntegrationTest extends IntegrationTest {

    private static final String DOCUMENTS = "/api/sales-documents";
    private static final String PAYMENTS = "/api/payments";

    private String token;
    private long mainWarehouse;
    private long customerId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Credit Note Test Co");
        mainWarehouse = number(getJson("/api/warehouses", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        customerId = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Client Credit"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    private long good(String name) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"%s","kind":"GOOD","purpose":"SALE","unit":"PIECE","salePrice":10,"allowNegativeStock":true}
                """.formatted(name)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private long good() throws Exception {
        return good("Item-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /** An issued invoice of `quantity` pieces at 10.000: it asks for 10 x quantity. */
    private long issuedInvoice(long productId, int quantity) throws Exception {
        long invoice = number(postJson(DOCUMENTS, token, """
                {"type":"INVOICE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d,"unitPrice":10}]}
                """.formatted(customerId, mainWarehouse, productId, quantity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + invoice + "/issue", token, "").andExpect(status().isOk());
        return invoice;
    }

    /** A draft credit note made from the invoice, then lowered to the quantity really credited. */
    private long creditNoteFor(long invoice, long productId, int quantity) throws Exception {
        long note = number(postJson(DOCUMENTS + "/" + invoice + "/convert-to-credit-note", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CREDIT_NOTE"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceId").value(invoice))
                .andExpect(jsonPath("$.sourceType").value("INVOICE"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        putJson(DOCUMENTS + "/" + note, token, creditJson(customerId, productId, quantity)).andExpect(status().isOk());
        return note;
    }

    private String creditJson(long customer, long productId, int quantity) {
        return """
                {"type":"CREDIT_NOTE","customerId":%d,"issueDate":"2026-09-21",
                 "lines":[{"productId":%d,"quantity":%d,"unitPrice":10}]}
                """.formatted(customer, productId, quantity);
    }

    private void issue(long id) throws Exception {
        postJson(DOCUMENTS + "/" + id + "/issue", token, "").andExpect(status().isOk());
    }

    private void pay(long invoice, String amount) throws Exception {
        postJson(PAYMENTS, token, """
                {"invoiceId":%d,"amount":%s,"paymentDate":"2026-09-21","method":"CASH"}
                """.formatted(invoice, amount)).andExpect(status().isCreated());
    }

    private void expectInvoice(long invoice, String status, String credited, String paid, String balance) throws Exception {
        getJson(DOCUMENTS + "/" + invoice, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.creditedAmount").value(Double.parseDouble(credited)))
                .andExpect(jsonPath("$.paidAmount").value(Double.parseDouble(paid)))
                .andExpect(jsonPath("$.balance").value(Double.parseDouble(balance)));
    }

    // ----- Credit and payments -----

    @Test
    @DisplayName("a credit note is made from an invoice, lowered to what is credited, and once issued takes that off the invoice")
    void partialCredit() throws Exception {
        long product = good();
        long invoice = issuedInvoice(product, 5); // 50.000
        long note = creditNoteFor(invoice, product, 2); // 20.000

        expectInvoice(invoice, "ISSUED", "0", "0", "50"); // a draft credit note moves nothing
        postJson(DOCUMENTS + "/" + note + "/issue", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.reference").value(containsString("CN")))
                .andExpect(jsonPath("$.total").value(20.0));

        expectInvoice(invoice, "PARTIALLY_PAID", "20", "0", "30");
        getJson(DOCUMENTS + "/" + invoice, token)
                .andExpect(jsonPath("$.derived", hasSize(1)))
                .andExpect(jsonPath("$.derived[0].type").value("CREDIT_NOTE"));
    }

    @Test
    @DisplayName("payments are capped by what is left after the credit, and together they settle the invoice")
    void creditAndPayments() throws Exception {
        long product = good();
        long invoice = issuedInvoice(product, 5);
        issue(creditNoteFor(invoice, product, 2));

        postJson(PAYMENTS, token, """
                {"invoiceId":%d,"amount":30.001,"paymentDate":"2026-09-21","method":"CASH"}
                """.formatted(invoice)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("more than the 30.000 still due")));

        pay(invoice, "30.000");
        expectInvoice(invoice, "PAID", "20", "30", "0");
    }

    @Test
    @DisplayName("credit notes cannot take more off an invoice than it is worth, and the refused one stays a draft")
    void creditCannotExceedTheInvoice() throws Exception {
        long product = good();
        long invoice = issuedInvoice(product, 5); // 50
        issue(creditNoteFor(invoice, product, 3)); // 30 credited

        long tooMuch = creditNoteFor(invoice, product, 3); // 30 more
        postJson(DOCUMENTS + "/" + tooMuch + "/issue", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("more than the 20.000 that can still be credited")));

        getJson(DOCUMENTS + "/" + tooMuch, token)
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").doesNotExist());
        expectInvoice(invoice, "PARTIALLY_PAID", "30", "0", "20");

        putJson(DOCUMENTS + "/" + tooMuch, token, creditJson(customerId, product, 2)).andExpect(status().isOk());
        issue(tooMuch);
        expectInvoice(invoice, "PAID", "50", "0", "0");
    }

    @Test
    @DisplayName("an invoice credited in full is settled, and cannot be credited again")
    void creditedInFull() throws Exception {
        long product = good();
        long invoice = issuedInvoice(product, 2);
        issue(creditNoteFor(invoice, product, 2));

        expectInvoice(invoice, "PAID", "20", "0", "0");
        postJson(DOCUMENTS + "/" + invoice + "/convert-to-credit-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("credited in full")));
    }

    @Test
    @DisplayName("credited after being paid in full, an invoice shows what the customer is owed as a negative balance")
    void creditOfAPaidInvoice() throws Exception {
        long product = good();
        long invoice = issuedInvoice(product, 1); // 10
        pay(invoice, "10.000");
        expectInvoice(invoice, "PAID", "0", "10", "0");

        issue(creditNoteFor(invoice, product, 1)); // 10 credited on 10 paid... a full credit
        expectInvoice(invoice, "PAID", "10", "10", "-10");
    }

    @Test
    @DisplayName("cancelling a credit note gives the invoice back what it took off")
    void cancelCreditNote() throws Exception {
        long product = good();
        long invoice = issuedInvoice(product, 5);
        long note = creditNoteFor(invoice, product, 2);
        issue(note);
        expectInvoice(invoice, "PARTIALLY_PAID", "20", "0", "30");

        postJson(DOCUMENTS + "/" + note + "/cancel", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        expectInvoice(invoice, "ISSUED", "0", "0", "50");
        // and it can be credited again
        postJson(DOCUMENTS + "/" + invoice + "/convert-to-credit-note", token, "").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an invoice with a credit note cannot be cancelled until the credit note is")
    void invoiceWithCreditNoteCannotBeCancelled() throws Exception {
        long product = good();
        long invoice = issuedInvoice(product, 3);
        long note = creditNoteFor(invoice, product, 1);

        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has credit notes")));

        issue(note);
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has credit notes")));

        postJson(DOCUMENTS + "/" + note + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
    }

    // ----- Stock -----

    @Test
    @DisplayName("a credit note moves no stock")
    void creditNoteMovesNoStock() throws Exception {
        String name = "Item-" + UUID.randomUUID().toString().substring(0, 8);
        long product = good(name);
        postJson("/api/stock/movements", token, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":10,"reason":"test"}
                """.formatted(product, mainWarehouse)).andExpect(status().isCreated());
        long invoice = issuedInvoice(product, 4);

        issue(creditNoteFor(invoice, product, 4));

        getJson("/api/stock/levels?search=" + name, token)
                .andExpect(jsonPath("$.content[0].physical").value(6.0));
    }

    // ----- Rules -----

    @Test
    @DisplayName("only an issued invoice can be credited: not a draft, a cancelled invoice, or another document")
    void whatCanBeCredited() throws Exception {
        long product = good();
        long draft = number(postJson(DOCUMENTS, token, """
                {"type":"INVOICE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":10}]}
                """.formatted(customerId, mainWarehouse, product)).andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + draft + "/convert-to-credit-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only an issued invoice")));

        long cancelled = issuedInvoice(product, 1);
        postJson(DOCUMENTS + "/" + cancelled + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + cancelled + "/convert-to-credit-note", token, "")
                .andExpect(status().isUnprocessableEntity());

        long order = number(postJson(DOCUMENTS, token, """
                {"type":"SALES_ORDER","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":10}]}
                """.formatted(customerId, product)).andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + order + "/convert-to-credit-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only an invoice can be credited")));
    }

    @Test
    @DisplayName("a credit note is not created on its own, and stays with the customer of its invoice")
    void creditNoteIsBoundToItsInvoice() throws Exception {
        long product = good();
        postJson(DOCUMENTS, token, creditJson(customerId, product, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("made from an invoice")));

        long otherCustomer = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Another client"}
                """).andReturn().getResponse().getContentAsString(), "$.id");
        long invoice = issuedInvoice(product, 1);
        long note = creditNoteFor(invoice, product, 1);

        putJson(DOCUMENTS + "/" + note, token, creditJson(otherCustomer, product, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("customer of its invoice")));
    }

    @Test
    @DisplayName("a credit note is issued and cancelled, never moved to another status by hand")
    void statusIsNotChangedByHand() throws Exception {
        long product = good();
        long invoice = issuedInvoice(product, 1);
        long note = creditNoteFor(invoice, product, 1);
        issue(note);

        postJson(DOCUMENTS + "/" + note + "/status", token, "{\"status\":\"PAID\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    // ----- Permissions -----

    @Test
    @DisplayName("a sales agent can prepare a credit note but not issue or cancel it")
    void permissions() throws Exception {
        String email = uniqueEmail("agent");
        createUser(token, email, roleId(token, "SALES_AGENT"));
        String agent = login(email, "Password123");
        long product = good();
        long invoice = issuedInvoice(product, 2);

        long note = number(postJson(DOCUMENTS + "/" + invoice + "/convert-to-credit-note", agent, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + note + "/issue", agent, "").andExpect(status().isForbidden());

        issue(note);
        postJson(DOCUMENTS + "/" + note + "/cancel", agent, "").andExpect(status().isForbidden());
    }
}
