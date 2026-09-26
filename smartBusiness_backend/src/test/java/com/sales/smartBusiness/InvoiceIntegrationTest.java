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
 * Full stack for the invoice: what issuing one does to the stock (nothing after a delivery note, the
 * goods leave otherwise), how payments move its status, and the rules that tie it to the documents
 * it was made from. Every test builds its own product and documents, so none depends on another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceIntegrationTest extends IntegrationTest {

    private static final String DOCUMENTS = "/api/sales-documents";
    private static final String PAYMENTS = "/api/payments";

    private String token;
    private long mainWarehouse;
    private long customerId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Invoice Test Co");
        mainWarehouse = number(getJson("/api/warehouses", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        customerId = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Client Invoice"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    private String uniqueName() {
        return "Item-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long good(String name, boolean allowNegative) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"%s","kind":"GOOD","purpose":"SALE","unit":"PIECE","salePrice":10,"allowNegativeStock":%b}
                """.formatted(name, allowNegative))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void enter(long productId, String quantity) throws Exception {
        postJson("/api/stock/movements", token, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":%s,"reason":"test"}
                """.formatted(productId, mainWarehouse, quantity)).andExpect(status().isCreated());
    }

    private void expectLevels(String name, String physical, String reserved, String available) throws Exception {
        getJson("/api/stock/levels?search=" + name, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].physical").value(Double.parseDouble(physical)))
                .andExpect(jsonPath("$.content[0].reserved").value(Double.parseDouble(reserved)))
                .andExpect(jsonPath("$.content[0].available").value(Double.parseDouble(available)));
    }

    private String documentJson(String type, long productId, int quantity) {
        return """
                {"type":"%s","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d,"unitPrice":10}]}
                """.formatted(type, customerId, mainWarehouse, productId, quantity);
    }

    private long create(String type, long productId, int quantity) throws Exception {
        return number(postJson(DOCUMENTS, token, documentJson(type, productId, quantity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void issue(long id) throws Exception {
        postJson(DOCUMENTS + "/" + id + "/issue", token, "").andExpect(status().isOk());
    }

    private void setStatus(long id, String status) throws Exception {
        postJson(DOCUMENTS + "/" + id + "/status", token, "{\"status\":\"" + status + "\"}").andExpect(status().isOk());
    }

    private long confirmedOrder(long productId, int quantity) throws Exception {
        long order = create("SALES_ORDER", productId, quantity);
        issue(order);
        setStatus(order, "CONFIRMED");
        return order;
    }

    private long invoiceFrom(long source) throws Exception {
        return number(postJson(DOCUMENTS + "/" + source + "/convert-to-invoice", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INVOICE"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceId").value(source))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private long issuedInvoice(long productId, int quantity) throws Exception {
        long invoice = create("INVOICE", productId, quantity);
        issue(invoice);
        return invoice;
    }

    /** A delivered note for the quantity: the goods have left the stock. */
    private long deliveredNote(long productId, int quantity) throws Exception {
        long note = create("DELIVERY_NOTE", productId, quantity);
        issue(note);
        setStatus(note, "DELIVERED");
        return note;
    }

    private long pay(long invoice, String amount) throws Exception {
        return number(postJson(PAYMENTS, token, paymentJson(invoice, amount))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String paymentJson(long invoice, String amount) {
        return """
                {"invoiceId":%d,"amount":%s,"paymentDate":"2026-09-21","method":"CHECK","reference":"CHQ-1"}
                """.formatted(invoice, amount);
    }

    private void expectInvoice(long invoice, String status, String paid, String balance) throws Exception {
        getJson(DOCUMENTS + "/" + invoice, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.paidAmount").value(Double.parseDouble(paid)))
                .andExpect(jsonPath("$.balance").value(Double.parseDouble(balance)));
    }

    // ----- Stock -----

    @Test
    @DisplayName("issuing an invoice made by hand takes its goods out of the stock, and cancelling it puts them back")
    void standaloneInvoiceMovesStock() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        enter(product, "10");

        long invoice = create("INVOICE", product, 4);
        expectLevels(name, "10", "0", "10"); // a draft moves nothing
        issue(invoice);
        expectLevels(name, "6", "0", "6");
        getJson(DOCUMENTS + "/" + invoice, token)
                .andExpect(jsonPath("$.reference").value(containsString("INV")))
                .andExpect(jsonPath("$.warehouseName").value("Default warehouse"));

        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectLevels(name, "10", "0", "10");
    }

    @Test
    @DisplayName("an invoice the stock cannot cover is refused and stays a draft, without a number")
    void invoiceRefusedByStock() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        enter(product, "2");
        long invoice = create("INVOICE", product, 3);

        postJson(DOCUMENTS + "/" + invoice + "/issue", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("2 available")));

        getJson(DOCUMENTS + "/" + invoice, token)
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").doesNotExist());
        expectLevels(name, "2", "0", "2");
    }

    @Test
    @DisplayName("an invoice made from an order sells what the order reserved: the reservation goes, the goods leave, what is left to sell stays")
    void invoiceFromOrderConsumesTheReservation() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        enter(product, "10");
        long order = confirmedOrder(product, 6);
        expectLevels(name, "10", "6", "4");

        long invoice = invoiceFrom(order);
        issue(invoice);
        expectLevels(name, "4", "0", "4");

        // Cancelled, the goods come back and the order that still stands promises them again
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
        expectLevels(name, "10", "6", "4");
    }

    @Test
    @DisplayName("an invoice made from a delivered note moves no stock: the note already took the goods out")
    void invoiceFromDeliveryNoteMovesNothing() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        enter(product, "10");
        long note = deliveredNote(product, 4);
        expectLevels(name, "6", "0", "6");

        long invoice = invoiceFrom(note);
        getJson(DOCUMENTS + "/" + invoice, token).andExpect(jsonPath("$.warehouseName").value("Default warehouse"));
        issue(invoice);
        expectLevels(name, "6", "0", "6");

        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
        expectLevels(name, "6", "0", "6");
    }

    // ----- Payments -----

    @Test
    @DisplayName("payments move an invoice from unpaid to partly paid to paid, and cancelling one moves it back")
    void paymentsMoveTheStatus() throws Exception {
        long invoice = issuedInvoice(good(uniqueName(), true), 5); // 50.000 to pay
        expectInvoice(invoice, "ISSUED", "0", "50");

        long first = pay(invoice, "20.000");
        expectInvoice(invoice, "PARTIALLY_PAID", "20", "30");

        long second = pay(invoice, "30.000");
        expectInvoice(invoice, "PAID", "50", "0");

        postJson(PAYMENTS + "/" + second + "/cancel", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectInvoice(invoice, "PARTIALLY_PAID", "20", "30");

        postJson(PAYMENTS + "/" + first + "/cancel", token, "").andExpect(status().isOk());
        expectInvoice(invoice, "ISSUED", "0", "50");
    }

    @Test
    @DisplayName("a payment cannot exceed what is still due, and a paid invoice takes no more")
    void noOverpayment() throws Exception {
        long invoice = issuedInvoice(good(uniqueName(), true), 1); // 10.000

        postJson(PAYMENTS, token, paymentJson(invoice, "10.001"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("more than the 10.000 still due")));

        pay(invoice, "4.000");
        postJson(PAYMENTS, token, paymentJson(invoice, "6.001")).andExpect(status().isUnprocessableEntity());
        pay(invoice, "6.000");

        postJson(PAYMENTS, token, paymentJson(invoice, "0.001"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("paid in full")));
    }

    @Test
    @DisplayName("only an issued invoice can be paid: not a draft, a cancelled invoice, or another document")
    void onlyIssuedInvoicesArePaid() throws Exception {
        long product = good(uniqueName(), true);
        long draft = create("INVOICE", product, 1);
        postJson(PAYMENTS, token, paymentJson(draft, "1")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Issue the invoice")));

        long cancelled = issuedInvoice(product, 1);
        postJson(DOCUMENTS + "/" + cancelled + "/cancel", token, "").andExpect(status().isOk());
        postJson(PAYMENTS, token, paymentJson(cancelled, "1")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("cancelled")));

        long order = create("SALES_ORDER", product, 1);
        postJson(PAYMENTS, token, paymentJson(order, "1")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only an invoice")));

        postJson(PAYMENTS, token, paymentJson(999999, "1")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a payment needs a positive amount, a date and a method")
    void paymentValidation() throws Exception {
        long invoice = issuedInvoice(good(uniqueName(), true), 1);

        postJson(PAYMENTS, token, paymentJson(invoice, "0")).andExpect(status().isBadRequest());
        postJson(PAYMENTS, token, paymentJson(invoice, "-5")).andExpect(status().isBadRequest());
        postJson(PAYMENTS, token, paymentJson(invoice, "1.0001")).andExpect(status().isBadRequest());
        postJson(PAYMENTS, token, "{\"invoiceId\":" + invoice + ",\"amount\":1}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an invoice with a payment cannot be cancelled until the payment is; a payment is cancelled once")
    void invoiceWithPaymentCannotBeCancelled() throws Exception {
        long invoice = issuedInvoice(good(uniqueName(), true), 2);
        long payment = pay(invoice, "5.000");

        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has payments")));

        postJson(PAYMENTS + "/" + payment + "/cancel", token, "").andExpect(status().isOk());
        postJson(PAYMENTS + "/" + payment + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already cancelled")));

        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("the payments of an invoice are listed, cancelled ones included, newest date first")
    void paymentsAreListed() throws Exception {
        long invoice = issuedInvoice(good(uniqueName(), true), 3);
        long first = pay(invoice, "10.000");
        pay(invoice, "5.000");
        postJson(PAYMENTS + "/" + first + "/cancel", token, "").andExpect(status().isOk());

        getJson(PAYMENTS + "?invoiceId=" + invoice, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].invoiceReference").value(containsString("INV")))
                .andExpect(jsonPath("$.content[0].customerName").value("Client Invoice"))
                .andExpect(jsonPath("$.content[0].method").value("CHECK"))
                .andExpect(jsonPath("$.content[0].reference").value("CHQ-1"));
    }

    @Test
    @DisplayName("the status of an invoice cannot be changed by hand")
    void invoiceStatusIsNotChangedByHand() throws Exception {
        long invoice = issuedInvoice(good(uniqueName(), true), 1);

        postJson(DOCUMENTS + "/" + invoice + "/status", token, "{\"status\":\"PAID\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("paid by its payments")));
    }

    // ----- Links between documents -----

    @Test
    @DisplayName("an invoiced order cannot be cancelled or delivered by note; once the invoice is cancelled it can")
    void invoicedOrder() throws Exception {
        long product = good(uniqueName(), true);
        long order = confirmedOrder(product, 2);
        long invoice = invoiceFrom(order);

        postJson(DOCUMENTS + "/" + order + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has an invoice")));
        postJson(DOCUMENTS + "/" + order + "/convert-to-delivery-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already invoiced")));
        postJson(DOCUMENTS + "/" + order + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already invoiced")));

        // A draft invoice already counts: it is the invoice of that order until it is cancelled
        issue(invoice);
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + order + "/cancel", token, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("an order that went out on delivery notes is invoiced through them, one invoice per note")
    void invoiceThroughDeliveryNotes() throws Exception {
        long product = good(uniqueName(), true);
        long order = confirmedOrder(product, 5);
        long note = number(postJson(DOCUMENTS + "/" + order + "/convert-to-delivery-note", token, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        postJson(DOCUMENTS + "/" + order + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("invoice them instead")));

        // An undelivered note cannot be invoiced yet
        issue(note);
        postJson(DOCUMENTS + "/" + note + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("delivered delivery note")));

        setStatus(note, "DELIVERED");
        long invoice = invoiceFrom(note);
        postJson(DOCUMENTS + "/" + note + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already invoiced")));

        // The note cannot be cancelled under its invoice
        postJson(DOCUMENTS + "/" + note + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has an invoice")));

        issue(invoice);
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + note + "/cancel", token, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("an accepted quote can be invoiced directly, once; a quote with an order cannot")
    void invoiceFromQuote() throws Exception {
        long product = good(uniqueName(), true);
        long quote = create("QUOTE", product, 1);
        postJson(DOCUMENTS + "/" + quote + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("issued or accepted")));

        issue(quote);
        invoiceFrom(quote);
        postJson(DOCUMENTS + "/" + quote + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already has a sales order or an invoice")));
        postJson(DOCUMENTS + "/" + quote + "/convert-to-order", token, "").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("an invoice needs a warehouse, and only quotes, orders and delivered notes can be turned into one")
    void invoiceRules() throws Exception {
        long product = good(uniqueName(), true);
        postJson(DOCUMENTS, token, """
                {"type":"INVOICE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":10}]}
                """.formatted(customerId, product))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("An invoice needs a warehouse")));

        long invoice = issuedInvoice(product, 1);
        postJson(DOCUMENTS + "/" + invoice + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("An invoice cannot be turned into an invoice")));
    }

    @Test
    @DisplayName("the invoice list can be filtered by payment status")
    void listByPaymentStatus() throws Exception {
        long product = good(uniqueName(), true);
        long paid = issuedInvoice(product, 1);
        pay(paid, "10.000");
        long partly = issuedInvoice(product, 2);
        pay(partly, "1.000");

        getJson(DOCUMENTS + "?type=INVOICE&status=PAID&size=100", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + paid + ")].paidAmount").value(10.0));
        getJson(DOCUMENTS + "?type=INVOICE&status=PARTIALLY_PAID&size=100", token)
                .andExpect(jsonPath("$.content[?(@.id == " + partly + ")].balance").value(19.0))
                .andExpect(jsonPath("$.content[?(@.id == " + paid + ")]", hasSize(0)));
    }

    // ----- Permissions -----

    @Test
    @DisplayName("a sales agent can prepare an invoice but not issue it, take a payment or cancel one")
    void permissions() throws Exception {
        String email = uniqueEmail("agent");
        createUser(token, email, roleId(token, "SALES_AGENT"));
        String agent = login(email, "Password123");
        long product = good(uniqueName(), true);
        long order = confirmedOrder(product, 1);

        long invoice = number(postJson(DOCUMENTS + "/" + order + "/convert-to-invoice", agent, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + invoice + "/issue", agent, "").andExpect(status().isForbidden());

        issue(invoice);
        getJson(PAYMENTS + "?invoiceId=" + invoice, agent).andExpect(status().isOk());
        postJson(PAYMENTS, agent, paymentJson(invoice, "1")).andExpect(status().isForbidden());

        long payment = pay(invoice, "1.000");
        postJson(PAYMENTS + "/" + payment + "/cancel", agent, "").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous caller is refused")
    void anonymousRefused() throws Exception {
        getJson(PAYMENTS, null).andExpect(status().isUnauthorized());
        postJson(PAYMENTS, null, "{}").andExpect(status().isUnauthorized());
    }
}
