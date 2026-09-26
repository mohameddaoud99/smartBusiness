package com.sales.smartBusiness.role;

import lombok.Getter;

/**
 * Groups permissions for the administration screen.
 * The label is what the permission matrix displays as a row heading.
 * <p>
 * This is the fine-grained RBAC catalogue — which module a permission belongs to for
 * the company's own role editor. It is unrelated to which modules a company is entitled
 * to use at all (see {@code com.sales.smartBusiness.company.BusinessModule}), a coarser
 * grouping controlled by a platform admin, not by anything here.
 */
@Getter
public enum PermissionModule {

    USERS("Users"),
    ROLES("Roles"),
    BRANCHES("Branches"),
    CUSTOMERS("Customers"),
    SUPPLIERS("Suppliers"),
    PRODUCTS("Products"),
    SALES("Sales"),
    PURCHASES("Purchases"),
    STOCK("Stock"),
    COMPANY("Company"),
    SECURITY("Security");

    private final String label;

    PermissionModule(String label) {
        this.label = label;
    }
}
