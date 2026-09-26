import { Address } from '../../core/models/address.model';

export type { Address };

export type SupplierType = 'COMPANY' | 'INDIVIDUAL';

export interface SupplierResponse {
  id: number;
  type: SupplierType;
  reference: string;
  name: string;
  contactName?: string;
  email?: string;
  phone?: string;
  /** Company supplier — tax registration number ("matricule fiscal"). */
  taxId?: string;
  /** Individual supplier — national id card number ("CIN"). */
  nationalId?: string;
  /** Individual supplier — ISO date, e.g. "1985-03-20". */
  birthDate?: string;
  billingAddress?: Address;
  shippingAddress?: Address;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierRequest {
  type: SupplierType;
  /** Optional — the backend generates "F-0001" when left blank on creation. */
  reference?: string;
  name: string;
  contactName?: string;
  email?: string;
  phone?: string;
  taxId?: string;
  nationalId?: string;
  birthDate?: string | null;
  billingAddress?: Address | null;
  shippingAddress?: Address | null;
  notes?: string;
}

export const TYPE_OPTIONS: { label: string; value: SupplierType }[] = [
  { label: 'Business', value: 'COMPANY' },
  { label: 'Individual', value: 'INDIVIDUAL' }
];

export function typeLabel(type: SupplierType): string {
  return type === 'COMPANY' ? 'Business' : 'Individual';
}
