import { Address } from '../../core/models/address.model';

export type CustomerType = 'COMPANY' | 'INDIVIDUAL';

export type { Address };

export interface CustomerResponse {
  id: number;
  type: CustomerType;
  reference: string;
  name: string;
  contactName?: string;
  email?: string;
  phone?: string;
  /** Company customer — tax registration number ("matricule fiscal"). */
  taxId?: string;
  /** Individual customer — national id card number ("CIN"). */
  nationalId?: string;
  /** Individual customer — ISO date, e.g. "1990-05-14". */
  birthDate?: string;
  /** Reference of a VAT-suspension permit, when the customer holds one. */
  vatSuspensionNumber?: string;
  billingAddress?: Address;
  shippingAddress?: Address;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerRequest {
  type: CustomerType;
  /** Optional — the backend generates "C-0001" when left blank on creation. */
  reference?: string;
  name: string;
  contactName?: string;
  email?: string;
  phone?: string;
  taxId?: string;
  nationalId?: string;
  birthDate?: string | null;
  vatSuspensionNumber?: string;
  billingAddress?: Address | null;
  shippingAddress?: Address | null;
  notes?: string;
}

export const TYPE_OPTIONS: { label: string; value: CustomerType }[] = [
  { label: 'Business', value: 'COMPANY' },
  { label: 'Individual', value: 'INDIVIDUAL' }
];

export function typeLabel(type: CustomerType): string {
  return type === 'COMPANY' ? 'Business' : 'Individual';
}
