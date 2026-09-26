/**
 * Mirrors the backend `Permission` enum. Typed as a union rather than `string` so a
 * misspelt permission in a guard or a template fails to compile instead of silently
 * hiding a button forever.
 *
 * When a business module adds permissions, extend this list.
 */
export type Permission =
  | 'USER_VIEW' | 'USER_CREATE' | 'USER_UPDATE' | 'USER_DISABLE'
  | 'ROLE_VIEW' | 'ROLE_CREATE' | 'ROLE_UPDATE' | 'ROLE_DELETE'
  | 'BRANCH_VIEW' | 'BRANCH_CREATE' | 'BRANCH_UPDATE' | 'BRANCH_DISABLE'
  | 'CUSTOMER_VIEW' | 'CUSTOMER_CREATE' | 'CUSTOMER_UPDATE' | 'CUSTOMER_DELETE'
  | 'SUPPLIER_VIEW' | 'SUPPLIER_CREATE' | 'SUPPLIER_UPDATE' | 'SUPPLIER_DELETE'
  | 'PRODUCT_VIEW' | 'PRODUCT_CREATE' | 'PRODUCT_UPDATE' | 'PRODUCT_DELETE'
  | 'SALE_VIEW' | 'SALE_CREATE' | 'SALE_UPDATE' | 'SALE_CANCEL'
  | 'PURCHASE_VIEW' | 'PURCHASE_CREATE' | 'PURCHASE_UPDATE' | 'PURCHASE_CANCEL'
  | 'STOCK_VIEW' | 'STOCK_ADJUST' | 'STOCK_TRANSFER'
  | 'COMPANY_VIEW' | 'COMPANY_UPDATE'
  | 'AUDIT_VIEW';

export interface RoleSummary {
  id: number;
  name: string;
  label: string;
  system: boolean;
}

/**
 * Mirrors the backend's BusinessModule enum — the modules a platform admin actually
 * toggles per company, coarser than `Permission`/`PermissionModule` on purpose:
 * "Purchases" covers purchase orders and suppliers together, "Inventory" covers
 * products and stock together.
 */
export type BusinessModule = 'CUSTOMERS' | 'SALES' | 'PURCHASES' | 'INVENTORY';

/** What `/api/auth/me` returns — identity, company, effective permissions and modules. */
export interface SessionUser {
  id: number;
  fullName: string;
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  phone?: string;
  companyId: number;
  companyName: string;
  roles: RoleSummary[];
  permissions: Permission[];
  /** Set by a platform admin — this company's own admin cannot change it. */
  enabledModules: BusinessModule[];
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: SessionUser;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  companyName: string;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  phone?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
