package com.sales.smartBusiness.role;

import lombok.Getter;

/**
 * The complete catalogue of what a user may do. There is no permissions table:
 * this enum is the source of truth and role_permissions stores the name.
 * <p>
 * Adding a business module means adding values here — the permission matrix, the
 * role editor and the Angular menu all read the catalogue and adapt on their own.
 * <p>
 * Permissions never carry a branch: they apply to the whole company.
 */
@Getter
public enum Permission {

    USER_VIEW(PermissionModule.USERS, "View users"),
    USER_CREATE(PermissionModule.USERS, "Create users"),
    USER_UPDATE(PermissionModule.USERS, "Update users"),
    USER_DISABLE(PermissionModule.USERS, "Enable and disable users"),

    ROLE_VIEW(PermissionModule.ROLES, "View roles"),
    ROLE_CREATE(PermissionModule.ROLES, "Create roles"),
    ROLE_UPDATE(PermissionModule.ROLES, "Update roles"),
    ROLE_DELETE(PermissionModule.ROLES, "Delete roles"),

    BRANCH_VIEW(PermissionModule.BRANCHES, "View branches"),
    BRANCH_CREATE(PermissionModule.BRANCHES, "Create branches"),
    BRANCH_UPDATE(PermissionModule.BRANCHES, "Update branches"),
    BRANCH_DISABLE(PermissionModule.BRANCHES, "Enable and disable branches"),

    CUSTOMER_VIEW(PermissionModule.CUSTOMERS, "View customers"),
    CUSTOMER_CREATE(PermissionModule.CUSTOMERS, "Create customers"),
    CUSTOMER_UPDATE(PermissionModule.CUSTOMERS, "Update customers"),
    CUSTOMER_DELETE(PermissionModule.CUSTOMERS, "Delete customers"),

    SUPPLIER_VIEW(PermissionModule.SUPPLIERS, "View suppliers"),
    SUPPLIER_CREATE(PermissionModule.SUPPLIERS, "Create suppliers"),
    SUPPLIER_UPDATE(PermissionModule.SUPPLIERS, "Update suppliers"),
    SUPPLIER_DELETE(PermissionModule.SUPPLIERS, "Delete suppliers"),

    PRODUCT_VIEW(PermissionModule.PRODUCTS, "View products"),
    PRODUCT_CREATE(PermissionModule.PRODUCTS, "Create products"),
    PRODUCT_UPDATE(PermissionModule.PRODUCTS, "Update products"),
    PRODUCT_DELETE(PermissionModule.PRODUCTS, "Delete products"),

    SALE_VIEW(PermissionModule.SALES, "View sales"),
    SALE_CREATE(PermissionModule.SALES, "Create sales"),
    SALE_UPDATE(PermissionModule.SALES, "Update sales"),
    SALE_CANCEL(PermissionModule.SALES, "Cancel sales"),

    PURCHASE_VIEW(PermissionModule.PURCHASES, "View purchases"),
    PURCHASE_CREATE(PermissionModule.PURCHASES, "Create purchases"),
    PURCHASE_UPDATE(PermissionModule.PURCHASES, "Update purchases"),
    PURCHASE_CANCEL(PermissionModule.PURCHASES, "Cancel purchases"),

    STOCK_VIEW(PermissionModule.STOCK, "View stock"),
    STOCK_ADJUST(PermissionModule.STOCK, "Adjust stock"),
    STOCK_TRANSFER(PermissionModule.STOCK, "Transfer stock between branches"),

    COMPANY_VIEW(PermissionModule.COMPANY, "View company settings"),
    COMPANY_UPDATE(PermissionModule.COMPANY, "Update company settings"),

    AUDIT_VIEW(PermissionModule.SECURITY, "View the audit log");

    private final PermissionModule module;
    private final String label;

    Permission(PermissionModule module, String label) {
        this.module = module;
        this.label = label;
    }
}
