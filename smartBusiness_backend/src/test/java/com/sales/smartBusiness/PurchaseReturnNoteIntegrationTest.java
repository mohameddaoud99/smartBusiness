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
 * Full stack for the supplier return note - the mirror of {@code ReturnNoteIntegrationTest}: goods that go back to the
 * supplier. Validating one takes them out of the warehouse - refused when a product that forbids negative stock does
 * not have them - and cancelling it brings them back.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PurchaseReturnNoteIntegrationTest extends IntegrationTest {

    private static final String DOCUMENTS = "/api/purchase-documents";

    private String token;
    private long mainWarehouse;
    private long supplierId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Supplier Return Note Test Co");
        mainWarehouse = number(getJson("/api/warehouses", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        supplierId = number(postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Fournisseur Retour"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    private String uniqueName() {
        return "Buy-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long good(String name, boolean allowNegative) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"%s","kind":"GOOD","purpose":"BOTH","unit":"PIECE","purchasePrice":30,"salePrice":50,
                 "allowNegativeStock":%b}
                """.formatted(name, allowNegative)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void exit(long productId, String quantity) throws Exception {
        postJson("/api/stock/movements", token, """
                {"type":"EXIT","productId":%d,"warehouseId":%d,"quantity":%s,"reason":"sold"}
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

    /** A draft return note made from the document, then lowered to the quantity really going back. */
    private long returnNoteFrom(long source, long productId, int quantity) throws Exception {
        long note = number(postJson(DOCUMENTS + "/" + source + "/convert-to-return-note", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PURCHASE_RETURN_NOTE"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceId").value(source))
                .andReturn().getResponse().getContentAsString(), "$.id");
        putJson(DOCUMENTS + "/" + note, token, documentJson("PURCHASE_RETURN_NOTE", productId, quantity))
                .andExpect(status().isOk());
        return note;
    }

    // ----- Stock -----

    @Test
    @DisplayName("a return note made from a goods receipt takes the goods out, and cancelling it brings them back")
    void returnFromReceipt() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        long receipt = validated("GOODS_RECEIPT", product, 5);
        expectPhysical(name, "5");

        long note = returnNoteFrom(receipt, product, 2);
        expectPhysical(name, "5"); // a draft moves nothing
        postJson(DOCUMENTS + "/" + note + "/validate", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.reference").value(containsString("PRN")));
        expectPhysical(name, "3");

        postJson(DOCUMENTS + "/" + note + "/cancel", token, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectPhysical(name, "5");
    }

    @Test
    @DisplayName("a return note made from an invoice takes the goods out too")
    void returnFromInvoice() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        long invoice = validated("PURCHASE_INVOICE", product, 4);
        expectPhysical(name, "4");

        validate(returnNoteFrom(invoice, product, 4));

        expectPhysical(name, "0");
    }

    @Test
    @DisplayName("a return note the stock cannot honour is refused and stays a draft, without a number")
    void refusedByStock() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        long receipt = validated("GOODS_RECEIPT", product, 5);
        exit(product, "4"); // only 1 left
        long note = returnNoteFrom(receipt, product, 3);

        postJson(DOCUMENTS + "/" + note + "/validate", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("1 available")));

        getJson(DOCUMENTS + "/" + note, token)
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").doesNotExist());
        expectPhysical(name, "1");
    }

    @Test
    @DisplayName("a return note made by hand works without any source")
    void standaloneReturnNote() throws Exception {
        String name = uniqueName();
        long product = good(name, true);

        long note = create("PURCHASE_RETURN_NOTE", product, 2);
        getJson(DOCUMENTS + "/" + note, token)
                .andExpect(jsonPath("$.warehouseName").value("Default warehouse"))
                .andExpect(jsonPath("$.sourceId").doesNotExist());
        validate(note);

        expectPhysical(name, "-2");
    }

    // ----- Links between documents -----

    @Test
    @DisplayName("a receipt or an invoice with a return note cannot be cancelled until the return note is")
    void returnedDocumentCannotBeCancelled() throws Exception {
        long product = good(uniqueName(), true);
        long receipt = validated("GOODS_RECEIPT", product, 3);
        long invoice = validated("PURCHASE_INVOICE", product, 3);
        long noteOfReceipt = returnNoteFrom(receipt, product, 1);
        long noteOfInvoice = returnNoteFrom(invoice, product, 1);

        // Even a draft return note counts: it is the return of that document until it is deleted or cancelled
        postJson(DOCUMENTS + "/" + receipt + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has a return note")));
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("has a return note")));

        validate(noteOfReceipt);
        validate(noteOfInvoice);
        postJson(DOCUMENTS + "/" + noteOfReceipt + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + noteOfInvoice + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + receipt + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + invoice + "/cancel", token, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("a document can be returned in several parts: nothing stops a second return note")
    void severalReturnNotes() throws Exception {
        String name = uniqueName();
        long product = good(name, true);
        long receipt = validated("GOODS_RECEIPT", product, 5);

        validate(returnNoteFrom(receipt, product, 2));
        validate(returnNoteFrom(receipt, product, 3));

        getJson(DOCUMENTS + "/" + receipt, token).andExpect(jsonPath("$.derived", hasSize(2)));
        expectPhysical(name, "0");
    }

    @Test
    @DisplayName("what can be returned: a validated receipt or invoice, not a draft, a cancelled document or an order")
    void whatCanBeReturned() throws Exception {
        long product = good(uniqueName(), true);

        long draft = create("GOODS_RECEIPT", product, 1);
        postJson(DOCUMENTS + "/" + draft + "/convert-to-return-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only a validated goods receipt")));

        long cancelled = validated("PURCHASE_INVOICE", product, 1);
        postJson(DOCUMENTS + "/" + cancelled + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + cancelled + "/convert-to-return-note", token, "")
                .andExpect(status().isUnprocessableEntity());

        long order = validated("PURCHASE_ORDER", product, 1);
        postJson(DOCUMENTS + "/" + order + "/convert-to-return-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("goods receipt or an invoice")));
    }

    @Test
    @DisplayName("a return note needs a warehouse, and keeps the supplier of the document it was made from")
    void returnNoteRules() throws Exception {
        long product = good(uniqueName(), true);
        postJson(DOCUMENTS, token, """
                {"type":"PURCHASE_RETURN_NOTE","supplierId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1}]}
                """.formatted(supplierId, product))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("A supplier return note needs a warehouse")));

        long otherSupplier = number(postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Another supplier"}
                """).andReturn().getResponse().getContentAsString(), "$.id");
        long receipt = validated("GOODS_RECEIPT", product, 1);
        long note = returnNoteFrom(receipt, product, 1);

        putJson(DOCUMENTS + "/" + note, token, """
                {"type":"PURCHASE_RETURN_NOTE","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1}]}
                """.formatted(otherSupplier, mainWarehouse, product))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("supplier of the document")));
    }

    // ----- Permissions -----

    @Test
    @DisplayName("an accountant can read return notes but not create, validate or cancel one")
    void permissions() throws Exception {
        String email = uniqueEmail("accountant");
        createUser(token, email, roleId(token, "ACCOUNTANT"));
        String accountant = login(email, "Password123");
        long product = good(uniqueName(), true);
        long receipt = validated("GOODS_RECEIPT", product, 2);

        postJson(DOCUMENTS + "/" + receipt + "/convert-to-return-note", accountant, "").andExpect(status().isForbidden());

        long note = returnNoteFrom(receipt, product, 1);
        getJson(DOCUMENTS + "/" + note, accountant).andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + note + "/validate", accountant, "").andExpect(status().isForbidden());

        validate(note);
        postJson(DOCUMENTS + "/" + note + "/cancel", accountant, "").andExpect(status().isForbidden());
    }
}
