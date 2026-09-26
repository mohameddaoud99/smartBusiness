import { BusinessModule, Permission } from '../core/auth/session.model';

/**
 * Sidebar navigation model.
 *
 * Items marked `disabled` are modules not implemented yet — they stay visible so
 * the navigation reflects the real product scope, but they are not clickable.
 *
 * An item carrying a `permission` disappears entirely for users who lack it, and one
 * carrying a `module` disappears for a company whose plan does not include it — set by
 * a platform admin, never by the company itself. A group whose children all disappear
 * goes with them. That is comfort, not security: the route guard and the backend are
 * what actually refuse access.
 */

export interface NavItem {
  label: string;
  icon: string;
  route?: string;
  disabled?: boolean;
  permission?: Permission;
  module?: BusinessModule;
  /**
   * Highlight only on this exact URL. Needed when the route of another item starts with this
   * one's (`/stock` and `/stock/movements`): by default a link stays active on every URL
   * below it, so both entries would light up together.
   */
  exact?: boolean;
  children?: NavItem[];
}

export interface NavSection {
  label?: string;
  items: NavItem[];
}

export const NAVIGATION: NavSection[] = [
  {
    items: [
      { label: 'Dashboard', icon: 'pi pi-th-large', route: '/dashboard' }
    ]
  },
  {
    label: 'Operations',
    items: [
      {
        label: 'Sales',
        icon: 'pi pi-shopping-cart',
        module: 'SALES',
        children: [
          { label: 'Quotes', icon: 'pi pi-file-edit', route: '/quotes', permission: 'SALE_VIEW' },
          { label: 'Sales orders', icon: 'pi pi-list', route: '/sales-orders', permission: 'SALE_VIEW' },
          { label: 'Delivery notes', icon: 'pi pi-send', route: '/delivery-notes', permission: 'SALE_VIEW' },
          { label: 'Invoices', icon: 'pi pi-file', route: '/invoices', permission: 'SALE_VIEW' },
          { label: 'Credit notes', icon: 'pi pi-replay', route: '/credit-notes', permission: 'SALE_VIEW' },
          { label: 'Return notes', icon: 'pi pi-undo', route: '/return-notes', permission: 'SALE_VIEW' }
        ]
      },
      {
        label: 'Purchases',
        icon: 'pi pi-truck',
        module: 'PURCHASES',
        children: [
          { label: 'Purchase orders', icon: 'pi pi-list', route: '/purchase-orders', permission: 'PURCHASE_VIEW' },
          { label: 'Goods receipts', icon: 'pi pi-inbox', route: '/goods-receipts', permission: 'PURCHASE_VIEW' },
          { label: 'Purchase invoices', icon: 'pi pi-file', route: '/purchase-invoices', permission: 'PURCHASE_VIEW' },
          { label: 'Supplier credit notes', icon: 'pi pi-replay', route: '/purchase-credit-notes', permission: 'PURCHASE_VIEW' },
          { label: 'Supplier return notes', icon: 'pi pi-undo', route: '/purchase-return-notes', permission: 'PURCHASE_VIEW' }
        ]
      },
      {
        label: 'Inventory',
        icon: 'pi pi-box',
        module: 'INVENTORY',
        children: [
          { label: 'Products', icon: 'pi pi-tag', route: '/products', permission: 'PRODUCT_VIEW' },
          { label: 'Stock', icon: 'pi pi-chart-bar', route: '/stock', permission: 'STOCK_VIEW', exact: true },
          { label: 'Movements', icon: 'pi pi-arrow-right-arrow-left', route: '/stock/movements', permission: 'STOCK_VIEW' }
        ]
      },
      {
        label: 'Customers',
        icon: 'pi pi-users',
        route: '/customers',
        permission: 'CUSTOMER_VIEW',
        module: 'CUSTOMERS'
      },
      {
        label: 'Suppliers',
        icon: 'pi pi-briefcase',
        route: '/suppliers',
        permission: 'SUPPLIER_VIEW',
        module: 'PURCHASES'
      }
    ]
  },
  {
    label: 'Administration',
    items: [
      {
        label: 'Users & Security',
        icon: 'pi pi-shield',
        children: [
          { label: 'Users', icon: 'pi pi-user', route: '/users', permission: 'USER_VIEW' },
          { label: 'Roles', icon: 'pi pi-key', route: '/roles', permission: 'ROLE_VIEW' }
        ]
      },
      { label: 'Branches', icon: 'pi pi-building', route: '/branches', permission: 'BRANCH_VIEW' },
      { label: 'Audit log', icon: 'pi pi-history', route: '/audit', permission: 'AUDIT_VIEW' },
      { label: 'Reports', icon: 'pi pi-chart-line', disabled: true },
      { label: 'Settings', icon: 'pi pi-cog', route: '/settings' }
    ]
  }
];
