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
 * Full stack for the stock: warehouses, the register, levels computed from real SQL sums,
 * the low-stock query, and the reservation a confirmed sales order makes — everything a
 * Mockito test cannot see. Every test builds its own product, so none depends on another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockIntegrationTest extends IntegrationTest {

    private static final String LEVELS = "/api/stock/levels";
    private static final String MOVEMENTS = "/api/stock/movements";

    private String token;
    private long mainWarehouse;
    private long annexWarehouse;
    private long customerId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Stock Test Co");

        String warehouses = getJson("/api/warehouses", token).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].defaultWarehouse").value(true))
                .andReturn().getResponse().getContentAsString();
        mainWarehouse = number(warehouses, "$[0].id");

        annexWarehouse = number(postJson("/api/warehouses", token, """
                {"name":"Annex","address":"Sfax"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultWarehouse").value(false))
                .andReturn().getResponse().getContentAsString(), "$.id");

        customerId = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Client Stock"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    /** A good with a unique name, so the levels screen can be searched for it. */
    private String uniqueName() {
        return "Item-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long good(String name, boolean allowNegative, String minStock) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"%s","kind":"GOOD","purpose":"SALE","unit":"PIECE","salePrice":10,
                 "allowNegativeStock":%b,"minStock":%s}
                """.formatted(name, allowNegative, minStock))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void move(long productId, long warehouseId, String type, String quantity) throws Exception {
        postJson(MOVEMENTS, token, """
                {"type":"%s","productId":%d,"warehouseId":%d,"quantity":%s,"reason":"test"}
                """.formatted(type, productId, warehouseId, quantity))
                .andExpect(status().isCreated());
    }

    private long issuedOrder(long productId, int quantity) throws Exception {
        long id = number(postJson("/api/sales-documents", token, """
                {"type":"SALES_ORDER","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d,"unitPrice":10}]}
                """.formatted(customerId, productId, quantity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/sales-documents/" + id + "/issue", token, "").andExpect(status().isOk());
        return id;
    }

    private void expectLevels(String name, String physical, String reserved, String available) throws Exception {
        getJson(LEVELS + "?search=" + name, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].physical").value(Double.parseDouble(physical)))
                .andExpect(jsonPath("$.content[0].reserved").value(Double.parseDouble(reserved)))
                .andExpect(jsonPath("$.content[0].available").value(Double.parseDouble(available)));
    }

    // ----- Levels -----

    @Test
    @DisplayName("a product with no movement shows zero, then its entries and exits add up")
    void levelsFollowTheRegister() throws Exception {
        String name = uniqueName();
        long product = good(name, true, "null");
        expectLevels(name, "0", "0", "0");

        move(product, mainWarehouse, "ENTRY", "10");
        move(product, mainWarehouse, "EXIT", "4");

        expectLevels(name, "6", "0", "6");
    }

    @Test
    @DisplayName("a service has no stock and is not listed")
    void serviceHasNoStock() throws Exception {
        String name = uniqueName();
        long service = number(postJson("/api/products", token, """
                {"name":"%s","kind":"SERVICE","purpose":"SALE","unit":"HOUR"}
                """.formatted(name)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        getJson(LEVELS + "?search=" + name, token).andExpect(jsonPath("$.content", hasSize(0)));
        postJson(MOVEMENTS, token, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":1}
                """.formatted(service, mainWarehouse))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("service")));
    }

    @Test
    @DisplayName("levels can be read one warehouse at a time")
    void levelsPerWarehouse() throws Exception {
        String name = uniqueName();
        long product = good(name, true, "null");
        move(product, mainWarehouse, "ENTRY", "10");
        move(product, annexWarehouse, "ENTRY", "3");

        expectLevels(name, "13", "0", "13");
        getJson(LEVELS + "?search=" + name + "&warehouseId=" + annexWarehouse, token)
                .andExpect(jsonPath("$.content[0].physical").value(3.0));
        getJson(LEVELS + "?search=" + name + "&warehouseId=" + mainWarehouse, token)
                .andExpect(jsonPath("$.content[0].physical").value(10.0));
    }

    @Test
    @DisplayName("a product at its minimum is flagged, and the low-stock filter finds it")
    void lowStock() throws Exception {
        String low = uniqueName();
        String fine = uniqueName();
        long lowProduct = good(low, true, "5");
        long fineProduct = good(fine, true, "5");
        move(lowProduct, mainWarehouse, "ENTRY", "5");
        move(fineProduct, mainWarehouse, "ENTRY", "50");

        getJson(LEVELS + "?search=" + low, token).andExpect(jsonPath("$.content[0].lowStock").value(true));
        getJson(LEVELS + "?search=" + fine, token).andExpect(jsonPath("$.content[0].lowStock").value(false));

        getJson(LEVELS + "?search=Item-&lowOnly=true&size=50", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == '" + low + "')]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.name == '" + fine + "')]", hasSize(0)));
    }

    // ----- Manual movements -----

    @Test
    @DisplayName("an exit past the available stock is refused for a strict product, allowed for the others")
    void exitRules() throws Exception {
        String strictName = uniqueName();
        long strict = good(strictName, false, "null");
        move(strict, mainWarehouse, "ENTRY", "3");

        postJson(MOVEMENTS, token, """
                {"type":"EXIT","productId":%d,"warehouseId":%d,"quantity":4}
                """.formatted(strict, mainWarehouse))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("3 available")));
        expectLevels(strictName, "3", "0", "3");

        String looseName = uniqueName();
        long loose = good(looseName, true, "null");
        move(loose, mainWarehouse, "EXIT", "2");
        expectLevels(looseName, "-2", "0", "-2");
    }

    @Test
    @DisplayName("an adjustment writes the difference between the count and the register")
    void adjustment() throws Exception {
        String name = uniqueName();
        long product = good(name, true, "null");
        move(product, mainWarehouse, "ENTRY", "10");

        move(product, mainWarehouse, "ADJUSTMENT", "7");
        expectLevels(name, "7", "0", "7");

        getJson(MOVEMENTS + "?productId=" + product + "&type=ADJUSTMENT", token)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].quantity").value(-3.0));

        postJson(MOVEMENTS, token, """
                {"type":"ADJUSTMENT","productId":%d,"warehouseId":%d,"quantity":7}
                """.formatted(product, mainWarehouse))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a transfer moves stock between warehouses and leaves the total unchanged")
    void transfer() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "10");

        postJson("/api/stock/transfers", token, """
                {"productId":%d,"fromWarehouseId":%d,"toWarehouseId":%d,"quantity":4,"reason":"Restock"}
                """.formatted(product, mainWarehouse, annexWarehouse))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$[0].quantity").value(-4.0))
                .andExpect(jsonPath("$[1].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$[1].warehouseName").value("Annex"));

        expectLevels(name, "10", "0", "10");
        getJson(LEVELS + "?search=" + name + "&warehouseId=" + mainWarehouse, token)
                .andExpect(jsonPath("$.content[0].physical").value(6.0));
        getJson(LEVELS + "?search=" + name + "&warehouseId=" + annexWarehouse, token)
                .andExpect(jsonPath("$.content[0].physical").value(4.0));

        // the source cannot give what it does not have (this product forbids negative stock)
        postJson("/api/stock/transfers", token, """
                {"productId":%d,"fromWarehouseId":%d,"toWarehouseId":%d,"quantity":7}
                """.formatted(product, mainWarehouse, annexWarehouse))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/stock/transfers", token, """
                {"productId":%d,"fromWarehouseId":%d,"toWarehouseId":%d,"quantity":1}
                """.formatted(product, mainWarehouse, mainWarehouse))
                .andExpect(status().isUnprocessableEntity());
    }

    // ----- Sales orders -----

    @Test
    @DisplayName("confirming an order reserves its goods; cancelling it gives them back")
    void orderReservesAndReleases() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "10");
        long order = issuedOrder(product, 4);

        // issued only: nothing promised yet
        expectLevels(name, "10", "0", "10");

        postJson("/api/sales-documents/" + order + "/status", token, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isOk());
        expectLevels(name, "10", "4", "6");

        getJson(MOVEMENTS + "?productId=" + product + "&type=RESERVE", token)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].sourceType").value("SALES_DOCUMENT"))
                .andExpect(jsonPath("$.content[0].sourceId").value(order));

        postJson("/api/sales-documents/" + order + "/cancel", token, "").andExpect(status().isOk());
        expectLevels(name, "10", "0", "10");
    }

    @Test
    @DisplayName("an order that asks for more than is available cannot be confirmed, and stays issued")
    void orderRefusedWhenNotEnoughStock() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "3");
        long order = issuedOrder(product, 5);

        postJson("/api/sales-documents/" + order + "/status", token, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString(name)));

        getJson("/api/sales-documents/" + order, token).andExpect(jsonPath("$.status").value("ISSUED"));
        expectLevels(name, "3", "0", "3");
    }

    @Test
    @DisplayName("what a first order reserved is not available to a second one")
    void reservationsCompete() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "5");
        long first = issuedOrder(product, 4);
        long second = issuedOrder(product, 3);

        postJson("/api/sales-documents/" + first + "/status", token, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isOk());
        postJson("/api/sales-documents/" + second + "/status", token, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("1 available")));

        // once the first is cancelled, the second fits
        postJson("/api/sales-documents/" + first + "/cancel", token, "").andExpect(status().isOk());
        postJson("/api/sales-documents/" + second + "/status", token, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isOk());
        expectLevels(name, "5", "3", "2");
    }

    @Test
    @DisplayName("a product that allows negative stock is confirmed even without stock, and shows as reserved")
    void looseProductAlwaysConfirms() throws Exception {
        String name = uniqueName();
        long product = good(name, true, "null");
        long order = issuedOrder(product, 2);

        postJson("/api/sales-documents/" + order + "/status", token, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isOk());

        expectLevels(name, "0", "2", "-2");
    }

    @Test
    @DisplayName("cancelling an order that was never confirmed writes nothing")
    void cancelUnconfirmedOrder() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "5");
        long order = issuedOrder(product, 2);

        postJson("/api/sales-documents/" + order + "/cancel", token, "").andExpect(status().isOk());

        getJson(MOVEMENTS + "?productId=" + product + "&type=RELEASE", token)
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ----- Protection -----

    @Test
    @DisplayName("a product or a warehouse that has movements cannot be deleted")
    void deleteProtection() throws Exception {
        long product = good(uniqueName(), true, "null");
        move(product, annexWarehouse, "ENTRY", "1");

        deleteJson("/api/products/" + product, token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("stock movement")));
        deleteJson("/api/warehouses/" + annexWarehouse, token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("stock movement")));
    }

    @Test
    @DisplayName("the default warehouse cannot be deleted or deactivated, but an unused one can go")
    void warehouseRules() throws Exception {
        deleteJson("/api/warehouses/" + mainWarehouse, token).andExpect(status().isUnprocessableEntity());
        putJson("/api/warehouses/" + mainWarehouse, token, """
                {"name":"Default warehouse","active":false}
                """).andExpect(status().isUnprocessableEntity());
        putJson("/api/warehouses/" + mainWarehouse, token, """
                {"name":"Head office stock","active":true}
                """).andExpect(status().isOk());
        putJson("/api/warehouses/" + mainWarehouse, token, """
                {"name":"Default warehouse","active":true}
                """).andExpect(status().isOk());

        long temporary = number(postJson("/api/warehouses", token, """
                {"name":"Temporary"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        deleteJson("/api/warehouses/" + temporary, token).andExpect(status().isNoContent());

        postJson("/api/warehouses", token, """
                {"name":"ANNEX"}
                """).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an inactive warehouse receives no new movement")
    void inactiveWarehouseRefused() throws Exception {
        long dormant = number(postJson("/api/warehouses", token, """
                {"name":"Dormant","active":false}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        long product = good(uniqueName(), true, "null");

        postJson(MOVEMENTS, token, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":1}
                """.formatted(product, dormant))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("not active")));
    }

    // ----- Permissions -----

    @Test
    @DisplayName("a sales agent can read the stock but neither move it nor manage warehouses")
    void salesAgentIsReadOnly() throws Exception {
        String email = uniqueEmail("agent");
        createUser(token, email, roleId(token, "SALES_AGENT"));
        String agent = login(email, "Password123");
        long product = good(uniqueName(), true, "null");

        getJson(LEVELS, agent).andExpect(status().isOk());
        getJson(MOVEMENTS, agent).andExpect(status().isOk());
        getJson("/api/warehouses", agent).andExpect(status().isOk());

        postJson(MOVEMENTS, agent, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":1}
                """.formatted(product, mainWarehouse)).andExpect(status().isForbidden());
        postJson("/api/stock/transfers", agent, """
                {"productId":%d,"fromWarehouseId":%d,"toWarehouseId":%d,"quantity":1}
                """.formatted(product, mainWarehouse, annexWarehouse)).andExpect(status().isForbidden());
        postJson("/api/warehouses", agent, "{\"name\":\"Nope\"}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a stock manager can move stock and manage warehouses")
    void stockManagerCanWrite() throws Exception {
        String email = uniqueEmail("stock");
        createUser(token, email, roleId(token, "STOCK_MANAGER"));
        String manager = login(email, "Password123");
        long product = good(uniqueName(), true, "null");

        postJson(MOVEMENTS, manager, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":1}
                """.formatted(product, mainWarehouse)).andExpect(status().isCreated());
        postJson("/api/warehouses", manager, "{\"name\":\"Managed " + UUID.randomUUID() + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an anonymous caller is refused")
    void anonymousRefused() throws Exception {
        getJson(LEVELS, null).andExpect(status().isUnauthorized());
        getJson("/api/warehouses", null).andExpect(status().isUnauthorized());
    }

    // ----- Delivery notes -----

    private long confirmedOrder(long productId, int quantity) throws Exception {
        long order = issuedOrder(productId, quantity);
        postJson("/api/sales-documents/" + order + "/status", token, "{\"status\":\"CONFIRMED\"}")
                .andExpect(status().isOk());
        return order;
    }

    private String deliveryJson(long productId, int quantity) {
        return """
                {"type":"DELIVERY_NOTE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d,"unitPrice":10}]}
                """.formatted(customerId, mainWarehouse, productId, quantity);
    }

    /** A draft delivery note made from an order, adjusted to the quantity that really leaves. */
    private long deliveryNoteFrom(long order, long productId, int quantity) throws Exception {
        long note = number(postJson("/api/sales-documents/" + order + "/convert-to-delivery-note", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DELIVERY_NOTE"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceId").value(order))
                .andReturn().getResponse().getContentAsString(), "$.id");
        putJson("/api/sales-documents/" + note, token, deliveryJson(productId, quantity)).andExpect(status().isOk());
        return note;
    }

    private void issue(long note) throws Exception {
        postJson("/api/sales-documents/" + note + "/issue", token, "").andExpect(status().isOk());
    }

    private void deliver(long note) throws Exception {
        postJson("/api/sales-documents/" + note + "/status", token, "{\"status\":\"DELIVERED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("delivering a confirmed order takes the goods out of the stock AND out of the reservation: what is left to sell does not move")
    void deliveryConsumesTheReservation() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "10");
        long order = confirmedOrder(product, 6);
        expectLevels(name, "10", "6", "4");

        long note = deliveryNoteFrom(order, product, 6);
        // a draft and a created note move nothing
        expectLevels(name, "10", "6", "4");
        postJson("/api/sales-documents/" + note + "/issue", token, "")
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("BL-\\d{4}-\\d{5}")));
        expectLevels(name, "10", "6", "4");

        deliver(note);

        expectLevels(name, "4", "0", "4");
        getJson(MOVEMENTS + "?productId=" + product + "&type=EXIT", token)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].quantity").value(-6.0))
                .andExpect(jsonPath("$.content[0].sourceId").value(note));
        getJson(MOVEMENTS + "?productId=" + product + "&type=RELEASE", token)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].sourceId").value(order));
    }

    @Test
    @DisplayName("an order delivered in two parts: 10 ordered, 4 then 6 delivered, and the reservation follows step by step")
    void partialDeliveries() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "10");
        long order = confirmedOrder(product, 10);
        expectLevels(name, "10", "10", "0");

        long first = deliveryNoteFrom(order, product, 4);
        issue(first);
        deliver(first);
        expectLevels(name, "6", "6", "0");

        long second = deliveryNoteFrom(order, product, 6);
        issue(second);
        deliver(second);
        expectLevels(name, "0", "0", "0");

        getJson("/api/sales-documents/" + order, token).andExpect(jsonPath("$.derived", hasSize(2)));
    }

    @Test
    @DisplayName("cancelling a delivered note brings the goods back and puts the reservation back on the order")
    void cancelDeliveredNote() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "10");
        long order = confirmedOrder(product, 10);
        long note = deliveryNoteFrom(order, product, 4);
        issue(note);
        deliver(note);
        expectLevels(name, "6", "6", "0");

        postJson("/api/sales-documents/" + note + "/cancel", token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectLevels(name, "10", "10", "0");

        // once cancelled it cannot be cancelled again, and the order can now be cancelled as a whole
        postJson("/api/sales-documents/" + note + "/cancel", token, "").andExpect(status().isUnprocessableEntity());
        postJson("/api/sales-documents/" + order + "/cancel", token, "").andExpect(status().isOk());
        expectLevels(name, "10", "0", "10");
    }

    @Test
    @DisplayName("an order that has a live delivery note cannot be cancelled")
    void orderWithDeliveryNoteCannotBeCancelled() throws Exception {
        String name = uniqueName();
        long product = good(name, true, "null");
        long order = confirmedOrder(product, 2);
        deliveryNoteFrom(order, product, 2);

        postJson("/api/sales-documents/" + order + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("delivery notes")));
    }

    @Test
    @DisplayName("cancelling a note that has not left yet moves nothing")
    void cancelCreatedNote() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "5");
        long order = confirmedOrder(product, 5);
        long note = deliveryNoteFrom(order, product, 5);
        issue(note);

        postJson("/api/sales-documents/" + note + "/cancel", token, "").andExpect(status().isOk());

        expectLevels(name, "5", "5", "0");
    }

    @Test
    @DisplayName("a note made by hand delivers without any order, and a strict product without the goods is refused")
    void standaloneDeliveryNote() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "5");

        long note = number(postJson("/api/sales-documents", token, deliveryJson(product, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseName").value("Default warehouse"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        issue(note);
        deliver(note);
        expectLevels(name, "2", "0", "2");

        long tooMuch = number(postJson("/api/sales-documents", token, deliveryJson(product, 3))
                .andReturn().getResponse().getContentAsString(), "$.id");
        issue(tooMuch);
        postJson("/api/sales-documents/" + tooMuch + "/status", token, "{\"status\":\"DELIVERED\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("2 available")));
        getJson("/api/sales-documents/" + tooMuch, token).andExpect(jsonPath("$.status").value("ISSUED"));
        expectLevels(name, "2", "0", "2");
    }

    @Test
    @DisplayName("a delivery of what an order reserved is allowed, but the same quantity delivered by hand would eat into that reservation")
    void reservedGoodsAreProtected() throws Exception {
        String name = uniqueName();
        long product = good(name, false, "null");
        move(product, mainWarehouse, "ENTRY", "10");
        long order = confirmedOrder(product, 8); // 10 in stock, 8 promised, 2 left to sell

        long byHand = number(postJson("/api/sales-documents", token, deliveryJson(product, 3))
                .andReturn().getResponse().getContentAsString(), "$.id");
        issue(byHand);
        postJson("/api/sales-documents/" + byHand + "/status", token, "{\"status\":\"DELIVERED\"}")
                .andExpect(status().isUnprocessableEntity());

        long fromOrder = deliveryNoteFrom(order, product, 8);
        issue(fromOrder);
        deliver(fromOrder);
        expectLevels(name, "2", "0", "2");
    }

    @Test
    @DisplayName("a delivery note needs a warehouse, and only a confirmed order can be turned into one")
    void deliveryNoteRules() throws Exception {
        long product = good(uniqueName(), true, "null");
        postJson("/api/sales-documents", token, """
                {"type":"DELIVERY_NOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":10}]}
                """.formatted(customerId, product))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("needs a warehouse")));

        long unconfirmed = issuedOrder(product, 1);
        postJson("/api/sales-documents/" + unconfirmed + "/convert-to-delivery-note", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Confirm the sales order")));
    }

    @Test
    @DisplayName("a sales agent can prepare a delivery note but not issue, deliver or cancel it")
    void deliveryPermissions() throws Exception {
        String email = uniqueEmail("agent");
        createUser(token, email, roleId(token, "SALES_AGENT"));
        String agent = login(email, "Password123");
        long product = good(uniqueName(), true, "null");
        long order = confirmedOrder(product, 1);

        long note = number(postJson("/api/sales-documents/" + order + "/convert-to-delivery-note", agent, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        postJson("/api/sales-documents/" + note + "/issue", agent, "").andExpect(status().isForbidden());
        postJson("/api/sales-documents/" + note + "/status", agent, "{\"status\":\"DELIVERED\"}").andExpect(status().isForbidden());
        postJson("/api/sales-documents/" + note + "/cancel", agent, "").andExpect(status().isForbidden());
    }
}
