import { Permission } from '../../core/auth/session.model';

export interface RoleResponse {
  id: number;
  /** Stable key derived from the label at creation — read-only afterwards. */
  name: string;
  label: string;
  description?: string;
  /** Shipped with the application: cannot be edited or deleted. */
  system: boolean;
  permissions: Permission[];
  userCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface RoleRequest {
  label: string;
  description?: string;
  permissions: Permission[];
}

export interface PermissionOption {
  name: Permission;
  label: string;
}

/** One row of the permission matrix, as served by GET /api/roles/permissions. */
export interface PermissionModuleGroup {
  module: string;
  label: string;
  permissions: PermissionOption[];
}
