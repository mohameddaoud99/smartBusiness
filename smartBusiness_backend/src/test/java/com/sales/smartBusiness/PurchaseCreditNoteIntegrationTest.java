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
 * Full stack for the supplier credit note - the mirror of {@code CreditNoteIntegrationTest}: made from a purchase
 * invoice, lowered while a draft, then validated against that invoice - lowering what it asks us to pay, together
 * with the payments - and cancelled to give it back. The price of a line is the purchase price of the product,
 * 30.000, so an invoice of q pieces asks for 30 x q.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PurchaseCreditNoteIntegrationTest extends IntegrationTest {

    private static final String DOCUMENTS = "/api/purchase-documents";
    private static final String PAYMENTS = "/api/supplier-payments";

    private String token;
    private long mainWarehouse;
    private long supplierId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Supplier Credit Note Test Co");
        mainWarehouse = number(getJson("/api/warehouses", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        supplierId = number(postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Fournisseur Avoir"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    private long good(String name) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"%s","kind":"GOOD","purpose":"BOTH","unit":"PIECE","purchasePrice":30,"salePrice":50,
                 "allowNegativeStock":true}
                """.formatted(name)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private long good() throws Exception {
        return good("Buy-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /** A validated invoice of `quantity` pieces at 30.000: it asks for 30 x quantity. */
    private long validatedInvoice(long productId, int quantity) throws Exception {
        long invoice = number(postJson(DOCUMENTS, token, """
                {"type":"PURCHASE_INVOICE","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d}]}
                """.formatted(supplierId, mainWarehouse, productId, quantity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        validate(invoice);
        return invoice;
    }

    /** A draft credit note made from the invoice, then lowered to the quantity really credited. */
    private long creditNoteFor(long invoice, long productId, int quantity) throws Exception {
        long note = number(postJson(DOCUMENTS + "/" + invoice + "/convert-to-credit-note", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PURCHASE_CREDIT_NOTE"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceId").value(invoice))
                .andExpect(jsonPath("$.sourceType").value("PURCHASE_INVOICE"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        putJson(DOCUMENTS + "/" + note, token, creditJson(supplierId, productId, quantity)).andExpect(status().isOk());
        return note;
    }

    private String creditJson(long supplier, long productId, int quantity) {
        return """
                {"type":"PURCHASE_CREDIT_NOTE","supplierId":%d,"issueDate":"2026-09-21",
                 "lines":[{"productId":%d,"quantity":%d}]}
                """.formatted(supplier, productId, quantity);
    }

    private void validate(long id) throws Exception {
        postJson(DOCUMENTS + "/" + id + "/validate", token, "").andExpect(status().isOk());
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
    @DisplayName("a credit note is made from an invoice, lowered to what is credited, and once validated takes that off the invoice")
    void partialCredit() throws Exception {
        long product = good();
        long invoice = validatedInvoice(product, 5); // 150.000
        long note = creditNoteFor(invoice, product, 2); // 60.000

        expectInvoice(invoice, "VALIDATED", "0", "0", "150"); // a draft credit note moves nothing
        postJson(DOCUMENTS + "/" + note + "/validate", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.reference").value(containsString("PCN")))
                .andExpect(jsonPath("$.total").value(60.0));

        expectInvoice(invoice, "PARTIALLY_PAID", "60", "0", "90");
        getJson(DOCUMENTS + "/" + invoice, token)
                .andExpect(jsonPath("$.derived", hasSize(1)))
                .andExpect(jsonPath("$.derived[0].type").value("PURCHASE_CREDIT_NOTE"));
    }

    @Test
    @DisplayName("payments are capped by what is left after the credit, and together they settle the invoice")
    void creditAndPayments() throws Exception {
        long product = good();
        long invoice = validatedInvoice(product, 5);
        validate(creditNoteFor(invoice, product, 2));

        postJson(PAYMENTS, token, """
                {"invoiceId":%d,"amount":90.001,"paymentDate":"2026-09-21","method":"CASH"}
                """.formatted(invoice)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("more than the 90.000 still due")));

        pay(invoice, "90.000");
        expectInvoice(invoice, "PAID", "60", "90", "0");
    }

    @Test
    @DisplayName("credit notes cannot take more off an invoice than it is worth, and the refused one stays a draft")
    void creditCannotExceedTheInvoice() throws Exception {
        long product = good();
        long invoice = validatedInvoice(product, 5); // 150
        validate(creditNoteFor(invoice, product, 3)); // 90 credited

        long tooMuch = creditNoteFor(invoice, product, 3); // 90 more
        postJson(DOCUMENTS + "/" + tooMuch + "/validate", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("more than the 60.000 that can still be credited")));

        getJson(DOCUMENTS + "/" + tooMuch, token)
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").doesNotExist());
        expectInvoice(invoice, "PARTIALLY_PAID", "90", "0", "60");

        putJson(DOCUMENTS + "/" + tooMuch, token, creditJson(supplierId, product, 2)).andExpect(status().isOk());
        validate(tooMuch);
        expectInvoice(invoice, "PAID", "150", "0", "0");
    }

    @Test
    @DisplayName("an invoice credited in full is settled, and cannot be credited again")
    void creditedInFull() throws Exception {
        long product = good();
        long invoice = validatedInvoice(product, 2);
        validate(creditNoteFor(invoice, product, 2));

        expectInvoice(invoice, "PAID", "60", "0", "0");
        postJson(DOCUMENTS + "/" + invoice + "/convert-to-credit-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("credited in full")));
    }

    @Test
    @DisplayName("credited after being paid in full, an invoice shows what the supplier owes us as a negative balance")
    void creditOfAPaidInvoice() throws Exception {
        long product = good();
        long invoice = validatedInvoice(product, 1); // 30
        pay(invoice, "30.000");
        expectInvoice(invoice, "PAID", "0", "30", "0");

        validate(creditNoteFor(invoice, product, 1)); // the whole invoice credited after it was paid
        expectInvoice(invoice, "PAID", "30", "30", "-30");
    }

    @Test
    @DisplayName("cancelling a credit note gives the invoice back what it took off")
    void cancelCreditNote() throws Exception {
        long product = good();
        long invoice = validatedInvoice(product, 5);
        long note = creditNoteFor(invoice, product, 2);
        validate(note);
        expectInvoice(invoice, "PARTIALLY_PAID", "60", "0", "90");

        postJson(DOCUMENTS + "/" + note + "/cancel", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        expectInvoice(invoice, "VALIDATED", "0", "0", "150");
        // and it can be credited again
        postJson(DOCUMENTS + "/" + invoice + "/convert-to-credit-note", token, "").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an invoice with a credit note cannot be cancelled until the credit note is")
    void invoiceWithCreditNoteCannotBeCancelled() throws Exception {
        long product = good();
        long invoice = validatedInvoice(product, 3);
        long note = creditNoteFor(invoice, product, 1);

        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has credit notes")));

        validate(note);
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
        String name = "Buy-" + UUID.randomUUID().toString().substring(0, 8);
        long product = good(name);
        long invoice = validatedInvoice(product, 4); // brings 4 pieces in

        validate(creditNoteFor(invoice, product, 4));

        getJson("/api/stock/levels?search=" + name, token)
                .andExpect(jsonPath("$.content[0].physical").value(4.0));
    }

    // ----- Rules -----

    @Test
    @DisplayName("only a validated invoice can be credited: not a draft, a cancelled invoice, or another document")
    void whatCanBeCredited() throws Exception {
        long product = good();
        long draft = number(postJson(DOCUMENTS, token, """
                {"type":"PURCHASE_INVOICE","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1}]}
                """.formatted(supplierId, mainWarehouse, product)).andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + draft + "/convert-to-credit-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only a validated invoice")));

        long cancelled = validatedInvoice(product, 1);
        postJson(DOCUMENTS + "/" + cancelled + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + cancelled + "/convert-to-credit-note", token, "")
                .andExpect(status().isUnprocessableEntity());

        long order = number(postJson(DOCUMENTS, token, """
                {"type":"PURCHASE_ORDER","supplierId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1}]}
                """.formatted(supplierId, product)).andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + order + "/convert-to-credit-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only an invoice can be credited")));
    }

    @Test
    @DisplayName("a credit note is not created on its own, and stays with the supplier of its invoice")
    void creditNoteIsBoundToItsInvoice() throws Exception {
        long product = good();
        postJson(DOCUMENTS, token, creditJson(supplierId, product, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("made from an invoice")));

        long otherSupplier = number(postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Another supplier"}
                """).andReturn().getResponse().getContentAsString(), "$.id");
        long invoice = validatedInvoice(product, 1);
        long note = creditNoteFor(invoice, product, 1);

        putJson(DOCUMENTS + "/" + note, token, creditJson(otherSupplier, product, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("supplier of its invoice")));
    }

    // ----- Permissions -----

    @Test
    @DisplayName("an accountant can read credit notes but not create, validate or cancel one")
    void permissions() throws Exception {
        String email = uniqueEmail("accountant");
        createUser(token, email, roleId(token, "ACCOUNTANT"));
        String accountant = login(email, "Password123");
        long product = good();
        long invoice = validatedInvoice(product, 2);

        postJson(DOCUMENTS + "/" + invoice + "/convert-to-credit-note", accountant, "").andExpect(status().isForbidden());

        long note = creditNoteFor(invoice, product, 1);
        getJson(DOCUMENTS + "/" + note, accountant).andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + note + "/validate", accountant, "").andExpect(status().isForbidden());

        validate(note);
        postJson(DOCUMENTS + "/" + note + "/cancel", accountant, "").andExpect(status().isForbidden());
    }
}
