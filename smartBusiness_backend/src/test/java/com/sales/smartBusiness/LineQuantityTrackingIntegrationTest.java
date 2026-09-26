package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack for the quantities followed line by line: an order is delivered (received) in parts and what is left is
 * shown and enforced; a delivery note (goods receipt, invoice) is returned in parts. Every test builds its own product
 * and documents, so none depends on another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LineQuantityTrackingIntegrationTest extends IntegrationTest {

    private static final String SALES = "/api/sales-documents";
    private static final String PURCHASES = "/api/purchase-documents";

    private String token;
    private long mainWarehouse;
    private long customerId;
    private long supplierId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Line Tracking Test Co");
        mainWarehouse = number(getJson("/api/warehouses", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        customerId = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Client Suivi"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        supplierId = number(postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Fournisseur Suivi"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    private long good() throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"Item-%s","kind":"GOOD","purpose":"BOTH","unit":"PIECE","purchasePrice":30,"salePrice":50,
                 "allowNegativeStock":true}
                """.formatted(UUID.randomUUID().toString().substring(0, 8))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String salesJson(String type, long productId, int quantity, Long sourceLineId) {
        return """
                {"type":"%s","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d,"unitPrice":10,"sourceLineId":%s}]}
                """.formatted(type, customerId, mainWarehouse, productId, quantity, sourceLineId);
    }

    private String purchaseJson(String type, long productId, int quantity, Long sourceLineId) {
        return """
                {"type":"%s","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d,"sourceLineId":%s}]}
                """.formatted(type, supplierId, mainWarehouse, productId, quantity, sourceLineId);
    }

    private long id(String json) {
        return number(json, "$.id");
    }

    private String read(String url) throws Exception {
        return getJson(url, token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long confirmedOrder(long product, int quantity) throws Exception {
        long order = id(postJson(SALES, token, """
                {"type":"SALES_ORDER","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d,"unitPrice":10}]}
                """.formatted(customerId, product, quantity)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        postJson(SALES + "/" + order + "/issue", token, "").andExpect(status().isOk());
        postJson(SALES + "/" + order + "/status", token, "{\"status\":\"CONFIRMED\"}").andExpect(status().isOk());
        return order;
    }

    private long validatedPurchaseOrder(long product, int quantity) throws Exception {
        long order = id(postJson(PURCHASES, token, """
                {"type":"PURCHASE_ORDER","supplierId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d}]}
                """.formatted(supplierId, product, quantity)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        postJson(PURCHASES + "/" + order + "/validate", token, "").andExpect(status().isOk());
        return order;
    }

    /** A draft made from the document, adjusted to the quantity really taken - keeping the link to the source line. */
    private long salesDraft(long source, String convert, String type, long product, int quantity) throws Exception {
        String draft = postJson(SALES + "/" + source + "/" + convert, token, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long sourceLine = number(draft, "$.lines[0].sourceLineId");
        putJson(SALES + "/" + id(draft), token, salesJson(type, product, quantity, sourceLine)).andExpect(status().isOk());
        return id(draft);
    }

    private long purchaseDraft(long source, String convert, String type, long product, int quantity) throws Exception {
        String draft = postJson(PURCHASES + "/" + source + "/" + convert, token, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long sourceLine = number(draft, "$.lines[0].sourceLineId");
        putJson(PURCHASES + "/" + id(draft), token, purchaseJson(type, product, quantity, sourceLine)).andExpect(status().isOk());
        return id(draft);
    }

    private void issue(long id) throws Exception {
        postJson(SALES + "/" + id + "/issue", token, "").andExpect(status().isOk());
    }

    private void validate(long id) throws Exception {
        postJson(PURCHASES + "/" + id + "/validate", token, "").andExpect(status().isOk());
    }

    // ----- Delivery of an order -----

    @Test
    @DisplayName("an order delivered in two parts shows what each line has left, and the next draft is filled with it")
    void orderDeliveredInParts() throws Exception {
        long product = good();
        long order = confirmedOrder(product, 10);

        long first = salesDraft(order, "convert-to-delivery-note", "DELIVERY_NOTE", product, 4);
        getJson(SALES + "/" + first, token)
                .andExpect(jsonPath("$.lines[0].sourceRemaining").value(10.0));
        issue(first);

        getJson(SALES + "/" + order, token)
                .andExpect(jsonPath("$.lines[0].fulfilledQuantity").value(4.0))
                .andExpect(jsonPath("$.lines[0].remainingQuantity").value(6.0));

        postJson(SALES + "/" + order + "/convert-to-delivery-note", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].quantity").value(6.0))
                .andExpect(jsonPath("$.lines[0].sourceRemaining").value(6.0));
    }

    @Test
    @DisplayName("a delivery note cannot deliver more of a line than the order has left")
    void cannotOverDeliver() throws Exception {
        long product = good();
        long order = confirmedOrder(product, 10);
        long first = salesDraft(order, "convert-to-delivery-note", "DELIVERY_NOTE", product, 7);
        long second = salesDraft(order, "convert-to-delivery-note", "DELIVERY_NOTE", product, 5);

        issue(first);
        postJson(SALES + "/" + second + "/issue", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only 3")))
                .andExpect(jsonPath("$.message").value(containsString("left to deliver")));

        putJson(SALES + "/" + second, token, salesJson("DELIVERY_NOTE", product, 3,
                number(read(SALES + "/" + second), "$.lines[0].sourceLineId"))).andExpect(status().isOk());
        issue(second);
    }

    @Test
    @DisplayName("an order fully delivered has nothing left to put on a delivery note; cancelling one gives its quantity back")
    void fullyDeliveredThenCancelled() throws Exception {
        long product = good();
        long order = confirmedOrder(product, 5);
        long note = salesDraft(order, "convert-to-delivery-note", "DELIVERY_NOTE", product, 5);
        issue(note);

        postJson(SALES + "/" + order + "/convert-to-delivery-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already on a delivery note")));

        postJson(SALES + "/" + note + "/cancel", token, "").andExpect(status().isOk());
        getJson(SALES + "/" + order, token).andExpect(jsonPath("$.lines[0].remainingQuantity").value(5.0));
        postJson(SALES + "/" + order + "/convert-to-delivery-note", token, "").andExpect(status().isCreated());
    }

    // ----- Return of a delivery note -----

    @Test
    @DisplayName("a delivered note is returned in parts: no more than was delivered, and the rest is proposed next time")
    void deliveryNoteReturnedInParts() throws Exception {
        long product = good();
        long order = confirmedOrder(product, 4);
        long note = salesDraft(order, "convert-to-delivery-note", "DELIVERY_NOTE", product, 4);
        issue(note);
        postJson(SALES + "/" + note + "/status", token, "{\"status\":\"DELIVERED\"}").andExpect(status().isOk());

        long tooMuch = salesDraft(note, "convert-to-return-note", "RETURN_NOTE", product, 5);
        postJson(SALES + "/" + tooMuch + "/issue", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only 4")))
                .andExpect(jsonPath("$.message").value(containsString("left to return")));

        long right = salesDraft(note, "convert-to-return-note", "RETURN_NOTE", product, 3);
        issue(right);

        getJson(SALES + "/" + note, token)
                .andExpect(jsonPath("$.lines[0].fulfilledQuantity").value(3.0))
                .andExpect(jsonPath("$.lines[0].remainingQuantity").value(1.0));
        postJson(SALES + "/" + note + "/convert-to-return-note", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].quantity").value(1.0));
    }

    // ----- Links -----

    @Test
    @DisplayName("a line of a draft can only follow a line of its own source, and a document without source ignores the link")
    void linksAreChecked() throws Exception {
        long product = good();
        long order = confirmedOrder(product, 2);
        long other = confirmedOrder(product, 2);
        long foreignLine = number(read(SALES + "/" + other), "$.lines[0].id");

        String draft = postJson(SALES + "/" + order + "/convert-to-delivery-note", token, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        putJson(SALES + "/" + id(draft), token, salesJson("DELIVERY_NOTE", product, 1, foreignLine))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("does not belong")));

        postJson(SALES, token, salesJson("DELIVERY_NOTE", product, 1, foreignLine))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].sourceLineId").value(nullValue()));
    }

    // ----- Purchases -----

    @Test
    @DisplayName("a purchase order received in parts shows what is left, refuses an over-receipt and proposes the rest")
    void purchaseOrderReceivedInParts() throws Exception {
        long product = good();
        long order = validatedPurchaseOrder(product, 10);

        long first = purchaseDraft(order, "convert-to-receipt", "GOODS_RECEIPT", product, 6);
        long second = purchaseDraft(order, "convert-to-receipt", "GOODS_RECEIPT", product, 6);
        validate(first);

        getJson(PURCHASES + "/" + order, token)
                .andExpect(jsonPath("$.lines[0].fulfilledQuantity").value(6.0))
                .andExpect(jsonPath("$.lines[0].remainingQuantity").value(4.0));

        postJson(PURCHASES + "/" + second + "/validate", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Only 4")))
                .andExpect(jsonPath("$.message").value(containsString("left to receive")));

        postJson(PURCHASES + "/" + order + "/convert-to-receipt", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].quantity").value(4.0));
    }

    @Test
    @DisplayName("a goods receipt returned in full has nothing left to return; cancelling the return note gives it back")
    void receiptReturned() throws Exception {
        long product = good();
        long order = validatedPurchaseOrder(product, 3);
        long receipt = purchaseDraft(order, "convert-to-receipt", "GOODS_RECEIPT", product, 3);
        validate(receipt);

        long note = purchaseDraft(receipt, "convert-to-return-note", "PURCHASE_RETURN_NOTE", product, 3);
        validate(note);

        postJson(PURCHASES + "/" + receipt + "/convert-to-return-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("already been returned")));

        postJson(PURCHASES + "/" + note + "/cancel", token, "").andExpect(status().isOk());
        getJson(PURCHASES + "/" + receipt, token).andExpect(jsonPath("$.lines[0].remainingQuantity").value(3.0));
    }
}
