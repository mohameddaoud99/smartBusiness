/** Mirrors the backend AuditAction enum. */
export type AuditAction =
  | 'LOGIN_SUCCESS' | 'LOGIN_FAILED'
  | 'USER_CREATED' | 'USER_UPDATED' | 'USER_ROLES_CHANGED'
  | 'USER_ACTIVATED' | 'USER_DISABLED' | 'USER_PASSWORD_RESET' | 'PASSWORD_CHANGED'
  | 'ROLE_CREATED' | 'ROLE_UPDATED' | 'ROLE_DELETED'
  | 'BRANCH_CREATED' | 'BRANCH_UPDATED' | 'BRANCH_ACTIVATED' | 'BRANCH_DISABLED'
  | 'COMPANY_UPDATED';

export type AuditEntity = 'USER' | 'ROLE' | 'BRANCH' | 'COMPANY';

export interface AuditLogResponse {
  id: number;
  action: AuditAction;
  /** Readable label produced by the backend — never print the enum name. */
  actionLabel: string;
  entityType?: AuditEntity;
  entityId?: number;
  detail?: string;
  userId?: number;
  username: string;
  ipAddress?: string;
  occurredAt: string;
}

export const ENTITY_OPTIONS: { label: string; value: AuditEntity }[] = [
  { label: 'Users', value: 'USER' },
  { label: 'Roles', value: 'ROLE' },
  { label: 'Branches', value: 'BRANCH' },
  { label: 'Company', value: 'COMPANY' }
];

export const ACTION_OPTIONS: { label: string; value: AuditAction }[] = [
  { label: 'Signed in', value: 'LOGIN_SUCCESS' },
  { label: 'Sign-in refused', value: 'LOGIN_FAILED' },
  { label: 'User created', value: 'USER_CREATED' },
  { label: 'User updated', value: 'USER_UPDATED' },
  { label: 'User roles changed', value: 'USER_ROLES_CHANGED' },
  { label: 'User activated', value: 'USER_ACTIVATED' },
  { label: 'User deactivated', value: 'USER_DISABLED' },
  { label: 'Password reset', value: 'USER_PASSWORD_RESET' },
  { label: 'Password changed', value: 'PASSWORD_CHANGED' },
  { label: 'Role created', value: 'ROLE_CREATED' },
  { label: 'Role updated', value: 'ROLE_UPDATED' },
  { label: 'Role deleted', value: 'ROLE_DELETED' },
  { label: 'Branch created', value: 'BRANCH_CREATED' },
  { label: 'Branch updated', value: 'BRANCH_UPDATED' },
  { label: 'Branch activated', value: 'BRANCH_ACTIVATED' },
  { label: 'Branch deactivated', value: 'BRANCH_DISABLED' },
  { label: 'Company settings updated', value: 'COMPANY_UPDATED' }
];

/** One icon and one colour per action, shared by the audit screen and the user drawer. */
export function auditIcon(action: AuditAction): string {
  switch (action) {
    case 'LOGIN_SUCCESS':       return 'pi pi-sign-in';
    case 'LOGIN_FAILED':        return 'pi pi-exclamation-triangle';
    case 'USER_CREATED':        return 'pi pi-user-plus';
    case 'USER_ROLES_CHANGED':  return 'pi pi-key';
    case 'USER_ACTIVATED':      return 'pi pi-check';
    case 'USER_DISABLED':       return 'pi pi-ban';
    case 'USER_PASSWORD_RESET':
    case 'PASSWORD_CHANGED':    return 'pi pi-lock';
    case 'ROLE_CREATED':        return 'pi pi-key';
    case 'ROLE_DELETED':        return 'pi pi-trash';
    case 'BRANCH_CREATED':      return 'pi pi-building';
    case 'BRANCH_DISABLED':     return 'pi pi-ban';
    case 'BRANCH_ACTIVATED':    return 'pi pi-check';
    case 'COMPANY_UPDATED':     return 'pi pi-cog';
    default:                    return 'pi pi-pencil';
  }
}

export function auditColor(action: AuditAction): string {
  switch (action) {
    case 'LOGIN_FAILED':
    case 'USER_DISABLED':
    case 'BRANCH_DISABLED':
    case 'ROLE_DELETED':
      return 'var(--p-red-500)';
    case 'LOGIN_SUCCESS':
    case 'USER_ACTIVATED':
    case 'BRANCH_ACTIVATED':
      return 'var(--p-green-500)';
    case 'USER_CREATED':
    case 'ROLE_CREATED':
    case 'BRANCH_CREATED':
      return 'var(--p-primary-color)';
    default:
      return 'var(--p-surface-400)';
  }
}
