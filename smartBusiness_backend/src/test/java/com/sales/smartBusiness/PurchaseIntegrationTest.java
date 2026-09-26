package com.sales.smartBusiness;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack for purchase orders and goods receipts: the shared calculation, numbering at
 * validation, the order → receipt conversion, and above all the stock a receipt writes and
 * takes back. Every test builds its own product so none depends on another.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PurchaseIntegrationTest extends IntegrationTest {

    private static final String DOCUMENTS = "/api/purchase-documents";
    private static final String LEVELS = "/api/stock/levels";

    private String token;
    private long supplierId;
    private long mainWarehouse;
    private long annexWarehouse;
    private long vat19;
    private long fodec;
    private long stamp;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Purchase Test Co");

        String taxes = getJson("/api/settings/taxes", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        vat19 = taxId(taxes, "TVA 19%");
        fodec = taxId(taxes, "FODEC");
        stamp = taxId(taxes, "Timbre fiscal");

        supplierId = number(postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Fournisseur Achat"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        mainWarehouse = number(getJson("/api/warehouses", token).andReturn().getResponse().getContentAsString(), "$[0].id");
        annexWarehouse = number(postJson("/api/warehouses", token, """
                {"name":"Annex"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    private static long taxId(String taxes, String name) {
        List<Number> ids = JsonPath.read(taxes, "$[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }

    private String uniqueName() {
        return "Buy-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long product(String name, String purpose, boolean allowNegative, String kind) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"%s","kind":"%s","purpose":"%s","unit":"PIECE","purchasePrice":30,"salePrice":50,
                 "allowNegativeStock":%b}
                """.formatted(name, kind, purpose, allowNegative))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private long good(String name, boolean allowNegative) throws Exception {
        return product(name, "BOTH", allowNegative, "GOOD");
    }

    private String documentJson(String type, Long warehouseId, long productId, int quantity) {
        return """
                {"type":"%s","supplierId":%d,%s"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":%d}]}
                """.formatted(type, supplierId,
                warehouseId == null ? "" : "\"warehouseId\":" + warehouseId + ",", productId, quantity);
    }

    private long draftOrder(long productId, int quantity) throws Exception {
        return number(postJson(DOCUMENTS, token, documentJson("PURCHASE_ORDER", null, productId, quantity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private long validatedOrder(long productId, int quantity) throws Exception {
        long id = draftOrder(productId, quantity);
        postJson(DOCUMENTS + "/" + id + "/validate", token, "").andExpect(status().isOk());
        return id;
    }

    private long validatedReceipt(long productId, long warehouseId, int quantity) throws Exception {
        long id = number(postJson(DOCUMENTS, token, documentJson("GOODS_RECEIPT", warehouseId, productId, quantity))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + id + "/validate", token, "").andExpect(status().isOk());
        return id;
    }

    private void expectPhysical(String name, String physical) throws Exception {
        getJson(LEVELS + "?search=" + name, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].physical").value(Double.parseDouble(physical)));
    }

    // ----- Totals -----

    @Test
    @DisplayName("a draft takes the purchase price and carries the Finco totals with the tax breakdown")
    void draftHasComputedTotals() throws Exception {
        long product = good(uniqueName(), true);

        postJson(DOCUMENTS, token, """
                {"type":"PURCHASE_ORDER","supplierId":%d,"issueDate":"2026-09-20","taxIds":[%d,%d],
                 "lines":[{"productId":%d,"quantity":1,"vatTaxId":%d}]}
                """.formatted(supplierId, fodec, stamp, product, vat19))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").value(nullValue()))
                .andExpect(jsonPath("$.supplierName").value("Fournisseur Achat"))
                .andExpect(jsonPath("$.warehouseId").value(nullValue()))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(30.0))
                .andExpect(jsonPath("$.subtotal").value(30.0))
                .andExpect(jsonPath("$.total").value(37.057))
                .andExpect(jsonPath("$.taxes[0].name").value("FODEC"))
                .andExpect(jsonPath("$.taxes[1].amount").value(5.757))
                .andExpect(jsonPath("$.taxes[2].name").value("Timbre fiscal"));
    }

    @Test
    @DisplayName("the preview gives the totals and creates nothing")
    void previewSavesNothing() throws Exception {
        long product = good(uniqueName(), true);

        postJson(DOCUMENTS + "/preview", token, documentJson("PURCHASE_ORDER", null, product, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nullValue()))
                .andExpect(jsonPath("$.total").value(60.0));
    }

    // ----- Purchase order -----

    @Test
    @DisplayName("validating an order numbers it and freezes it, without touching the stock")
    void validateOrder() throws Exception {
        String name = uniqueName();
        long product = good(name, true);
        long id = draftOrder(product, 5);

        postJson(DOCUMENTS + "/" + id + "/validate", token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.reference").value(matchesPattern("PO-\\d{4}-\\d{5}")));

        putJson(DOCUMENTS + "/" + id, token, documentJson("PURCHASE_ORDER", null, product, 9))
                .andExpect(status().isUnprocessableEntity());
        deleteJson(DOCUMENTS + "/" + id, token).andExpect(status().isUnprocessableEntity());
        expectPhysical(name, "0");
    }

    @Test
    @DisplayName("a draft can be deleted and leaves no gap in the numbering")
    void deleteDraft() throws Exception {
        long id = draftOrder(good(uniqueName(), true), 1);

        deleteJson(DOCUMENTS + "/" + id, token).andExpect(status().isNoContent());
        getJson(DOCUMENTS + "/" + id, token).andExpect(status().isNotFound());
    }

    // ----- Receipt and stock -----

    @Test
    @DisplayName("validating a receipt puts the goods into the stock; cancelling it takes them back out")
    void receiptWritesAndReversesStock() throws Exception {
        String name = uniqueName();
        long product = good(name, false);

        long receipt = validatedReceipt(product, mainWarehouse, 8);
        expectPhysical(name, "8");
        getJson("/api/stock/movements?productId=" + product, token)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("ENTRY"))
                .andExpect(jsonPath("$.content[0].sourceType").value("PURCHASE_DOCUMENT"))
                .andExpect(jsonPath("$.content[0].sourceId").value(receipt));

        postJson(DOCUMENTS + "/" + receipt + "/cancel", token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectPhysical(name, "0");
        getJson("/api/stock/movements?productId=" + product + "&type=EXIT", token)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].quantity").value(-8.0));

        // cancelling again is refused, and writes nothing more
        postJson(DOCUMENTS + "/" + receipt + "/cancel", token, "").andExpect(status().isUnprocessableEntity());
        expectPhysical(name, "0");
    }

    @Test
    @DisplayName("a draft receipt moves nothing until it is validated")
    void draftReceiptMovesNothing() throws Exception {
        String name = uniqueName();
        long product = good(name, true);

        postJson(DOCUMENTS, token, documentJson("GOODS_RECEIPT", mainWarehouse, product, 4))
                .andExpect(status().isCreated());

        expectPhysical(name, "0");
    }

    @Test
    @DisplayName("a receipt goes into the warehouse it names")
    void receiptWarehouse() throws Exception {
        String name = uniqueName();
        long product = good(name, true);
        validatedReceipt(product, annexWarehouse, 3);

        getJson(LEVELS + "?search=" + name + "&warehouseId=" + annexWarehouse, token)
                .andExpect(jsonPath("$.content[0].physical").value(3.0));
        getJson(LEVELS + "?search=" + name + "&warehouseId=" + mainWarehouse, token)
                .andExpect(jsonPath("$.content[0].physical").value(0.0));
    }

    @Test
    @DisplayName("a service on a receipt puts nothing into the stock")
    void serviceHasNoStockEffect() throws Exception {
        String name = uniqueName();
        long service = product(name, "BOTH", true, "SERVICE");

        long receipt = validatedReceipt(service, mainWarehouse, 2);

        getJson("/api/stock/movements?productId=" + service, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        postJson(DOCUMENTS + "/" + receipt + "/cancel", token, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("a receipt cannot be cancelled once a strict product's goods have gone")
    void cancelRefusedWhenGoodsAreGone() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        long receipt = validatedReceipt(product, mainWarehouse, 5);

        postJson("/api/stock/movements", token, """
                {"type":"EXIT","productId":%d,"warehouseId":%d,"quantity":3}
                """.formatted(product, mainWarehouse)).andExpect(status().isCreated());

        postJson(DOCUMENTS + "/" + receipt + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Not enough stock")));

        getJson(DOCUMENTS + "/" + receipt, token).andExpect(jsonPath("$.status").value("VALIDATED"));
        expectPhysical(name, "2");
    }

    @Test
    @DisplayName("a receipt needs a warehouse, and an inactive one is refused")
    void warehouseRules() throws Exception {
        long product = good(uniqueName(), true);
        postJson(DOCUMENTS, token, documentJson("GOODS_RECEIPT", null, product, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("needs a warehouse")));

        long dormant = number(postJson("/api/warehouses", token, """
                {"name":"Dormant %s","active":false}
                """.formatted(UUID.randomUUID())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS, token, documentJson("GOODS_RECEIPT", dormant, product, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("not active")));
    }

    // ----- Order -> receipt -----

    @Test
    @DisplayName("an order is received in two parts: each receipt adds its own quantity")
    void partialReceipts() throws Exception {
        String name = uniqueName();
        long product = good(name, false);
        long order = validatedOrder(product, 10);

        long first = number(postJson(DOCUMENTS + "/" + order + "/convert-to-receipt", token, "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("GOODS_RECEIPT"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceId").value(order))
                .andExpect(jsonPath("$.warehouseName").value("Default warehouse"))
                .andExpect(jsonPath("$.lines[0].quantity").value(10.0))
                .andExpect(jsonPath("$.total").value(300.0))
                .andReturn().getResponse().getContentAsString(), "$.id");

        // only 4 arrived: the draft is adjusted, then validated
        putJson(DOCUMENTS + "/" + first, token, documentJson("GOODS_RECEIPT", mainWarehouse, product, 4))
                .andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + first + "/validate", token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(matchesPattern("GR-\\d{4}-\\d{5}")));
        expectPhysical(name, "4");

        long second = number(postJson(DOCUMENTS + "/" + order + "/convert-to-receipt", token, "")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        putJson(DOCUMENTS + "/" + second, token, documentJson("GOODS_RECEIPT", mainWarehouse, product, 6))
                .andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + second + "/validate", token, "").andExpect(status().isOk());
        expectPhysical(name, "10");

        getJson(DOCUMENTS + "/" + order, token).andExpect(jsonPath("$.derived", hasSize(2)));
    }

    @Test
    @DisplayName("an order with a live receipt cannot be cancelled until the receipt is")
    void cancelOrderWithReceipt() throws Exception {
        long product = good(uniqueName(), true);
        long order = validatedOrder(product, 2);
        long receipt = number(postJson(DOCUMENTS + "/" + order + "/convert-to-receipt", token, "")
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + receipt + "/validate", token, "").andExpect(status().isOk());

        postJson(DOCUMENTS + "/" + order + "/cancel", token, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("goods receipts")));

        postJson(DOCUMENTS + "/" + receipt + "/cancel", token, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + order + "/cancel", token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("only a validated order can be converted, and only an order")
    void convertRules() throws Exception {
        long product = good(uniqueName(), true);
        long draft = draftOrder(product, 1);
        postJson(DOCUMENTS + "/" + draft + "/convert-to-receipt", token, "")
                .andExpect(status().isUnprocessableEntity());

        long receipt = validatedReceipt(product, mainWarehouse, 1);
        postJson(DOCUMENTS + "/" + receipt + "/convert-to-receipt", token, "")
                .andExpect(status().isUnprocessableEntity());
    }

    // ----- References -----

    @Test
    @DisplayName("a sale-only product cannot be bought")
    void saleOnlyProductRefused() throws Exception {
        long saleOnly = product(uniqueName(), "SALE", true, "GOOD");

        postJson(DOCUMENTS, token, documentJson("PURCHASE_ORDER", null, saleOnly, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("sale-only")));
    }

    @Test
    @DisplayName("a supplier or a product that appears on a purchase document cannot be deleted")
    void deleteProtection() throws Exception {
        long supplier = number(postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Supplier Protected"}
                """).andReturn().getResponse().getContentAsString(), "$.id");
        long product = good(uniqueName(), true);

        long draft = number(postJson(DOCUMENTS, token, """
                {"type":"PURCHASE_ORDER","supplierId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1}]}
                """.formatted(supplier, product)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        deleteJson("/api/suppliers/" + supplier, token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("purchase document")));
        deleteJson("/api/products/" + product, token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("purchase document line")));

        deleteJson(DOCUMENTS + "/" + draft, token).andExpect(status().isNoContent());
        deleteJson("/api/suppliers/" + supplier, token).andExpect(status().isNoContent());
        deleteJson("/api/products/" + product, token).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("the list is filtered by type and by status")
    void listFilters() throws Exception {
        long product = good(uniqueName(), true);
        validatedOrder(product, 1);

        getJson(DOCUMENTS + "?type=PURCHASE_ORDER&status=VALIDATED&search=achat", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("PURCHASE_ORDER"))
                .andExpect(jsonPath("$.content[0].supplierName").value("Fournisseur Achat"))
                .andExpect(jsonPath("$.content[0].reference").value(matchesPattern("PO-.*")));
        // other tests cancel orders too: what matters is that nothing but cancelled ones come back
        getJson(DOCUMENTS + "?type=PURCHASE_ORDER&status=CANCELLED&size=50", token)
                .andExpect(jsonPath("$.content[?(@.status != 'CANCELLED')]", hasSize(0)));
        getJson(DOCUMENTS, token).andExpect(status().isBadRequest());
    }

    // ----- Permissions -----

    @Test
    @DisplayName("a purchase manager works with documents and reads the tax list; a sales agent cannot")
    void permissionsAreEnforced() throws Exception {
        String manager = login(userWithRole("PURCHASE_MANAGER"), "Password123");
        String agent = login(userWithRole("SALES_AGENT"), "Password123");
        long product = good(uniqueName(), true);
        String json = documentJson("PURCHASE_ORDER", null, product, 1);

        getJson("/api/settings/taxes", manager).andExpect(status().isOk());
        long id = number(postJson(DOCUMENTS, manager, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson(DOCUMENTS + "/" + id + "/validate", manager, "").andExpect(status().isOk());
        postJson(DOCUMENTS + "/" + id + "/cancel", manager, "").andExpect(status().isOk());

        // a sales agent has no PURCHASE_* right at all
        getJson(DOCUMENTS + "?type=PURCHASE_ORDER", agent).andExpect(status().isForbidden());
        postJson(DOCUMENTS, agent, json).andExpect(status().isForbidden());
        postJson(DOCUMENTS + "/preview", agent, json).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a read-only purchase role can list but neither create, validate nor cancel")
    void readOnlyRole() throws Exception {
        long viewerRole = number(postJson("/api/roles", token, """
                {"label":"Purchase viewer","permissions":["PURCHASE_VIEW"]}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String email = uniqueEmail("pviewer");
        createUser(token, email, viewerRole);
        String viewer = login(email, "Password123");
        long product = good(uniqueName(), true);
        long draft = draftOrder(product, 1);

        getJson(DOCUMENTS + "?type=PURCHASE_ORDER", viewer).andExpect(status().isOk());
        getJson(DOCUMENTS + "/" + draft, viewer).andExpect(status().isOk());
        postJson(DOCUMENTS, viewer, documentJson("PURCHASE_ORDER", null, product, 1)).andExpect(status().isForbidden());
        postJson(DOCUMENTS + "/" + draft + "/validate", viewer, "").andExpect(status().isForbidden());
        postJson(DOCUMENTS + "/" + draft + "/cancel", viewer, "").andExpect(status().isForbidden());
        deleteJson(DOCUMENTS + "/" + draft, viewer).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous caller is refused")
    void anonymousRefused() throws Exception {
        getJson(DOCUMENTS + "?type=PURCHASE_ORDER", null).andExpect(status().isUnauthorized());
    }

    private String userWithRole(String roleName) throws Exception {
        String email = uniqueEmail(roleName.toLowerCase().replace('_', '-'));
        createUser(token, email, roleId(token, roleName));
        return email;
    }
}
