import { TaxKind } from '../settings/taxes/tax.model';

export type PurchaseDocumentType =
  'PURCHASE_ORDER' | 'GOODS_RECEIPT' | 'PURCHASE_INVOICE' | 'PURCHASE_CREDIT_NOTE' | 'PURCHASE_RETURN_NOTE';

/**
 * The three statuses Finco shows for the supplier order and the receipt, plus the two a purchase invoice
 * moves through as it is paid: a validated invoice nobody has paid is "unpaid".
 */
export type PurchaseDocumentStatus = 'DRAFT' | 'VALIDATED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export interface PurchaseDocumentLine {
  id?: number;
  productId?: number | null;
  reference?: string | null;
  designation: string;
  quantity: number;
  unitPrice: number;
  discountRate: number;
  vatTaxId?: number | null;
  /** Read only: the rate as it was when the document was saved. */
  vatRate?: number;
  lineTotal?: number;
  /** The line of the source document this one follows (a draft made from an order, a delivery note...). Sent back as it came. */
  sourceLineId?: number | null;
  /** Read only. On an order: what delivery notes have taken of the line; on a delivery note or an invoice: what return notes have. */
  fulfilledQuantity?: number | null;
  remainingQuantity?: number | null;
  /** Read only. On a draft that follows a source line: how much of that line is still free to take. */
  sourceRemaining?: number | null;
}

export interface PurchaseDocumentTaxRow {
  /** The company tax it came from; null for a VAT row. */
  taxId?: number | null;
  kind: TaxKind;
  name: string;
  rate?: number | null;
  base?: number | null;
  amount: number;
}

export interface PurchaseDocumentSummary {
  id: number;
  type: PurchaseDocumentType;
  status: PurchaseDocumentStatus;
  reference?: string | null;
  supplierId: number;
  supplierName: string;
  issueDate: string;
  dueDate?: string | null;
  total: number;
  /** Invoice only: what has been paid, and what is left — both come from the server. */
  paidAmount?: number;
  creditedAmount?: number;
  balance?: number;
  createdAt: string;
}

export interface PurchaseDocumentResponse {
  id: number | null;
  type: PurchaseDocumentType;
  status: PurchaseDocumentStatus;
  reference?: string | null;
  supplierId?: number | null;
  supplierName?: string | null;
  /** Where a goods receipt or an invoice puts its stock. Null for a purchase order. */
  warehouseId?: number | null;
  warehouseName?: string | null;
  sourceId?: number | null;
  sourceReference?: string | null;
  /** What the source is: an invoice comes from a purchase order or a goods receipt, a credit note from an invoice. */
  sourceType?: PurchaseDocumentType | null;
  issueDate: string;
  dueDate?: string | null;
  subtotal: number;
  total: number;
  paidAmount?: number;
  /** Invoice only: what its credit notes take off. */
  creditedAmount?: number;
  balance?: number;
  notes?: string | null;
  terms?: string | null;
  lines: PurchaseDocumentLine[];
  taxes: PurchaseDocumentTaxRow[];
  /** The documents made from this one (the receipts of a purchase order). */
  derived: PurchaseDocumentSummary[];
  createdAt?: string;
  updatedAt?: string;
}

export interface PurchaseDocumentRequest {
  type: PurchaseDocumentType;
  supplierId: number;
  warehouseId?: number | null;
  issueDate: string;
  dueDate?: string | null;
  notes?: string | null;
  terms?: string | null;
  taxIds: number[];
  lines: PurchaseDocumentLine[];
}

/** What differs between the document types on screen — everything else is shared. */
export interface PurchaseDocumentConfig {
  label: string;
  plural: string;
  /** Route prefix: `/purchase-orders`, `/goods-receipts`. */
  path: string;
  description: string;
  statuses: PurchaseDocumentStatus[];
}

export const PURCHASE_CONFIGS: Record<PurchaseDocumentType, PurchaseDocumentConfig> = {
  PURCHASE_ORDER: {
    label: 'Purchase order',
    plural: 'Purchase orders',
    path: '/purchase-orders',
    description: 'What you have ordered from your suppliers',
    statuses: ['DRAFT', 'VALIDATED', 'CANCELLED']
  },
  GOODS_RECEIPT: {
    label: 'Goods receipt',
    plural: 'Goods receipts',
    path: '/goods-receipts',
    description: 'Goods that have arrived — validating one adds them to the stock',
    statuses: ['DRAFT', 'VALIDATED', 'CANCELLED']
  },
  PURCHASE_INVOICE: {
    label: 'Purchase invoice',
    plural: 'Purchase invoices',
    path: '/purchase-invoices',
    description: 'What your suppliers ask you to pay — validating one brings its goods in, payments settle it',
    statuses: ['DRAFT', 'VALIDATED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED']
  },
  PURCHASE_CREDIT_NOTE: {
    label: 'Supplier credit note',
    plural: 'Supplier credit notes',
    path: '/purchase-credit-notes',
    description: 'Corrections to purchase invoices — validating one lowers what you owe the supplier',
    statuses: ['DRAFT', 'VALIDATED', 'CANCELLED']
  },
  PURCHASE_RETURN_NOTE: {
    label: 'Supplier return note',
    plural: 'Supplier return notes',
    path: '/purchase-return-notes',
    description: 'Goods you send back to your suppliers — validating one takes them out of the stock',
    statuses: ['DRAFT', 'VALIDATED', 'CANCELLED']
  }
};

/** Whether a document can be started from scratch — a supplier credit note is only ever made from an invoice. */
export function canBeCreatedByHand(type: PurchaseDocumentType): boolean {
  return type !== 'PURCHASE_CREDIT_NOTE';
}

/** What a document's lines are followed by: an order by what was received, a goods receipt or an invoice by what went back. */
export function fulfilledWord(type: PurchaseDocumentType): string {
  return type === 'PURCHASE_ORDER' ? 'received' : 'returned';
}

/** What a draft that follows a source line may still take of it. */
export function takingWord(type: PurchaseDocumentType): string {
  return type === 'GOODS_RECEIPT' ? 'to receive' : 'to return';
}

/** A goods receipt and an invoice say which warehouse their goods go into, a return note where they leave from. */
export function hasWarehouse(type: PurchaseDocumentType): boolean {
  return type === 'GOODS_RECEIPT' || type === 'PURCHASE_INVOICE' || type === 'PURCHASE_RETURN_NOTE';
}

const STATUS_LABELS: Record<PurchaseDocumentStatus, string> = {
  DRAFT: 'Draft',
  VALIDATED: 'Validated',
  PARTIALLY_PAID: 'Partially paid',
  PAID: 'Paid',
  CANCELLED: 'Cancelled'
};

export type StatusSeverity = 'secondary' | 'success' | 'warn' | 'danger';

const STATUS_SEVERITIES: Record<PurchaseDocumentStatus, StatusSeverity> = {
  DRAFT: 'secondary',
  VALIDATED: 'success',
  PARTIALLY_PAID: 'warn',
  PAID: 'success',
  CANCELLED: 'danger'
};

/** A validated invoice nobody has paid yet is "Unpaid", a validated credit note "Applied" — otherwise "Validated". */
export function statusLabel(status: PurchaseDocumentStatus, type?: PurchaseDocumentType): string {
  if (status === 'VALIDATED') {
    // A credit note is applied to its invoice the moment it is validated
    return type === 'PURCHASE_INVOICE' ? 'Unpaid'
      : type === 'PURCHASE_CREDIT_NOTE' ? 'Applied'
      : type === 'PURCHASE_RETURN_NOTE' ? 'Sent back'
      : STATUS_LABELS[status];
  }
  return STATUS_LABELS[status];
}

export function statusSeverity(status: PurchaseDocumentStatus, type?: PurchaseDocumentType): StatusSeverity {
  return type === 'PURCHASE_INVOICE' && status === 'VALIDATED' ? 'danger' : STATUS_SEVERITIES[status];
}

export function statusOptions(type: PurchaseDocumentType) {
  return PURCHASE_CONFIGS[type].statuses.map(value => ({ value, label: statusLabel(value, type) }));
}
