package com.sales.smartBusiness.company;

import com.sales.smartBusiness.role.Permission;
import com.sales.smartBusiness.role.PermissionModule;
import com.sales.smartBusiness.role.Role;
import com.sales.smartBusiness.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyModulesTest {

    private static Company companyWith(PermissionModule... modules) {
        Company company = new Company();
        company.setEnabledModules(modules.length == 0
                ? EnumSet.noneOf(PermissionModule.class)
                : EnumSet.copyOf(Set.of(modules)));
        return company;
    }

    @Test
    @DisplayName("the six business permission modules can be switched off; the administrative ones cannot")
    void whichModulesAreGated() {
        for (PermissionModule module : new PermissionModule[]{
                PermissionModule.CUSTOMERS, PermissionModule.SUPPLIERS, PermissionModule.PRODUCTS,
                PermissionModule.SALES, PermissionModule.PURCHASES, PermissionModule.STOCK}) {
            assertThat(BusinessModule.gates(module)).as(module.name()).isTrue();
        }
        for (PermissionModule module : new PermissionModule[]{
                PermissionModule.USERS, PermissionModule.ROLES, PermissionModule.BRANCHES,
                PermissionModule.COMPANY, PermissionModule.SECURITY}) {
            assertThat(BusinessModule.gates(module)).as(module.name()).isFalse();
        }
    }

    @Test
    @DisplayName("a company allows the permissions of the modules it keeps, and refuses the others")
    void allowsWhatItKeeps() {
        Company company = companyWith(PermissionModule.CUSTOMERS);

        assertThat(company.allows(Permission.CUSTOMER_VIEW)).isTrue();
        assertThat(company.allows(Permission.SALE_VIEW)).isFalse();
        assertThat(company.allows(Permission.PURCHASE_UPDATE)).isFalse();
        assertThat(company.allows(Permission.STOCK_ADJUST)).isFalse();
    }

    @Test
    @DisplayName("administrative permissions are allowed even by a company with no business module at all")
    void administrationIsAlwaysAllowed() {
        Company company = companyWith();

        assertThat(company.allows(Permission.USER_CREATE)).isTrue();
        assertThat(company.allows(Permission.ROLE_UPDATE)).isTrue();
        assertThat(company.allows(Permission.COMPANY_UPDATE)).isTrue();
        assertThat(company.allows(Permission.AUDIT_VIEW)).isTrue();
        assertThat(company.allows(Permission.CUSTOMER_VIEW)).isFalse();
    }

    @Test
    @DisplayName("a business module is a bundle: Purchases carries Suppliers, Inventory carries Products and Stock")
    void bundlesMoveTogether() {
        Company purchases = companyWith(PermissionModule.PURCHASES, PermissionModule.SUPPLIERS);
        assertThat(purchases.allows(Permission.PURCHASE_VIEW)).isTrue();
        assertThat(purchases.allows(Permission.SUPPLIER_VIEW)).isTrue();
        assertThat(purchases.allows(Permission.PRODUCT_VIEW)).isFalse();

        Company inventory = companyWith(PermissionModule.PRODUCTS, PermissionModule.STOCK);
        assertThat(inventory.allows(Permission.PRODUCT_VIEW)).isTrue();
        assertThat(inventory.allows(Permission.STOCK_VIEW)).isTrue();
        assertThat(inventory.allows(Permission.SUPPLIER_VIEW)).isFalse();
    }

    @Test
    @DisplayName("a user keeps of their roles only what their company may use")
    void userPermissionsFollowTheCompany() {
        Role role = new Role();
        role.setPermissions(EnumSet.of(Permission.USER_VIEW, Permission.CUSTOMER_VIEW, Permission.SALE_VIEW,
                Permission.PURCHASE_VIEW, Permission.STOCK_VIEW));
        User user = new User();
        user.setRoles(Set.of(role));

        user.setCompany(companyWith(PermissionModule.CUSTOMERS, PermissionModule.SALES));
        assertThat(user.collectPermissions())
                .containsExactlyInAnyOrder(Permission.USER_VIEW, Permission.CUSTOMER_VIEW, Permission.SALE_VIEW);

        user.setCompany(companyWith(PermissionModule.CUSTOMERS, PermissionModule.SALES, PermissionModule.PURCHASES,
                PermissionModule.SUPPLIERS, PermissionModule.PRODUCTS, PermissionModule.STOCK));
        assertThat(user.collectPermissions()).hasSize(5);
    }
}
