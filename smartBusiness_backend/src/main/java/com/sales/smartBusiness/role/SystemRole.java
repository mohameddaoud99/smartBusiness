package com.sales.smartBusiness.role;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.sales.smartBusiness.role.Permission.*;

/**
 * Standard roles shipped with the application. Every company gets its own copy of
 * each one at registration; they cannot be renamed, edited or deleted.
 * <p>
 * Changing a set here propagates to all existing companies at the next startup
 * (see {@code RoleService#syncSystemRoles}).
 */
@Getter
public enum SystemRole {

    COMPANY_ADMIN("Company Administrator",
            "Full access, including users, roles and company settings",
            EnumSet.allOf(Permission.class)),

    SALES_MANAGER("Sales Manager",
            "Runs the sales cycle and manages the customer base",
            EnumSet.of(CUSTOMER_VIEW, CUSTOMER_CREATE, CUSTOMER_UPDATE, CUSTOMER_DELETE,
                    SALE_VIEW, SALE_CREATE, SALE_UPDATE, SALE_CANCEL,
                    PRODUCT_VIEW, STOCK_VIEW)),

    SALES_AGENT("Sales Agent",
            "Records sales and adds customers",
            EnumSet.of(CUSTOMER_VIEW, CUSTOMER_CREATE,
                    SALE_VIEW, SALE_CREATE,
                    PRODUCT_VIEW, STOCK_VIEW)),

    PURCHASE_MANAGER("Purchase Manager",
            "Runs the purchasing cycle and manages suppliers",
            EnumSet.of(SUPPLIER_VIEW, SUPPLIER_CREATE, SUPPLIER_UPDATE, SUPPLIER_DELETE,
                    PURCHASE_VIEW, PURCHASE_CREATE, PURCHASE_UPDATE, PURCHASE_CANCEL,
                    PRODUCT_VIEW, STOCK_VIEW)),

    STOCK_MANAGER("Stock Manager",
            "Manages the catalogue and stock levels",
            EnumSet.of(PRODUCT_VIEW, PRODUCT_CREATE, PRODUCT_UPDATE, PRODUCT_DELETE,
                    STOCK_VIEW, STOCK_ADJUST, STOCK_TRANSFER,
                    SALE_VIEW, PURCHASE_VIEW, BRANCH_VIEW)),

    ACCOUNTANT("Accountant",
            "Read access to everything that carries an amount",
            EnumSet.of(CUSTOMER_VIEW, SUPPLIER_VIEW, PRODUCT_VIEW,
                    SALE_VIEW, PURCHASE_VIEW)),

    VIEWER("Viewer",
            "Read-only access to the business modules",
            EnumSet.of(CUSTOMER_VIEW, SUPPLIER_VIEW, PRODUCT_VIEW,
                    SALE_VIEW, PURCHASE_VIEW, STOCK_VIEW));

    private final String label;
    private final String description;
    private final Set<Permission> permissions;

    SystemRole(String label, String description, Set<Permission> permissions) {
        this.label = label;
        this.description = description;
        this.permissions = Collections.unmodifiableSet(permissions);
    }
}
