export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CHECK' | 'CARD' | 'OTHER';

/** A payment is never deleted: a mistake is cancelled, and the invoice follows. */
export type PaymentStatus = 'ACTIVE' | 'CANCELLED';

export interface PaymentResponse {
  id: number;
  invoiceId: number;
  invoiceReference?: string | null;
  /** The customer of a sales invoice, or the supplier of a purchase invoice. */
  customerName?: string;
  supplierName?: string;
  amount: number;
  paymentDate: string;
  method: PaymentMethod;
  /** What the customer gave to identify it: a cheque number, a transfer reference. */
  reference?: string | null;
  notes?: string | null;
  status: PaymentStatus;
  createdAt: string;
}

export interface PaymentRequest {
  invoiceId: number;
  amount: number;
  paymentDate: string;
  method: PaymentMethod;
  reference?: string | null;
  notes?: string | null;
}

/** Whose payments: what customers pay us, or what we pay suppliers — the same panel and dialog serve both. */
export type PaymentFamily = 'customer' | 'supplier';

/** What the payments panel needs of an invoice, whichever family it belongs to. */
export interface PayableDocument {
  id: number | null;
  status: string;
  reference?: string | null;
  total: number;
  paidAmount?: number;
  creditedAmount?: number;
  balance?: number;
}

export const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'CHECK', label: 'Cheque' },
  { value: 'CARD', label: 'Bank card' },
  { value: 'OTHER', label: 'Other' }
];

export function methodLabel(method: PaymentMethod): string {
  return PAYMENT_METHODS.find(m => m.value === method)?.label ?? method;
}
