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
 * Full stack for the purchase invoice - the mirror of {@code InvoiceIntegrationTest}: what validating one does
 * to the stock (nothing after a goods receipt, the goods come in otherwise), how the payments to the supplier
 * move its status, and the rules that tie it to the documents it was made from. The price of a line is the
 * purchase price of the product, 30.000, so an invoice of q pieces asks for 30 x q.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PurchaseInvoiceIntegrationTest extends IntegrationTest {

    private static final String DOCUMENTS = "/api/purchase-documents";
    private static final String PAYMENTS = "/api/supplier-payments";

    private String token;
    private long supplierId;
    private long mainWarehouse;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Purchase Invoice Test Co");
        mainWarehouse = number(getJson("/api/warehouses", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        supplierId = number(postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Fournisseur Facture"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    private String uniqueName() {
        return "Buy-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long good(String name) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"%s","kind":"GOOD","purpose":"BOTH","unit":"PIECE","purchasePrice":30,"salePrice":50,
                 "allowNegativeStock":true}
                """.formatted(name)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private long good() throws Exception {
        return good(uniqueName());
    }

    private String documentJson(String type, long productId, int quantity) {
        return """
                {"type":"%s","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d}]}
                """.formatted(type, supplierId, mainWarehouse, productId, quantity);
    }

    private long create(String type, long productId, int quantity) throws Exception {
        return number(postJson(DOCUMENTS, token, documentJson(type, productId, quantity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void validate(long id) throws Exception {
        postJson(DOCUMENTS + "/" + id + "/validate", token, "").andExpect(status().isOk());
    }

    private long validated(String type, long productId, int quantity) throws Exception {
        long id = create(type, productId, quantity);
        validate(id);
        return id;
    }

    private long invoiceFrom(long source) throws Exception {
        return number(postJson(DOCUMENTS + "/" + source + "/convert-to-invoice", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PURCHASE_INVOICE"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceId").value(source))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private long validatedInvoice(long productId, int quantity) throws Exception {
        return validated("PURCHASE_INVOICE", productId, quantity);
    }

    private void expectPhysical(String name, String physical) throws Exception {
        getJson("/api/stock/levels?search=" + name, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].physical").value(Double.parseDouble(physical)));
    }

    private long pay(long invoice, String amount) throws Exception {
        return number(postJson(PAYMENTS, token, paymentJson(invoice, amount))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String paymentJson(long invoice, String amount) {
        return """
                {"invoiceId":%d,"amount":%s,"paymentDate":"2026-09-21","method":"BANK_TRANSFER","reference":"VIR-1"}
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
    @DisplayName("validating an invoice made by hand brings its goods into the stock, and cancelling it takes them out again")
    void standaloneInvoiceMovesStock() throws Exception {
        String name = uniqueName();
        long product = good(name);

        long invoice = create("PURCHASE_INVOICE", product, 4);
        getJson("/api/stock/levels?search=" + name, token).andExpect(jsonPath("$.content[0].physical").value(0.0)); // a draft moves nothing
        validate(invoice);
        expectPhysical(name, "4");
        getJson(DOCUMENTS + "/" + invoice, token)
                .andExpect(jsonPath("$.reference").value(containsString("PINV")))
                .andExpect(jsonPath("$.warehouseName").value("Default warehouse"))
                .andExpect(jsonPath("$.total").value(120.0));

        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectPhysical(name, "0");
    }

    @Test
    @DisplayName("an invoice made from a purchase order brings the goods in: the order itself moves no stock")
    void invoiceFromOrderBringsGoodsIn() throws Exception {
        String name = uniqueName();
        long product = good(name);
        long order = validated("PURCHASE_ORDER", product, 5);

        long invoice = invoiceFrom(order);
        validate(invoice);

        expectPhysical(name, "5");
    }

    @Test
    @DisplayName("an invoice made from a goods receipt moves no stock: the receipt already brought the goods in")
    void invoiceFromReceiptMovesNothing() throws Exception {
        String name = uniqueName();
        long product = good(name);
        long receipt = validated("GOODS_RECEIPT", product, 4);
        expectPhysical(name, "4");

        long invoice = invoiceFrom(receipt);
        validate(invoice);
        expectPhysical(name, "4");

        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
        expectPhysical(name, "4");
    }

    @Test
    @DisplayName("an invoice cannot be cancelled once its goods have been sold and the product forbids negative stock")
    void cancelRefusedWhenGoodsAreGone() throws Exception {
        String name = uniqueName();
        long strict = number(postJson("/api/products", token, """
                {"name":"%s","kind":"GOOD","purpose":"BOTH","unit":"PIECE","purchasePrice":30,"salePrice":50,
                 "allowNegativeStock":false}
                """.formatted(name)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        long invoice = validatedInvoice(strict, 3);
        postJson("/api/stock/movements", token, """
                {"type":"EXIT","productId":%d,"warehouseId":%d,"quantity":3,"reason":"sold"}
                """.formatted(strict, mainWarehouse)).andExpect(status().isCreated());

        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isUnprocessableEntity());

        getJson(DOCUMENTS + "/" + invoice, token).andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    // ----- Payments -----

    @Test
    @DisplayName("payments move an invoice from unpaid to partly paid to paid, and cancelling one moves it back")
    void paymentsMoveTheStatus() throws Exception {
        long invoice = validatedInvoice(good(), 5); // 150.000 to pay
        expectInvoice(invoice, "VALIDATED", "0", "150");

        long first = pay(invoice, "50.000");
        expectInvoice(invoice, "PARTIALLY_PAID", "50", "100");

        long second = pay(invoice, "100.000");
        expectInvoice(invoice, "PAID", "150", "0");

        postJson(PAYMENTS + "/" + second + "/cancel", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectInvoice(invoice, "PARTIALLY_PAID", "50", "100");

        postJson(PAYMENTS + "/" + first + "/cancel", token, "").andExpect(status().isOk());
        expectInvoice(invoice, "VALIDATED", "0", "150");
    }

    @Test
    @DisplayName("a payment cannot exceed what is still due, and a paid invoice takes no more")
    void noOverpayment() throws Exception {
        long invoice = validatedInvoice(good(), 1); // 30.000

        postJson(PAYMENTS, token, paymentJson(invoice, "30.001"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("more than the 30.000 still due")));

        pay(invoice, "10.000");
        postJson(PAYMENTS, token, paymentJson(invoice, "20.001")).andExpect(status().isUnprocessableEntity());
        pay(invoice, "20.000");

        postJson(PAYMENTS, token, paymentJson(invoice, "0.001"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("paid in full")));
    }

    @Test
    @DisplayName("only a validated invoice can be paid: not a draft, a cancelled invoice, or another document")
    void onlyValidatedInvoicesArePaid() throws Exception {
        long product = good();
        long draft = create("PURCHASE_INVOICE", product, 1);
        postJson(PAYMENTS, token, paymentJson(draft, "1")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Validate the invoice")));

        long cancelled = validatedInvoice(product, 1);
        postJson(DOCUMENTS + "/" + cancelled + "/cancel", token, "").andExpect(status().isOk());
        postJson(PAYMENTS, token, paymentJson(cancelled, "1")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("cancelled")));

        long order = create("PURCHASE_ORDER", product, 1);
        postJson(PAYMENTS, token, paymentJson(order, "1")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only an invoice")));

        postJson(PAYMENTS, token, paymentJson(999999, "1")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a payment needs a positive amount, a date and a method")
    void paymentValidation() throws Exception {
        long invoice = validatedInvoice(good(), 1);

        postJson(PAYMENTS, token, paymentJson(invoice, "0")).andExpect(status().isBadRequest());
        postJson(PAYMENTS, token, paymentJson(invoice, "-5")).andExpect(status().isBadRequest());
        postJson(PAYMENTS, token, paymentJson(invoice, "1.0001")).andExpect(status().isBadRequest());
        postJson(PAYMENTS, token, "{\"invoiceId\":" + invoice + ",\"amount\":1}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an invoice with a payment cannot be cancelled until the payment is; a payment is cancelled once")
    void invoiceWithPaymentCannotBeCancelled() throws Exception {
        long invoice = validatedInvoice(good(), 2);
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
    @DisplayName("the payments of an invoice are listed, cancelled ones included")
    void paymentsAreListed() throws Exception {
        long invoice = validatedInvoice(good(), 3);
        long first = pay(invoice, "10.000");
        pay(invoice, "5.000");
        postJson(PAYMENTS + "/" + first + "/cancel", token, "").andExpect(status().isOk());

        getJson(PAYMENTS + "?invoiceId=" + invoice, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].invoiceReference").value(containsString("PINV")))
                .andExpect(jsonPath("$.content[0].supplierName").value("Fournisseur Facture"))
                .andExpect(jsonPath("$.content[0].method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.content[0].reference").value("VIR-1"));
    }

    @Test
    @DisplayName("the invoice list can be filtered by payment status")
    void listByPaymentStatus() throws Exception {
        long product = good();
        long paid = validatedInvoice(product, 1);
        pay(paid, "30.000");
        long partly = validatedInvoice(product, 2);
        pay(partly, "1.000");

        getJson(DOCUMENTS + "?type=PURCHASE_INVOICE&status=PAID&size=100", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + paid + ")].paidAmount").value(30.0));
        getJson(DOCUMENTS + "?type=PURCHASE_INVOICE&status=PARTIALLY_PAID&size=100", token)
                .andExpect(jsonPath("$.content[?(@.id == " + partly + ")].balance").value(59.0))
                .andExpect(jsonPath("$.content[?(@.id == " + paid + ")]", hasSize(0)));
    }

    // ----- Links between documents -----

    @Test
    @DisplayName("an invoiced order cannot be cancelled or received; once the invoice is cancelled it can")
    void invoicedOrder() throws Exception {
        long product = good();
        long order = validated("PURCHASE_ORDER", product, 2);
        long invoice = invoiceFrom(order);

        postJson(DOCUMENTS + "/" + order + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has an invoice")));
        postJson(DOCUMENTS + "/" + order + "/convert-to-receipt", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already invoiced")));
        postJson(DOCUMENTS + "/" + order + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already invoiced")));

        validate(invoice);
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + order + "/cancel", token, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("an order received in parts is invoiced through its receipts, one invoice per receipt")
    void invoiceThroughReceipts() throws Exception {
        long product = good();
        long order = validated("PURCHASE_ORDER", product, 5);
        long receipt = number(postJson(DOCUMENTS + "/" + order + "/convert-to-receipt", token, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        postJson(DOCUMENTS + "/" + order + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("invoice them instead")));

        // A draft receipt cannot be invoiced yet
        postJson(DOCUMENTS + "/" + receipt + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("validated goods receipt")));

        validate(receipt);
        long invoice = invoiceFrom(receipt);
        postJson(DOCUMENTS + "/" + receipt + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already invoiced")));

        // The receipt cannot be cancelled under its invoice
        postJson(DOCUMENTS + "/" + receipt + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has an invoice")));

        validate(invoice);
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + receipt + "/cancel", token, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("an invoice needs a warehouse, and only a validated order or receipt can be turned into one")
    void invoiceRules() throws Exception {
        long product = good();
        postJson(DOCUMENTS, token, """
                {"type":"PURCHASE_INVOICE","supplierId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1}]}
                """.formatted(supplierId, product))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("A purchase invoice needs a warehouse")));

        long draftOrder = create("PURCHASE_ORDER", product, 1);
        postJson(DOCUMENTS + "/" + draftOrder + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Validate the purchase order")));

        long invoice = validatedInvoice(product, 1);
        postJson(DOCUMENTS + "/" + invoice + "/convert-to-invoice", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("cannot be turned into an invoice")));
    }

    // ----- Permissions -----

    @Test
    @DisplayName("an accountant can read invoices and payments but not create, pay or cancel")
    void permissions() throws Exception {
        String email = uniqueEmail("accountant");
        createUser(token, email, roleId(token, "ACCOUNTANT"));
        String accountant = login(email, "Password123");
        long product = good();
        long order = validated("PURCHASE_ORDER", product, 1);
        long invoice = validatedInvoice(product, 1);

        getJson(DOCUMENTS + "/" + invoice, accountant).andExpect(status().isOk());
        getJson(PAYMENTS + "?invoiceId=" + invoice, accountant).andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + order + "/convert-to-invoice", accountant, "").andExpect(status().isForbidden());
        postJson(PAYMENTS, accountant, paymentJson(invoice, "1")).andExpect(status().isForbidden());

        long payment = pay(invoice, "1.000");
        postJson(PAYMENTS + "/" + payment + "/cancel", accountant, "").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous caller is refused")
    void anonymousRefused() throws Exception {
        getJson(PAYMENTS, null).andExpect(status().isUnauthorized());
        postJson(PAYMENTS, null, "{}").andExpect(status().isUnauthorized());
    }
}
