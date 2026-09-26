import { RoleSummary } from '../../core/auth/session.model';
import { BranchSummary } from '../branches/branch.model';

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED';

export interface UserResponse {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  username: string;
  email: string;
  phone?: string;
  status: UserStatus;
  /** Which site this user is based at — organisational, never a permission scope. */
  branch?: BranchSummary;
  /** A user may hold several roles; their permissions are the union. */
  roles: RoleSummary[];
  lastLoginAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserRequest {
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  phone?: string;
  status: UserStatus;
  /** Optional — a user does not have to be tied to a single site. */
  branchId?: number | null;
  /** May be empty — a user with no role can sign in but reaches nothing. */
  roleIds: number[];
  password?: string;
}

export const STATUS_OPTIONS: { label: string; value: UserStatus }[] = [
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Inactive', value: 'INACTIVE' },
  { label: 'Locked', value: 'LOCKED' }
];

export function statusLabel(status: UserStatus): string {
  return STATUS_OPTIONS.find(option => option.value === status)?.label ?? status;
}

export function statusSeverity(status: UserStatus): 'success' | 'secondary' | 'danger' {
  if (status === 'ACTIVE') return 'success';
  return status === 'LOCKED' ? 'danger' : 'secondary';
}
