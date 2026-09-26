package com.sales.smartBusiness.company;

import com.sales.smartBusiness.role.PermissionModule;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The modules a platform admin actually sells and toggles for a company — coarser
 * than the RBAC permission catalogue on purpose.
 * <p>
 * A company's own role editor still grants permissions at the finer
 * {@link PermissionModule} granularity (Suppliers separately from Purchases, Products
 * separately from Stock) — that is about <em>who inside the company</em> may do what.
 * This enum is about <em>what the company may use at all</em>, and matches the modules
 * a user actually navigates to: "Purchases" covers purchase orders and suppliers in one
 * switch, "Inventory" covers products and stock in one switch. Toggling one on or off
 * moves every {@link PermissionModule} it lists together — never a partial module.
 */
@Getter
public enum BusinessModule {

    CUSTOMERS("Customers", EnumSet.of(PermissionModule.CUSTOMERS)),
    SALES("Sales", EnumSet.of(PermissionModule.SALES)),
    PURCHASES("Purchases", EnumSet.of(PermissionModule.PURCHASES, PermissionModule.SUPPLIERS)),
    INVENTORY("Inventory", EnumSet.of(PermissionModule.PRODUCTS, PermissionModule.STOCK));

    private final String label;
    private final Set<PermissionModule> permissionModules;

    BusinessModule(String label, Set<PermissionModule> permissionModules) {
        this.label = label;
        this.permissionModules = Collections.unmodifiableSet(permissionModules);
    }

    /** Every company starts with all of it — a platform admin narrows it down later. */
    public static Set<PermissionModule> allPermissionModules() {
        return Arrays.stream(values())
                .flatMap(module -> module.permissionModules.stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionModule.class)));
    }

    /**
     * Whether a permission module is one a platform admin can switch off. The administrative ones (users,
     * roles, branches, company, security) never are: a company always has them.
     */
    public static boolean gates(PermissionModule module) {
        return allPermissionModules().contains(module);
    }

    /**
     * Which business modules a company can actually use, given its stored
     * {@code Set<PermissionModule>}. A module counts as enabled only when every
     * {@link PermissionModule} it stands for is present — matches how a platform admin
     * always writes them together. Shared by the platform screen (sees every company)
     * and the session payload (a company user sees only their own).
     */
    public static Set<BusinessModule> enabledAmong(Set<PermissionModule> companyModules) {
        return Arrays.stream(values())
                .filter(module -> companyModules.containsAll(module.permissionModules))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(BusinessModule.class)));
    }
}
