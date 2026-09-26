package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The home page figures against real SQL: sums, the late invoices, the stock value and the low stock. Dates are
 * built from today, so the test holds whatever day it runs - including near a month boundary, where the expected
 * month is computed the same way the figures are.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardIntegrationTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    private String token;
    private long mainWarehouse;
    private long customerId;
    private long supplierId;

    @BeforeAll
    void setUp() throws Exception {
        token = registerCompany("Dashboard Test Co");
        mainWarehouse = number(getJson("/api/warehouses", token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        customerId = number(postJson("/api/customers", token, """
                {"type":"COMPANY","name":"Client Tableau"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        supplierId = number(postJson("/api/suppliers", token, """
                {"type":"COMPANY","name":"Fournisseur Tableau"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // ----- Fixtures -----

    private long product(String purchasePrice, String minStock) throws Exception {
        return number(postJson("/api/products", token, """
                {"name":"Dash-%s","kind":"GOOD","purpose":"BOTH","unit":"PIECE","salePrice":10,"purchasePrice":%s,
                 "allowNegativeStock":true,"minStock":%s}
                """.formatted(UUID.randomUUID().toString().substring(0, 8), purchasePrice, minStock))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** An issued sales invoice of `quantity` pieces at 10.000, dated `issue`, due `due` (null: no due date). */
    private long salesInvoice(long productId, int quantity, LocalDate issue, LocalDate due) throws Exception {
        long id = number(postJson("/api/sales-documents", token, """
                {"type":"INVOICE","customerId":%d,"warehouseId":%d,"issueDate":"%s",%s
                 "lines":[{"productId":%d,"quantity":%d,"unitPrice":10}]}
                """.formatted(customerId, mainWarehouse, issue, due == null ? "" : "\"dueDate\":\"" + due + "\",",
                productId, quantity)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/sales-documents/" + id + "/issue", token, "").andExpect(status().isOk());
        return id;
    }

    /** A validated purchase invoice of `quantity` pieces at the purchase price. */
    private long purchaseInvoice(long productId, int quantity, LocalDate issue, LocalDate due) throws Exception {
        long id = number(postJson("/api/purchase-documents", token, """
                {"type":"PURCHASE_INVOICE","supplierId":%d,"warehouseId":%d,"issueDate":"%s",%s
                 "lines":[{"productId":%d,"quantity":%d}]}
                """.formatted(supplierId, mainWarehouse, issue, due == null ? "" : "\"dueDate\":\"" + due + "\",",
                productId, quantity)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/purchase-documents/" + id + "/validate", token, "").andExpect(status().isOk());
        return id;
    }

    // ----- Sales -----

    @Test
    @DisplayName("sales: net revenue of the day and the month, what is unpaid, what is late, and the last six months")
    void salesFigures() throws Exception {
        String other = registerCompany("Dashboard Sales Co");
        // an isolated company: its own customer, product and warehouse
        long customer = number(postJson("/api/customers", other, """
                {"type":"COMPANY","name":"Client Only"}
                """).andReturn().getResponse().getContentAsString(), "$.id");
        long warehouse = number(getJson("/api/warehouses", other).andReturn().getResponse().getContentAsString(), "$[0].id");
        long good = number(postJson("/api/products", other, """
                {"name":"Only good","kind":"GOOD","purpose":"SALE","unit":"PIECE","salePrice":10,"allowNegativeStock":true}
                """).andReturn().getResponse().getContentAsString(), "$.id");

        LocalDate yesterday = TODAY.minusDays(1);
        LocalDate longAgo = TODAY.minusMonths(2);
        // A: 100 today, credited 20 -> 80 to pay ; B: 50 dated yesterday, due yesterday, 20 paid -> 30 late ;
        // C: 30 two months ago, no due date ; a draft and a cancelled invoice count for nothing
        long a = issuedFor(other, customer, warehouse, good, 10, TODAY, null);
        long b = issuedFor(other, customer, warehouse, good, 5, yesterday, yesterday);
        issuedFor(other, customer, warehouse, good, 3, longAgo, null);
        long cancelled = issuedFor(other, customer, warehouse, good, 9, TODAY, null);
        postJson("/api/sales-documents/" + cancelled + "/cancel", other, "").andExpect(status().isOk());
        postJson("/api/sales-documents", other, """
                {"type":"INVOICE","customerId":%d,"warehouseId":%d,"issueDate":"%s",
                 "lines":[{"productId":%d,"quantity":7,"unitPrice":10}]}
                """.formatted(customer, warehouse, TODAY, good)).andExpect(status().isCreated());

        long note = number(postJson("/api/sales-documents/" + a + "/convert-to-credit-note", other, "")
                .andReturn().getResponse().getContentAsString(), "$.id");
        putJson("/api/sales-documents/" + note, other, """
                {"type":"CREDIT_NOTE","customerId":%d,"issueDate":"%s",
                 "lines":[{"productId":%d,"quantity":2,"unitPrice":10}]}
                """.formatted(customer, TODAY, good)).andExpect(status().isOk());
        postJson("/api/sales-documents/" + note + "/issue", other, "").andExpect(status().isOk());
        postJson("/api/payments", other, """
                {"invoiceId":%d,"amount":20,"paymentDate":"%s","method":"CASH"}
                """.formatted(b, TODAY)).andExpect(status().isCreated());

        // net revenue by month, computed the way the figures are: invoices minus credit notes, by date
        double thisMonth = 80 + monthPart(yesterday, 50);
        double lastMonthAgo = monthPart(yesterday, 50, -1);
        double twoMonthsAgo = 30 + monthPart(yesterday, 50, -2);

        getJson("/api/dashboard/sales", other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(80.0))
                .andExpect(jsonPath("$.month").value(thisMonth))
                .andExpect(jsonPath("$.unpaid.count").value(3))
                .andExpect(jsonPath("$.unpaid.amount").value(140.0)) // 80 + 30 + 30
                .andExpect(jsonPath("$.overdue.count").value(1))
                .andExpect(jsonPath("$.overdue.amount").value(30.0))
                .andExpect(jsonPath("$.overdueInvoices", hasSize(1)))
                .andExpect(jsonPath("$.overdueInvoices[0].id").value((int) b))
                .andExpect(jsonPath("$.overdueInvoices[0].balance").value(30.0))
                .andExpect(jsonPath("$.months", hasSize(6)))
                .andExpect(jsonPath("$.months[5].month").value(YearMonth.from(TODAY).toString()))
                .andExpect(jsonPath("$.months[5].amount").value(thisMonth))
                .andExpect(jsonPath("$.months[4].month").value(YearMonth.from(TODAY).minusMonths(1).toString()))
                .andExpect(jsonPath("$.months[4].amount").value(lastMonthAgo))
                .andExpect(jsonPath("$.months[3].month").value(YearMonth.from(TODAY).minusMonths(2).toString()))
                .andExpect(jsonPath("$.months[3].amount").value(twoMonthsAgo));
    }

    /** The part of a dated amount that falls in the month `offset` months back (yesterday may be last month). */
    private static double monthPart(LocalDate date, double amount, int... offset) {
        int back = offset.length == 0 ? 0 : -offset[0];
        return YearMonth.from(date).equals(YearMonth.from(TODAY).minusMonths(back)) ? amount : 0;
    }

    private long issuedFor(String who, long customer, long warehouse, long good, int quantity,
                           LocalDate issue, LocalDate due) throws Exception {
        long id = number(postJson("/api/sales-documents", who, """
                {"type":"INVOICE","customerId":%d,"warehouseId":%d,"issueDate":"%s",%s
                 "lines":[{"productId":%d,"quantity":%d,"unitPrice":10}]}
                """.formatted(customer, warehouse, issue, due == null ? "" : "\"dueDate\":\"" + due + "\",",
                good, quantity)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/sales-documents/" + id + "/issue", who, "").andExpect(status().isOk());
        return id;
    }

    // ----- Purchases -----

    @Test
    @DisplayName("purchases: net purchases, what is still to pay, what is late, and the last six months")
    void purchaseFigures() throws Exception {
        String other = registerCompany("Dashboard Purchases Co");
        long supplier = number(postJson("/api/suppliers", other, """
                {"type":"COMPANY","name":"Supplier Only"}
                """).andReturn().getResponse().getContentAsString(), "$.id");
        long warehouse = number(getJson("/api/warehouses", other).andReturn().getResponse().getContentAsString(), "$[0].id");
        long good = number(postJson("/api/products", other, """
                {"name":"Only bought good","kind":"GOOD","purpose":"BOTH","unit":"PIECE","purchasePrice":30,
                 "salePrice":50,"allowNegativeStock":true}
                """).andReturn().getResponse().getContentAsString(), "$.id");

        LocalDate yesterday = TODAY.minusDays(1);
        // A: 90 today ; B: 60 dated yesterday, due yesterday, 10 paid -> 50 late
        long a = boughtFor(other, supplier, warehouse, good, 3, TODAY, null);
        long b = boughtFor(other, supplier, warehouse, good, 2, yesterday, yesterday);
        postJson("/api/supplier-payments", other, """
                {"invoiceId":%d,"amount":10,"paymentDate":"%s","method":"CASH"}
                """.formatted(b, TODAY)).andExpect(status().isCreated());
        long note = number(postJson("/api/purchase-documents/" + a + "/convert-to-credit-note", other, "")
                .andReturn().getResponse().getContentAsString(), "$.id");
        putJson("/api/purchase-documents/" + note, other, """
                {"type":"PURCHASE_CREDIT_NOTE","supplierId":%d,"issueDate":"%s","lines":[{"productId":%d,"quantity":1}]}
                """.formatted(supplier, TODAY, good)).andExpect(status().isOk());
        postJson("/api/purchase-documents/" + note + "/validate", other, "").andExpect(status().isOk());

        double thisMonth = 90 - 30 + monthPart(yesterday, 60);

        getJson("/api/dashboard/purchases", other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(60.0)) // 90 - 30 credited
                .andExpect(jsonPath("$.month").value(thisMonth))
                .andExpect(jsonPath("$.unpaid.count").value(2))
                .andExpect(jsonPath("$.unpaid.amount").value(110.0)) // (90 - 30) + (60 - 10)
                .andExpect(jsonPath("$.overdue.count").value(1))
                .andExpect(jsonPath("$.overdue.amount").value(50.0))
                .andExpect(jsonPath("$.overdueInvoices[0].id").value((int) b))
                .andExpect(jsonPath("$.months", hasSize(6)))
                .andExpect(jsonPath("$.months[5].amount").value(thisMonth));
    }

    private long boughtFor(String who, long supplier, long warehouse, long good, int quantity,
                           LocalDate issue, LocalDate due) throws Exception {
        long id = number(postJson("/api/purchase-documents", who, """
                {"type":"PURCHASE_INVOICE","supplierId":%d,"warehouseId":%d,"issueDate":"%s",%s
                 "lines":[{"productId":%d,"quantity":%d}]}
                """.formatted(supplier, warehouse, issue, due == null ? "" : "\"dueDate\":\"" + due + "\",",
                good, quantity)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/purchase-documents/" + id + "/validate", who, "").andExpect(status().isOk());
        return id;
    }

    // ----- Stock -----

    @Test
    @DisplayName("stock: what it is worth at purchase price, and the goods at or under their minimum")
    void stockFigures() throws Exception {
        String other = registerCompany("Dashboard Stock Co");
        long warehouse = number(getJson("/api/warehouses", other).andReturn().getResponse().getContentAsString(), "$[0].id");
        long low = number(postJson("/api/products", other, """
                {"name":"Aaa low good","kind":"GOOD","purpose":"BOTH","unit":"PIECE","purchasePrice":30,"salePrice":50,
                 "allowNegativeStock":true,"minStock":5}
                """).andReturn().getResponse().getContentAsString(), "$.id");
        long fine = number(postJson("/api/products", other, """
                {"name":"Bbb fine good","kind":"GOOD","purpose":"BOTH","unit":"PIECE","purchasePrice":10,"salePrice":20,
                 "allowNegativeStock":true,"minStock":2}
                """).andReturn().getResponse().getContentAsString(), "$.id");
        long noPrice = number(postJson("/api/products", other, """
                {"name":"Ccc unpriced good","kind":"GOOD","purpose":"SALE","unit":"PIECE","salePrice":20,
                 "allowNegativeStock":true}
                """).andReturn().getResponse().getContentAsString(), "$.id");

        for (long[] entry : new long[][]{{low, 4}, {fine, 10}, {noPrice, 7}}) {
            postJson("/api/stock/movements", other, """
                    {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":%d,"reason":"test"}
                    """.formatted(entry[0], warehouse, entry[1])).andExpect(status().isCreated());
        }
        // an exit lowers the value; a reservation is a promise, not goods
        postJson("/api/stock/movements", other, """
                {"type":"EXIT","productId":%d,"warehouseId":%d,"quantity":1,"reason":"sold"}
                """.formatted(fine, warehouse)).andExpect(status().isCreated());

        getJson("/api/dashboard/stock", other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(4 * 30.0 + 9 * 10.0)) // the unpriced good counts for nothing
                .andExpect(jsonPath("$.lowStockCount").value(1))
                .andExpect(jsonPath("$.lowStock", hasSize(1)))
                .andExpect(jsonPath("$.lowStock[0].name").value("Aaa low good"))
                .andExpect(jsonPath("$.lowStock[0].available").value(4.0))
                .andExpect(jsonPath("$.lowStock[0].minStock").value(5.0));
    }

    // ----- Rights and isolation -----

    @Test
    @DisplayName("a company with no data has empty figures: zero, no late invoice, six empty months")
    void emptyCompany() throws Exception {
        String empty = registerCompany("Dashboard Empty Co");

        getJson("/api/dashboard/sales", empty).andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(0.0))
                .andExpect(jsonPath("$.unpaid.count").value(0))
                .andExpect(jsonPath("$.unpaid.amount").value(0.0))
                .andExpect(jsonPath("$.overdueInvoices", hasSize(0)))
                .andExpect(jsonPath("$.months", hasSize(6)));
        getJson("/api/dashboard/purchases", empty).andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(0.0))
                .andExpect(jsonPath("$.overdue.count").value(0));
        getJson("/api/dashboard/stock", empty).andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(0.0))
                .andExpect(jsonPath("$.lowStockCount").value(0))
                .andExpect(jsonPath("$.lowStock", hasSize(0)));
    }

    @Test
    @DisplayName("what the caller's company did is never mixed with another's")
    void companiesAreSeparate() throws Exception {
        long good = product("30", "5");
        salesInvoice(good, 4, TODAY, null);
        purchaseInvoice(good, 2, TODAY, null);

        String stranger = registerCompany("Dashboard Stranger Co");

        getJson("/api/dashboard/sales", stranger).andExpect(jsonPath("$.today").value(0.0))
                .andExpect(jsonPath("$.unpaid.count").value(0));
        getJson("/api/dashboard/purchases", stranger).andExpect(jsonPath("$.today").value(0.0));
        getJson("/api/dashboard/stock", stranger).andExpect(jsonPath("$.value").value(0.0));
        getJson("/api/dashboard/sales", token).andExpect(jsonPath("$.unpaid.count", is(1)));
    }

    @Test
    @DisplayName("each area answers only to the right of its own module: an accountant sees sales and purchases, not the stock")
    void permissions() throws Exception {
        String email = uniqueEmail("accountant");
        createUser(token, email, roleId(token, "ACCOUNTANT"));
        String accountant = login(email, "Password123");

        getJson("/api/dashboard/sales", accountant).andExpect(status().isOk());
        getJson("/api/dashboard/purchases", accountant).andExpect(status().isOk());
        getJson("/api/dashboard/stock", accountant).andExpect(status().isForbidden());

        getJson("/api/dashboard/sales", null).andExpect(status().isUnauthorized());
        getJson("/api/dashboard/stock", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a module switched off takes its figures with it")
    void switchedOffModule() throws Exception {
        String company = registerCompany("Dashboard Gate Co");
        long id = number(getJson("/api/auth/me", company).andReturn().getResponse().getContentAsString(), "$.companyId");
        String platform = platformLogin();

        putJson("/api/platform/companies/" + id + "/modules", platform,
                "{\"modules\":[\"CUSTOMERS\",\"PURCHASES\",\"INVENTORY\"]}").andExpect(status().isOk());

        getJson("/api/dashboard/sales", company).andExpect(status().isForbidden());
        getJson("/api/dashboard/purchases", company).andExpect(status().isOk());
        getJson("/api/dashboard/stock", company).andExpect(status().isOk());
    }
}
