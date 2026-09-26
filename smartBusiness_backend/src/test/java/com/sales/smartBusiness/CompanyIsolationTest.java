package com.sales.smartBusiness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance criterion #1: a user reaches nothing outside their own company.
 * <p>
 * Two companies are created, then every resource of the first is requested with the
 * token of the second. A resource of another company must answer 404 — never 403, which
 * would confirm that it exists.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompanyIsolationTest extends IntegrationTest {

    private String tokenA;
    private String tokenB;

    private long userOfA;
    private long roleOfA;
    private long branchOfA;
    private long customRoleOfA;
    private long bankAccountOfA;
    private long taxOfA;
    private long customerOfA;
    private long supplierOfA;
    private long categoryOfA;
    private long brandOfA;
    private long productOfA;
    private long documentOfA;
    private long warehouseOfA;
    private long purchaseDocumentOfA;
    private long purchasableProductOfA;

    @BeforeAll
    void setUpTwoCompanies() throws Exception {
        tokenA = registerCompany("ABC Distribution");
        tokenB = registerCompany("XYZ Commerce");

        roleOfA = roleId(tokenA, "SALES_MANAGER");
        userOfA = createUser(tokenA, uniqueEmail("sonia"), roleOfA);

        String branch = postJson("/api/branches", tokenA, """
                {"code":"TUNIS","name":"Tunis"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        branchOfA = number(branch, "$.id");

        String role = postJson("/api/roles", tokenA, """
                {"label":"Assistant A","permissions":["SALE_VIEW"]}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        customRoleOfA = number(role, "$.id");

        String bankAccount = postJson("/api/settings/bank-accounts", tokenA, """
                {"label":"BIAT principal","rib":"07000000000000000000","currency":"TND"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        bankAccountOfA = number(bankAccount, "$.id");

        String tax = postJson("/api/settings/taxes", tokenA, """
                {"name":"Eco-tax A","kind":"PERCENTAGE_SURCHARGE","rate":2.5}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        taxOfA = number(tax, "$.id");

        String customer = postJson("/api/customers", tokenA, """
                {"type":"COMPANY","name":"Client Alpha A","email":"alpha@a.tn"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        customerOfA = number(customer, "$.id");

        String supplier = postJson("/api/suppliers", tokenA, """
                {"type":"COMPANY","name":"Supplier Alpha A","email":"supplier-alpha@a.tn"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        supplierOfA = number(supplier, "$.id");

        String category = postJson("/api/categories", tokenA, """
                {"name":"Category Alpha A"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        categoryOfA = number(category, "$.id");

        String brand = postJson("/api/brands", tokenA, """
                {"name":"Brand Alpha A"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        brandOfA = number(brand, "$.id");

        String product = postJson("/api/products", tokenA, """
                {"name":"Product Alpha A","kind":"GOOD","purpose":"SALE","unit":"PIECE"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        productOfA = number(product, "$.id");

        String document = postJson("/api/sales-documents", tokenA, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":10}]}
                """.formatted(customerOfA, productOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        documentOfA = number(document, "$.id");

        String warehouse = postJson("/api/warehouses", tokenA, """
                {"name":"Warehouse Alpha A"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        warehouseOfA = number(warehouse, "$.id");

        postJson("/api/stock/movements", tokenA, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":5}
                """.formatted(productOfA, warehouseOfA)).andExpect(status().isCreated());

        String purchasable = postJson("/api/products", tokenA, """
                {"name":"Purchasable Alpha A","kind":"GOOD","purpose":"BOTH","unit":"PIECE"}
                """).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        purchasableProductOfA = number(purchasable, "$.id");

        String purchaseDocument = postJson("/api/purchase-documents", tokenA, """
                {"type":"PURCHASE_ORDER","supplierId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1,"unitPrice":10}]}
                """.formatted(supplierOfA, purchasableProductOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        purchaseDocumentOfA = number(purchaseDocument, "$.id");
    }

    // ----- Reading across companies -----

    @Test
    @DisplayName("a user of another company is not found")
    void cannotReadForeignUser() throws Exception {
        getJson("/api/users/" + userOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a role of another company is not found")
    void cannotReadForeignRole() throws Exception {
        getJson("/api/roles/" + customRoleOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a branch of another company is not found")
    void cannotReadForeignBranch() throws Exception {
        getJson("/api/branches/" + branchOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the history of a foreign user is not found")
    void cannotReadForeignUserHistory() throws Exception {
        getJson("/api/users/" + userOfA + "/history", tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a bank account of another company is not found")
    void cannotReadForeignBankAccount() throws Exception {
        getJson("/api/settings/bank-accounts/" + bankAccountOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a tax of another company is not found")
    void cannotReadForeignTax() throws Exception {
        getJson("/api/settings/taxes/" + taxOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a customer of another company is not found")
    void cannotReadForeignCustomer() throws Exception {
        getJson("/api/customers/" + customerOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a supplier of another company is not found")
    void cannotReadForeignSupplier() throws Exception {
        getJson("/api/suppliers/" + supplierOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a category of another company is not found")
    void cannotReadForeignCategory() throws Exception {
        getJson("/api/categories/" + categoryOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a brand of another company is not found")
    void cannotReadForeignBrand() throws Exception {
        getJson("/api/brands/" + brandOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a product of another company is not found")
    void cannotReadForeignProduct() throws Exception {
        getJson("/api/products/" + productOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    // ----- Listing never leaks -----

    @Test
    @DisplayName("listing users only ever returns the caller's own company")
    void userListIsScopedToTheCompany() throws Exception {
        getJson("/api/users?size=50", tokenB)
                .andExpect(status().isOk())
                // Only the administrator created for company B
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("listing roles returns this company's own copies, not another's")
    void roleListIsScopedToTheCompany() throws Exception {
        // The 7 standard roles seeded for B, and none of A's custom role
        getJson("/api/roles", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.label == 'Assistant A')]").isEmpty());
    }

    @Test
    @DisplayName("listing branches returns only this company's sites")
    void branchListIsScopedToTheCompany() throws Exception {
        // B only has the main branch created at registration
        getJson("/api/branches?size=50", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("MAIN"));
    }

    @Test
    @DisplayName("listing bank accounts returns only this company's own")
    void bankAccountListIsScopedToTheCompany() throws Exception {
        getJson("/api/settings/bank-accounts", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("listing taxes returns this company's own standard set, not another's custom tax")
    void taxListIsScopedToTheCompany() throws Exception {
        getJson("/api/settings/taxes", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.name == 'Eco-tax A')]").isEmpty());
    }

    @Test
    @DisplayName("numbering sequences are seeded per company and scoped to it")
    void numberingListIsScopedToTheCompany() throws Exception {
        getJson("/api/settings/numbering", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(com.sales.smartBusiness.numbering.DocumentType.values().length))
                .andExpect(jsonPath("$[?(@.documentType == 'SALES_INVOICE')].prefix").value("INV"));
    }

    @Test
    @DisplayName("listing customers only ever returns the caller's own company")
    void customerListIsScopedToTheCompany() throws Exception {
        getJson("/api/customers", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("listing suppliers only ever returns the caller's own company")
    void supplierListIsScopedToTheCompany() throws Exception {
        getJson("/api/suppliers", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("listing categories returns this company's own copies, not another's")
    void categoryListIsScopedToTheCompany() throws Exception {
        getJson("/api/categories", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Category Alpha A')]").isEmpty());
    }

    @Test
    @DisplayName("listing brands returns this company's own copies, not another's")
    void brandListIsScopedToTheCompany() throws Exception {
        getJson("/api/brands", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Brand Alpha A')]").isEmpty());
    }

    @Test
    @DisplayName("listing products only ever returns the caller's own company")
    void productListIsScopedToTheCompany() throws Exception {
        getJson("/api/products", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("the audit log of a company never shows another company's events")
    void auditLogIsScopedToTheCompany() throws Exception {
        // B has only its own registration event
        getJson("/api/audit-logs?size=50", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("the company endpoint always resolves to the caller's own company")
    void companyEndpointResolvesFromTheToken() throws Exception {
        getJson("/api/company", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("XYZ Commerce"));
    }

    // ----- Writing across companies -----

    @Test
    @DisplayName("a foreign user cannot be updated")
    void cannotUpdateForeignUser() throws Exception {
        putJson("/api/users/" + userOfA, tokenB, """
                {"firstName":"Hacked","lastName":"User","username":"hacked",
                 "email":"hacked@test.local","status":"ACTIVE","roleIds":[]}
                """)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign user cannot be deactivated")
    void cannotDeactivateForeignUser() throws Exception {
        patchJson("/api/users/" + userOfA + "/deactivate", tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign user's password cannot be reset")
    void cannotResetForeignPassword() throws Exception {
        patchJson("/api/users/" + userOfA + "/reset-password", tokenB, """
                {"newPassword":"NewPassword1"}
                """)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign role cannot be edited")
    void cannotUpdateForeignRole() throws Exception {
        putJson("/api/roles/" + customRoleOfA, tokenB, """
                {"label":"Hijacked","permissions":["USER_VIEW"]}
                """)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign role cannot be deleted")
    void cannotDeleteForeignRole() throws Exception {
        deleteJson("/api/roles/" + customRoleOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign branch cannot be edited")
    void cannotUpdateForeignBranch() throws Exception {
        putJson("/api/branches/" + branchOfA, tokenB, """
                {"code":"HACK","name":"Hijacked"}
                """)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign bank account cannot be edited or deleted")
    void cannotWriteForeignBankAccount() throws Exception {
        putJson("/api/settings/bank-accounts/" + bankAccountOfA, tokenB, """
                {"label":"Hijacked","rib":"09999999999999999999","currency":"TND"}
                """)
                .andExpect(status().isNotFound());

        deleteJson("/api/settings/bank-accounts/" + bankAccountOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign tax cannot be edited or deleted")
    void cannotWriteForeignTax() throws Exception {
        putJson("/api/settings/taxes/" + taxOfA, tokenB, """
                {"name":"Hijacked","kind":"PERCENTAGE_SURCHARGE","rate":9}
                """)
                .andExpect(status().isNotFound());

        deleteJson("/api/settings/taxes/" + taxOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign customer cannot be edited or deleted")
    void cannotWriteForeignCustomer() throws Exception {
        putJson("/api/customers/" + customerOfA, tokenB, """
                {"type":"COMPANY","name":"Hijacked"}
                """)
                .andExpect(status().isNotFound());

        deleteJson("/api/customers/" + customerOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign supplier cannot be edited or deleted")
    void cannotWriteForeignSupplier() throws Exception {
        putJson("/api/suppliers/" + supplierOfA, tokenB, """
                {"type":"COMPANY","name":"Hijacked"}
                """)
                .andExpect(status().isNotFound());

        deleteJson("/api/suppliers/" + supplierOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign category cannot be edited or deleted")
    void cannotWriteForeignCategory() throws Exception {
        putJson("/api/categories/" + categoryOfA, tokenB, """
                {"name":"Hijacked"}
                """)
                .andExpect(status().isNotFound());

        deleteJson("/api/categories/" + categoryOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign brand cannot be edited or deleted")
    void cannotWriteForeignBrand() throws Exception {
        putJson("/api/brands/" + brandOfA, tokenB, """
                {"name":"Hijacked"}
                """)
                .andExpect(status().isNotFound());

        deleteJson("/api/brands/" + brandOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign product cannot be edited or deleted")
    void cannotWriteForeignProduct() throws Exception {
        putJson("/api/products/" + productOfA, tokenB, """
                {"name":"Hijacked","kind":"GOOD","purpose":"SALE","unit":"PIECE"}
                """)
                .andExpect(status().isNotFound());

        deleteJson("/api/products/" + productOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("photos of a foreign product cannot be added or removed")
    void cannotTouchForeignProductPhotos() throws Exception {
        postFile("/api/products/" + productOfA + "/images", tokenB,
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "photo.png", "image/png", new byte[]{1, 2, 3}))
                .andExpect(status().isNotFound());

        deleteJson("/api/products/" + productOfA + "/images/1", tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a sales document of another company is not found")
    void cannotReadForeignSalesDocument() throws Exception {
        getJson("/api/sales-documents/" + documentOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a foreign sales document cannot be edited, issued, cancelled, converted or deleted")
    void cannotWriteForeignSalesDocument() throws Exception {
        putJson("/api/sales-documents/" + documentOfA, tokenB, """
                {"type":"QUOTE","customerId":1,"issueDate":"2026-09-20",
                 "lines":[{"designation":"Hijacked","quantity":1,"unitPrice":1}]}
                """)
                .andExpect(status().isNotFound());

        postJson("/api/sales-documents/" + documentOfA + "/issue", tokenB, "")
                .andExpect(status().isNotFound());
        postJson("/api/sales-documents/" + documentOfA + "/status", tokenB, "{\"status\":\"ACCEPTED\"}")
                .andExpect(status().isNotFound());
        postJson("/api/sales-documents/" + documentOfA + "/cancel", tokenB, "")
                .andExpect(status().isNotFound());
        postJson("/api/sales-documents/" + documentOfA + "/convert-to-order", tokenB, "")
                .andExpect(status().isNotFound());
        deleteJson("/api/sales-documents/" + documentOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("listing sales documents only ever returns the caller's own company")
    void salesDocumentListIsScoped() throws Exception {
        getJson("/api/sales-documents?type=QUOTE", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a customer, a product or a tax of another company cannot go on one's own document")
    void cannotBuildDocumentOnForeignReferences() throws Exception {
        String ownCustomer = postJson("/api/customers", tokenB, """
                {"type":"COMPANY","name":"Client of B"}
                """).andReturn().getResponse().getContentAsString();
        long customerOfB = number(ownCustomer, "$.id");

        // foreign customer
        postJson("/api/sales-documents", tokenB, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1}]}
                """.formatted(customerOfA))
                .andExpect(status().isUnprocessableEntity());

        // foreign product
        postJson("/api/sales-documents", tokenB, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1}]}
                """.formatted(customerOfB, productOfA))
                .andExpect(status().isUnprocessableEntity());

        // foreign tax, on the document and on a line
        postJson("/api/sales-documents", tokenB, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20","taxIds":[%d],
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1}]}
                """.formatted(customerOfB, taxOfA))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/sales-documents", tokenB, """
                {"type":"QUOTE","customerId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1,"vatTaxId":%d}]}
                """.formatted(customerOfB, taxOfA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a warehouse of another company is not found, edited or deleted")
    void cannotTouchForeignWarehouse() throws Exception {
        getJson("/api/warehouses/" + warehouseOfA, tokenB).andExpect(status().isNotFound());
        putJson("/api/warehouses/" + warehouseOfA, tokenB, """
                {"name":"Hijacked"}
                """).andExpect(status().isNotFound());
        deleteJson("/api/warehouses/" + warehouseOfA, tokenB).andExpect(status().isNotFound());

        // and B's list only holds B's own default warehouse
        getJson("/api/warehouses", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("the stock register and levels only ever show the caller's own company")
    void stockReadsAreScoped() throws Exception {
        getJson("/api/stock/movements", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/stock/movements?productId=" + productOfA, tokenB)
                .andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/stock/levels", tokenB)
                .andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/stock/levels?warehouseId=" + warehouseOfA, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a product or a warehouse of another company cannot be used in one's own stock movement")
    void cannotMoveStockAcrossCompanies() throws Exception {
        String ownWarehouse = getJson("/api/warehouses", tokenB).andReturn().getResponse().getContentAsString();
        long warehouseOfB = number(ownWarehouse, "$[0].id");
        String ownProduct = postJson("/api/products", tokenB, """
                {"name":"Product of B","kind":"GOOD","purpose":"SALE","unit":"PIECE"}
                """).andReturn().getResponse().getContentAsString();
        long productOfB = number(ownProduct, "$.id");

        // foreign product
        postJson("/api/stock/movements", tokenB, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":1}
                """.formatted(productOfA, warehouseOfB)).andExpect(status().isUnprocessableEntity());
        // foreign warehouse
        postJson("/api/stock/movements", tokenB, """
                {"type":"ENTRY","productId":%d,"warehouseId":%d,"quantity":1}
                """.formatted(productOfB, warehouseOfA)).andExpect(status().isUnprocessableEntity());
        // foreign warehouse in a transfer
        postJson("/api/stock/transfers", tokenB, """
                {"productId":%d,"fromWarehouseId":%d,"toWarehouseId":%d,"quantity":1}
                """.formatted(productOfB, warehouseOfB, warehouseOfA)).andExpect(status().isUnprocessableEntity());

        // B's own list of products is asserted empty elsewhere: leave nothing behind
        deleteJson("/api/products/" + productOfB, tokenB).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a purchase document of another company is not found, edited, validated, cancelled, converted or deleted")
    void cannotTouchForeignPurchaseDocument() throws Exception {
        getJson("/api/purchase-documents/" + purchaseDocumentOfA, tokenB).andExpect(status().isNotFound());
        putJson("/api/purchase-documents/" + purchaseDocumentOfA, tokenB, """
                {"type":"PURCHASE_ORDER","supplierId":1,"issueDate":"2026-09-20",
                 "lines":[{"designation":"Hijacked","quantity":1,"unitPrice":1}]}
                """).andExpect(status().isNotFound());
        postJson("/api/purchase-documents/" + purchaseDocumentOfA + "/validate", tokenB, "")
                .andExpect(status().isNotFound());
        postJson("/api/purchase-documents/" + purchaseDocumentOfA + "/cancel", tokenB, "")
                .andExpect(status().isNotFound());
        postJson("/api/purchase-documents/" + purchaseDocumentOfA + "/convert-to-receipt", tokenB, "")
                .andExpect(status().isNotFound());
        deleteJson("/api/purchase-documents/" + purchaseDocumentOfA, tokenB).andExpect(status().isNotFound());

        getJson("/api/purchase-documents?type=PURCHASE_ORDER", tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a supplier, product, warehouse or tax of another company cannot go on one's own purchase document")
    void cannotBuildPurchaseDocumentOnForeignReferences() throws Exception {
        String ownSupplier = postJson("/api/suppliers", tokenB, """
                {"type":"COMPANY","name":"Supplier of B"}
                """).andReturn().getResponse().getContentAsString();
        long supplierOfB = number(ownSupplier, "$.id");
        long warehouseOfB = number(getJson("/api/warehouses", tokenB).andReturn().getResponse().getContentAsString(), "$[0].id");

        // foreign supplier
        postJson("/api/purchase-documents", tokenB, """
                {"type":"PURCHASE_ORDER","supplierId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1}]}
                """.formatted(supplierOfA)).andExpect(status().isUnprocessableEntity());
        // foreign product
        postJson("/api/purchase-documents", tokenB, """
                {"type":"PURCHASE_ORDER","supplierId":%d,"issueDate":"2026-09-20",
                 "lines":[{"productId":%d,"quantity":1}]}
                """.formatted(supplierOfB, purchasableProductOfA)).andExpect(status().isUnprocessableEntity());
        // foreign warehouse on a receipt
        postJson("/api/purchase-documents", tokenB, """
                {"type":"GOODS_RECEIPT","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1}]}
                """.formatted(supplierOfB, warehouseOfA)).andExpect(status().isUnprocessableEntity());
        // foreign tax, on the document and on a line
        postJson("/api/purchase-documents", tokenB, """
                {"type":"PURCHASE_ORDER","supplierId":%d,"issueDate":"2026-09-20","taxIds":[%d],
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1}]}
                """.formatted(supplierOfB, taxOfA)).andExpect(status().isUnprocessableEntity());
        postJson("/api/purchase-documents", tokenB, """
                {"type":"PURCHASE_ORDER","supplierId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1,"vatTaxId":%d}]}
                """.formatted(supplierOfB, taxOfA)).andExpect(status().isUnprocessableEntity());

        // the warehouse of B is B's own, for the record
        postJson("/api/purchase-documents/preview", tokenB, """
                {"type":"GOODS_RECEIPT","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1}]}
                """.formatted(supplierOfB, warehouseOfB)).andExpect(status().isOk());

        // B's own list of suppliers is asserted empty elsewhere: leave nothing behind
        deleteJson("/api/suppliers/" + supplierOfB, tokenB).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a delivery note cannot leave from a warehouse of another company, nor be made from a foreign order")
    void cannotDeliverAcrossCompanies() throws Exception {
        String ownCustomer = postJson("/api/customers", tokenB, """
                {"type":"COMPANY","name":"Delivery client of B"}
                """).andReturn().getResponse().getContentAsString();
        long customerOfB = number(ownCustomer, "$.id");

        // a foreign warehouse
        postJson("/api/sales-documents", tokenB, """
                {"type":"DELIVERY_NOTE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1}]}
                """.formatted(customerOfB, warehouseOfA)).andExpect(status().isUnprocessableEntity());

        // a foreign order cannot be turned into a delivery note
        postJson("/api/sales-documents/" + documentOfA + "/convert-to-delivery-note", tokenB, "")
                .andExpect(status().isNotFound());

        // B's own list of customers is asserted empty elsewhere: leave nothing behind
        deleteJson("/api/customers/" + customerOfB, tokenB).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("an invoice and its payments of another company cannot be read, paid, cancelled or made from a foreign document")
    void cannotReachForeignInvoicesAndPayments() throws Exception {
        // A free line: no product, so nothing touches the stock counted by the other tests
        long invoiceOfA = number(postJson("/api/sales-documents", tokenA, """
                {"type":"INVOICE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"Service A","quantity":1,"unitPrice":10}]}
                """.formatted(customerOfA, warehouseOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/sales-documents/" + invoiceOfA + "/issue", tokenA, "").andExpect(status().isOk());
        long paymentOfA = number(postJson("/api/payments", tokenA, """
                {"invoiceId":%d,"amount":4,"paymentDate":"2026-09-21","method":"CASH"}
                """.formatted(invoiceOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        // B cannot make an invoice out of a document of A, pay an invoice of A, or cancel a payment of A
        postJson("/api/sales-documents/" + documentOfA + "/convert-to-invoice", tokenB, "")
                .andExpect(status().isNotFound());
        postJson("/api/payments", tokenB, """
                {"invoiceId":%d,"amount":1,"paymentDate":"2026-09-21","method":"CASH"}
                """.formatted(invoiceOfA)).andExpect(status().isNotFound());
        postJson("/api/payments/" + paymentOfA + "/cancel", tokenB, "").andExpect(status().isNotFound());

        // B sees none of A's payments, whether it asks for them all or for that invoice
        getJson("/api/payments", tokenB).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/payments?invoiceId=" + invoiceOfA, tokenB).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/sales-documents/" + invoiceOfA, tokenB).andExpect(status().isNotFound());

        // and A's invoice is exactly as A left it
        getJson("/api/sales-documents/" + invoiceOfA, tokenA)
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.paidAmount").value(4.0));
        postJson("/api/payments/" + paymentOfA + "/cancel", tokenA, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("an invoice of another company cannot be credited")
    void cannotCreditAcrossCompanies() throws Exception {
        // A free line: no product, so nothing touches the stock counted by the other tests
        long invoiceOfA = number(postJson("/api/sales-documents", tokenA, """
                {"type":"INVOICE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"Service credit A","quantity":1,"unitPrice":10}]}
                """.formatted(customerOfA, warehouseOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/sales-documents/" + invoiceOfA + "/issue", tokenA, "").andExpect(status().isOk());

        postJson("/api/sales-documents/" + invoiceOfA + "/convert-to-credit-note", tokenB, "")
                .andExpect(status().isNotFound());

        getJson("/api/sales-documents/" + invoiceOfA, tokenA)
                .andExpect(jsonPath("$.derived", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.creditedAmount").value(0.0));
    }

    @Test
    @DisplayName("a purchase invoice and its payments of another company cannot be read, paid, cancelled or made from a foreign document")
    void cannotReachForeignPurchaseInvoicesAndPayments() throws Exception {
        // A free line: no product, so nothing touches the stock counted by the other tests
        long invoiceOfA = number(postJson("/api/purchase-documents", tokenA, """
                {"type":"PURCHASE_INVOICE","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"Service purchase A","quantity":1,"unitPrice":10}]}
                """.formatted(supplierOfA, warehouseOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/purchase-documents/" + invoiceOfA + "/validate", tokenA, "").andExpect(status().isOk());
        long paymentOfA = number(postJson("/api/supplier-payments", tokenA, """
                {"invoiceId":%d,"amount":4,"paymentDate":"2026-09-21","method":"CASH"}
                """.formatted(invoiceOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        // B cannot make an invoice out of a document of A, pay an invoice of A, or cancel a payment of A
        postJson("/api/purchase-documents/" + purchaseDocumentOfA + "/convert-to-invoice", tokenB, "")
                .andExpect(status().isNotFound());
        postJson("/api/supplier-payments", tokenB, """
                {"invoiceId":%d,"amount":1,"paymentDate":"2026-09-21","method":"CASH"}
                """.formatted(invoiceOfA)).andExpect(status().isNotFound());
        postJson("/api/supplier-payments/" + paymentOfA + "/cancel", tokenB, "").andExpect(status().isNotFound());

        // B sees none of A's payments, whether it asks for them all or for that invoice
        getJson("/api/supplier-payments", tokenB).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/supplier-payments?invoiceId=" + invoiceOfA, tokenB).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/purchase-documents/" + invoiceOfA, tokenB).andExpect(status().isNotFound());

        // and A's invoice is exactly as A left it
        getJson("/api/purchase-documents/" + invoiceOfA, tokenA)
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.paidAmount").value(4.0));
        postJson("/api/supplier-payments/" + paymentOfA + "/cancel", tokenA, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("a purchase invoice of another company cannot be credited")
    void cannotCreditPurchaseInvoicesAcrossCompanies() throws Exception {
        // A free line: no product, so nothing touches the stock counted by the other tests
        long invoiceOfA = number(postJson("/api/purchase-documents", tokenA, """
                {"type":"PURCHASE_INVOICE","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"Service credit purchase A","quantity":1,"unitPrice":10}]}
                """.formatted(supplierOfA, warehouseOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/purchase-documents/" + invoiceOfA + "/validate", tokenA, "").andExpect(status().isOk());

        postJson("/api/purchase-documents/" + invoiceOfA + "/convert-to-credit-note", tokenB, "")
                .andExpect(status().isNotFound());

        getJson("/api/purchase-documents/" + invoiceOfA, tokenA)
                .andExpect(jsonPath("$.derived", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.creditedAmount").value(0.0));
    }

    @Test
    @DisplayName("a return note cannot be made from a document of another company, nor come back to a foreign warehouse")
    void cannotReturnAcrossCompanies() throws Exception {
        // A free line: no product, so nothing touches the stock counted by the other tests
        long invoiceOfA = number(postJson("/api/sales-documents", tokenA, """
                {"type":"INVOICE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"Service return A","quantity":1,"unitPrice":10}]}
                """.formatted(customerOfA, warehouseOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/sales-documents/" + invoiceOfA + "/issue", tokenA, "").andExpect(status().isOk());
        long purchaseInvoiceOfA = number(postJson("/api/purchase-documents", tokenA, """
                {"type":"PURCHASE_INVOICE","supplierId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"Service return purchase A","quantity":1,"unitPrice":10}]}
                """.formatted(supplierOfA, warehouseOfA)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        postJson("/api/purchase-documents/" + purchaseInvoiceOfA + "/validate", tokenA, "").andExpect(status().isOk());

        postJson("/api/sales-documents/" + invoiceOfA + "/convert-to-return-note", tokenB, "")
                .andExpect(status().isNotFound());
        postJson("/api/purchase-documents/" + purchaseInvoiceOfA + "/convert-to-return-note", tokenB, "")
                .andExpect(status().isNotFound());

        // a return note of B cannot come back to a warehouse of A
        String ownCustomer = postJson("/api/customers", tokenB, """
                {"type":"COMPANY","name":"Return client of B"}
                """).andReturn().getResponse().getContentAsString();
        long customerOfB = number(ownCustomer, "$.id");
        postJson("/api/sales-documents", tokenB, """
                {"type":"RETURN_NOTE","customerId":%d,"warehouseId":%d,"issueDate":"2026-09-20",
                 "lines":[{"designation":"A","quantity":1,"unitPrice":1}]}
                """.formatted(customerOfB, warehouseOfA)).andExpect(status().isUnprocessableEntity());

        // B's own list of customers is asserted empty elsewhere: leave nothing behind
        deleteJson("/api/customers/" + customerOfB, tokenB).andExpect(status().isNoContent());

        getJson("/api/sales-documents/" + invoiceOfA, tokenA)
                .andExpect(jsonPath("$.derived", org.hamcrest.Matchers.hasSize(0)));
        getJson("/api/purchase-documents/" + purchaseInvoiceOfA, tokenA)
                .andExpect(jsonPath("$.derived", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("a role of another company cannot be assigned to one's own user")
    void cannotAssignForeignRole() throws Exception {
        postJson("/api/users", tokenB, """
                {"firstName":"Cross","lastName":"Company","username":"cross",
                 "email":"%s","status":"ACTIVE","password":"Password123","roleIds":[%d]}
                """.formatted(uniqueEmail("cross"), customRoleOfA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a branch of another company cannot be assigned to one's own user")
    void cannotAssignForeignBranch() throws Exception {
        postJson("/api/users", tokenB, """
                {"firstName":"Cross","lastName":"Company","username":"cross-branch",
                 "email":"%s","status":"ACTIVE","password":"Password123",
                 "branchId":%d,"roleIds":[]}
                """.formatted(uniqueEmail("cross-branch"), branchOfA))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("company A is left untouched by everything company B attempted")
    void companyAIsIntact() throws Exception {
        getJson("/api/users/" + userOfA, tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.firstName").value("Test"));

        getJson("/api/roles/" + customRoleOfA, tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Assistant A"));

        getJson("/api/branches/" + branchOfA, tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TUNIS"));
    }
}
