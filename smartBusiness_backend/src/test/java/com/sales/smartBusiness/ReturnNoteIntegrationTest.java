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
 * Full stack for the customer return note: goods that come back. Issuing one puts them into the warehouse, cancelling
 * it takes them out again - refused when a product that forbids negative stock no longer has them. Every test builds
 * its own product and documents, so none depends on another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReturnNoteIntegrationTest extends IntegrationTest {

    private static final String DOCUMENTS = "/api/sales-documents";

    private String token;
    private long mainWarehouse;
    private long customerId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Return Note Test Co");
        mainWarehouse = number(getJson("/api/warehouses", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        customerId = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Client Retour"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    private String uniqueName() {
        return "Item-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long good(String name, boolean allowNegative) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"%s","kind":"GOOD","purpose":"SALE","unit":"PIECE","salePrice":10,"allowNegativeStock":%b}
                """.formatted(name, allowNegative)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void enter(long productId, String quantity) throws Exception {
        postJson("/api/stock/movements", token, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":%s,"reason":"test"}
                """.formatted(productId, mainWarehouse, quantity)).andExpect(status().isCreated());
    }

    private void exit(long productId, String quantity) throws Exception {
        postJson("/api/stock/movements", token, """
                {"type":"EXIT","productId":%d,"warehouseId":%d,"quantity":%s,"reason":"sold elsewhere"}
                """.formatted(productId, mainWarehouse, quantity)).andExpect(status().isCreated());
    }

    private void expectPhysical(String name, String physical) throws Exception {
        getJson("/api/stock/levels?search=" + name, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].physical").value(Double.parseDouble(physical)));
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

    private long deliveredNote(long productId, int quantity) throws Exception {
        long note = create("DELIVERY_NOTE", productId, quantity);
        issue(note);
        postJson(DOCUMENTS + "/" + note + "/status", token, "{\"status\":\"DELIVERED\"}").andExpect(status().isOk());
        return note;
    }

    private long issuedInvoice(long productId, int quantity) throws Exception {
        long invoice = create("INVOICE", productId, quantity);
        issue(invoice);
        return invoice;
    }

    /** A draft return note made from the document, then lowered to the quantity really coming back. */
    private long returnNoteFrom(long source, long productId, int quantity) throws Exception {
        long note = number(postJson(DOCUMENTS + "/" + source + "/convert-to-return-note", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RETURN_NOTE"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceId").value(source))
                .andReturn().getResponse().getContentAsString(), "$.id");
        putJson(DOCUMENTS + "/" + note, token, documentJson("RETURN_NOTE", productId, quantity)).andExpect(status().isOk());
        return note;
    }

    // ----- Stock -----

    @Test
    @DisplayName("a return note made from a delivered note puts the goods back, and cancelling it takes them out again")
    void returnFromDeliveryNote() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        enter(product, "10");
        long delivery = deliveredNote(product, 4);
        expectPhysical(name, "6");

        long note = returnNoteFrom(delivery, product, 3);
        expectPhysical(name, "6"); // a draft moves nothing
        postJson(DOCUMENTS + "/" + note + "/issue", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.reference").value(containsString("RN")));
        expectPhysical(name, "9");

        postJson(DOCUMENTS + "/" + note + "/cancel", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectPhysical(name, "6");
    }

    @Test
    @DisplayName("a return note made from an invoice puts the goods back too")
    void returnFromInvoice() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        enter(product, "10");
        long invoice = issuedInvoice(product, 4);
        expectPhysical(name, "6");

        issue(returnNoteFrom(invoice, product, 4));

        expectPhysical(name, "10");
    }

    @Test
    @DisplayName("a return note made by hand, for goods that came from elsewhere, works without any source")
    void standaloneReturnNote() throws Exception {
        String name = uniqueName();
        long product = good(name, true);

        long note = create("RETURN_NOTE", product, 5);
        getJson(DOCUMENTS + "/" + note, token)
                .andExpect(jsonPath("$.warehouseName").value("Default warehouse"))
                .andExpect(jsonPath("$.sourceId").doesNotExist());
        issue(note);

        expectPhysical(name, "5");
    }

    @Test
    @DisplayName("a return note whose goods were sold again cannot be cancelled when the product forbids negative stock")
    void cancelRefusedWhenGoodsAreGone() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        long note = create("RETURN_NOTE", product, 3);
        issue(note); // 3 in stock
        exit(product, "3"); // ... and gone again

        postJson(DOCUMENTS + "/" + note + "/cancel", token, "").andExpect(status().isUnprocessableEntity());

        getJson(DOCUMENTS + "/" + note, token).andExpect(jsonPath("$.status").value("ISSUED"));
        expectPhysical(name, "0");
    }

    @Test
    @DisplayName("a return note moves only goods: a service on its lines has no stock")
    void servicesHaveNoStock() throws Exception {
        String name = uniqueName();
        long product = good(name, true);
        long service = number(postJson("/api/products", token, """
                {"name":"%s","kind":"SERVICE","purpose":"SALE","unit":"HOUR"}
                """.formatted(uniqueName())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        long note = number(postJson(DOCUMENTS, token, """
                {"type":"RETURN_NOTE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":2,"unitPrice":10},{"productId":%d,"quantity":1,"unitPrice":10}]}
                """.formatted(customerId, mainWarehouse, product, service))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        issue(note);

        expectPhysical(name, "2");
    }

    // ----- Links between documents -----

    @Test
    @DisplayName("a delivered note or an invoice with a return note cannot be cancelled until the return note is")
    void returnedDocumentCannotBeCancelled() throws Exception {
        long product = good(uniqueName(), true);
        long delivery = deliveredNote(product, 2);
        long invoice = issuedInvoice(product, 2);
        long noteOfDelivery = returnNoteFrom(delivery, product, 1);
        long noteOfInvoice = returnNoteFrom(invoice, product, 1);

        // Even a draft return note counts: it is the return of that document until it is deleted or cancelled
        postJson(DOCUMENTS + "/" + delivery + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has a return note")));
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has a return note")));

        issue(noteOfDelivery);
        issue(noteOfInvoice);
        postJson(DOCUMENTS + "/" + noteOfDelivery + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + noteOfInvoice + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + delivery + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("a document can be returned in several parts: nothing stops a second return note")
    void severalReturnNotes() throws Exception {
        String name = uniqueName();
        long product = good(name, true);
        long delivery = deliveredNote(product, 5);

        issue(returnNoteFrom(delivery, product, 2));
        issue(returnNoteFrom(delivery, product, 3));

        getJson(DOCUMENTS + "/" + delivery, token).andExpect(jsonPath("$.derived", hasSize(2)));
        expectPhysical(name, "0"); // 5 out on the delivery, 2 + 3 back
    }

    @Test
    @DisplayName("what can be returned: a delivered note or an issued invoice, not an undelivered note, a draft, a cancelled invoice or an order")
    void whatCanBeReturned() throws Exception {
        long product = good(uniqueName(), true);

        long undelivered = create("DELIVERY_NOTE", product, 1);
        issue(undelivered);
        postJson(DOCUMENTS + "/" + undelivered + "/convert-to-return-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("delivered delivery note")));

        long draft = create("INVOICE", product, 1);
        postJson(DOCUMENTS + "/" + draft + "/convert-to-return-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only an issued invoice")));

        long cancelled = issuedInvoice(product, 1);
        postJson(DOCUMENTS + "/" + cancelled + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + cancelled + "/convert-to-return-note", token, "")
                .andExpect(status().isUnprocessableEntity());

        long order = create("SALES_ORDER", product, 1);
        postJson(DOCUMENTS + "/" + order + "/convert-to-return-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("delivery note or an invoice")));
    }

    @Test
    @DisplayName("a return note needs a warehouse, and keeps the customer of the document it was made from")
    void returnNoteRules() throws Exception {
        long product = good(uniqueName(), true);
        postJson(DOCUMENTS, token, """
                {"type":"RETURN_NOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":10}]}
                """.formatted(customerId, product))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("A return note needs a warehouse")));

        long otherCustomer = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Another client"}
                """).andReturn().getResponse().getContentAsString(), "$.id");
        long invoice = issuedInvoice(product, 1);
        long note = returnNoteFrom(invoice, product, 1);

        putJson(DOCUMENTS + "/" + note, token, """
                {"type":"RETURN_NOTE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":10}]}
                """.formatted(otherCustomer, mainWarehouse, product))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("customer of the document")));
    }

    // ----- Permissions -----

    @Test
    @DisplayName("a sales agent can prepare a return note but not issue or cancel it")
    void permissions() throws Exception {
        String email = uniqueEmail("agent");
        createUser(token, email, roleId(token, "SALES_AGENT"));
        String agent = login(email, "Password123");
        long product = good(uniqueName(), true);
        long delivery = deliveredNote(product, 1);

        long note = number(postJson(DOCUMENTS + "/" + delivery + "/convert-to-return-note", agent, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + note + "/issue", agent, "").andExpect(status().isForbidden());

        issue(note);
        postJson(DOCUMENTS + "/" + note + "/cancel", agent, "").andExpect(status().isForbidden());
    }
}
