import { Routes } from '@angular/router';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { authGuard, permissionGuard } from './core/auth/auth.guard';
import { platformAuthGuard } from './core/platform-auth/platform-auth.guard';

/**
 * Quotes and sales orders are the same screens on a different document type: a list, the
 * "new" editor and the editor of a saved document, all told apart by `data.documentType`.
 * `new` comes before `:id` so it is not read as an id.
 */
function salesDocumentRoutes(path: string, documentType: string, plural: string, label: string): Routes[number] {
  return {
    path,
    data: { breadcrumb: plural },
    children: [
      {
        path: '',
        canActivate: [permissionGuard],
        data: { breadcrumb: '', permission: 'SALE_VIEW', documentType },
        loadComponent: () =>
          import('./features/sales/sales-documents.component').then(m => m.SalesDocumentsComponent)
      },
      {
        path: 'new',
        canActivate: [permissionGuard],
        data: { breadcrumb: `New ${label}`, permission: 'SALE_CREATE', documentType },
        loadComponent: () =>
          import('./features/sales/sales-document-editor/sales-document-editor.component')
            .then(m => m.SalesDocumentEditorComponent)
      },
      {
        path: ':id',
        canActivate: [permissionGuard],
        data: { breadcrumb: label, permission: 'SALE_VIEW', documentType },
        loadComponent: () =>
          import('./features/sales/sales-document-editor/sales-document-editor.component')
            .then(m => m.SalesDocumentEditorComponent)
      }
    ]
  };
}

/** The purchase side of {@link salesDocumentRoutes}: purchase orders and goods receipts, on PURCHASE_* rights. */
function purchaseDocumentRoutes(path: string, documentType: string, plural: string, label: string): Routes[number] {
  return {
    path,
    data: { breadcrumb: plural },
    children: [
      {
        path: '',
        canActivate: [permissionGuard],
        data: { breadcrumb: '', permission: 'PURCHASE_VIEW', documentType },
        loadComponent: () =>
          import('./features/purchases/purchase-documents.component').then(m => m.PurchaseDocumentsComponent)
      },
      {
        path: 'new',
        canActivate: [permissionGuard],
        data: { breadcrumb: `New ${label}`, permission: 'PURCHASE_CREATE', documentType },
        loadComponent: () =>
          import('./features/purchases/purchase-document-editor/purchase-document-editor.component')
            .then(m => m.PurchaseDocumentEditorComponent)
      },
      {
        path: ':id',
        canActivate: [permissionGuard],
        data: { breadcrumb: label, permission: 'PURCHASE_VIEW', documentType },
        loadComponent: () =>
          import('./features/purchases/purchase-document-editor/purchase-document-editor.component')
            .then(m => m.PurchaseDocumentEditorComponent)
      }
    ]
  };
}

export const routes: Routes = [
  // Public — outside MainLayout, so no sidebar and no topbar
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent)
  },

  // The platform portal is its own world: separate login, separate token, separate
  // layout with no sidebar. It never shares a route tree with the company app.
  {
    path: 'platform/login',
    loadComponent: () =>
      import('./features/platform/login/platform-login.component').then(m => m.PlatformLoginComponent)
  },
  {
    path: 'platform',
    canActivate: [platformAuthGuard],
    loadComponent: () =>
      import('./features/platform/platform-layout/platform-layout.component')
        .then(m => m.PlatformLayoutComponent),
    children: [
      { path: '', redirectTo: 'companies', pathMatch: 'full' },
      {
        path: 'companies',
        loadComponent: () =>
          import('./features/platform/companies/platform-companies.component')
            .then(m => m.PlatformCompaniesComponent)
      }
    ]
  },

  // The print preview is a sheet of its own — no sidebar, no topbar — opened in a new tab.
  // One route per family of documents; the sheet itself is the same for all of them.
  {
    path: 'print',
    canActivate: [authGuard],
    children: [
      {
        path: 'sales/:id',
        canActivate: [permissionGuard],
        data: { permission: 'SALE_VIEW', family: 'sales' },
        loadComponent: () =>
          import('./features/print/document-print.component').then(m => m.DocumentPrintComponent)
      },
      {
        path: 'purchases/:id',
        canActivate: [permissionGuard],
        data: { permission: 'PURCHASE_VIEW', family: 'purchases' },
        loadComponent: () =>
          import('./features/print/document-print.component').then(m => m.DocumentPrintComponent)
      }
    ]
  },

  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        data: { breadcrumb: 'Dashboard' },
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'users',
        canActivate: [permissionGuard],
        data: { breadcrumb: 'Users', permission: 'USER_VIEW' },
        loadComponent: () =>
          import('./features/users/users.component').then(m => m.UsersComponent)
      },
      {
        path: 'roles',
        canActivate: [permissionGuard],
        data: { breadcrumb: 'Roles', permission: 'ROLE_VIEW' },
        loadComponent: () =>
          import('./features/roles/roles.component').then(m => m.RolesComponent)
      },
      {
        path: 'customers',
        canActivate: [permissionGuard],
        data: { breadcrumb: 'Customers', permission: 'CUSTOMER_VIEW' },
        loadComponent: () =>
          import('./features/customers/customers.component').then(m => m.CustomersComponent)
      },
      {
        path: 'suppliers',
        canActivate: [permissionGuard],
        data: { breadcrumb: 'Suppliers', permission: 'SUPPLIER_VIEW' },
        loadComponent: () =>
          import('./features/suppliers/suppliers.component').then(m => m.SuppliersComponent)
      },
      {
        path: 'products',
        canActivate: [permissionGuard],
        data: { breadcrumb: 'Products', permission: 'PRODUCT_VIEW' },
        loadComponent: () =>
          import('./features/products/products.component').then(m => m.ProductsComponent)
      },
      {
        path: 'stock',
        data: { breadcrumb: 'Stock' },
        children: [
          {
            path: '',
            canActivate: [permissionGuard],
            data: { breadcrumb: '', permission: 'STOCK_VIEW' },
            loadComponent: () =>
              import('./features/stock/stock.component').then(m => m.StockComponent)
          },
          {
            path: 'movements',
            canActivate: [permissionGuard],
            data: { breadcrumb: 'Movements', permission: 'STOCK_VIEW' },
            loadComponent: () =>
              import('./features/stock/stock-movements.component').then(m => m.StockMovementsComponent)
          }
        ]
      },
      salesDocumentRoutes('quotes', 'QUOTE', 'Quotes', 'Quote'),
      salesDocumentRoutes('sales-orders', 'SALES_ORDER', 'Sales orders', 'Sales order'),
      salesDocumentRoutes('delivery-notes', 'DELIVERY_NOTE', 'Delivery notes', 'Delivery note'),
      salesDocumentRoutes('invoices', 'INVOICE', 'Invoices', 'Invoice'),
      salesDocumentRoutes('credit-notes', 'CREDIT_NOTE', 'Credit notes', 'Credit note'),
      salesDocumentRoutes('return-notes', 'RETURN_NOTE', 'Return notes', 'Return note'),
      purchaseDocumentRoutes('purchase-orders', 'PURCHASE_ORDER', 'Purchase orders', 'Purchase order'),
      purchaseDocumentRoutes('goods-receipts', 'GOODS_RECEIPT', 'Goods receipts', 'Goods receipt'),
      purchaseDocumentRoutes('purchase-invoices', 'PURCHASE_INVOICE', 'Purchase invoices', 'Purchase invoice'),
      purchaseDocumentRoutes('purchase-credit-notes', 'PURCHASE_CREDIT_NOTE', 'Supplier credit notes', 'Supplier credit note'),
      purchaseDocumentRoutes('purchase-return-notes', 'PURCHASE_RETURN_NOTE', 'Supplier return notes', 'Supplier return note'),
      {
        path: 'branches',
        canActivate: [permissionGuard],
        data: { breadcrumb: 'Branches', permission: 'BRANCH_VIEW' },
        loadComponent: () =>
          import('./features/branches/branches.component').then(m => m.BranchesComponent)
      },
      {
        path: 'audit',
        canActivate: [permissionGuard],
        data: { breadcrumb: 'Audit log', permission: 'AUDIT_VIEW' },
        loadComponent: () =>
          import('./features/audit/audit.component').then(m => m.AuditComponent)
      },
      {
        path: 'settings',
        data: { breadcrumb: 'Settings' },
        children: [
          {
            path: '',
            data: { breadcrumb: '' },
            loadComponent: () =>
              import('./features/settings/settings.component').then(m => m.SettingsComponent)
          },
          {
            path: 'profile',
            data: { breadcrumb: 'My profile' },
            loadComponent: () =>
              import('./features/settings/profile/profile.component').then(m => m.ProfileComponent)
          },
          {
            path: 'company',
            canActivate: [permissionGuard],
            data: { breadcrumb: 'Company', permission: 'COMPANY_VIEW' },
            loadComponent: () =>
              import('./features/settings/company/company-settings.component')
                .then(m => m.CompanySettingsComponent)
          },
          {
            path: 'bank-accounts',
            canActivate: [permissionGuard],
            data: { breadcrumb: 'Bank accounts', permission: 'COMPANY_VIEW' },
            loadComponent: () =>
              import('./features/settings/bank-accounts/bank-accounts.component')
                .then(m => m.BankAccountsComponent)
          },
          {
            path: 'taxes',
            canActivate: [permissionGuard],
            data: { breadcrumb: 'Taxes', permission: 'COMPANY_VIEW' },
            loadComponent: () =>
              import('./features/settings/taxes/taxes.component').then(m => m.TaxesComponent)
          },
          {
            path: 'numbering',
            canActivate: [permissionGuard],
            data: { breadcrumb: 'Document numbering', permission: 'COMPANY_VIEW' },
            loadComponent: () =>
              import('./features/settings/numbering/numbering.component')
                .then(m => m.NumberingComponent)
          },
          {
            path: 'categories',
            canActivate: [permissionGuard],
            data: { breadcrumb: 'Categories', permission: 'PRODUCT_VIEW' },
            loadComponent: () =>
              import('./features/settings/categories/categories.component')
                .then(m => m.CategoriesComponent)
          },
          {
            path: 'warehouses',
            canActivate: [permissionGuard],
            data: { breadcrumb: 'Warehouses', permission: 'STOCK_VIEW' },
            loadComponent: () =>
              import('./features/settings/warehouses/warehouses.component')
                .then(m => m.WarehousesComponent)
          },
          {
            path: 'brands',
            canActivate: [permissionGuard],
            data: { breadcrumb: 'Brands', permission: 'PRODUCT_VIEW' },
            loadComponent: () =>
              import('./features/settings/brands/brands.component')
                .then(m => m.BrandsComponent)
          }
        ]
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
