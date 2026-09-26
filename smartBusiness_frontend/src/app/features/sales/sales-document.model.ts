import { TaxKind } from '../settings/taxes/tax.model';

export type SalesDocumentType = 'QUOTE' | 'SALES_ORDER' | 'DELIVERY_NOTE' | 'INVOICE' | 'CREDIT_NOTE' | 'RETURN_NOTE';

export type SalesDocumentStatus =
  | 'DRAFT'
  | 'ISSUED'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'CONFIRMED'
  | 'DELIVERED'
  | 'PARTIALLY_PAID'
  | 'PAID'
  | 'CANCELLED';

export interface SalesDocumentLine {
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

export interface SalesDocumentTaxRow {
  /** The company tax it came from; null for a VAT row. */
  taxId?: number | null;
  kind: TaxKind;
  name: string;
  rate?: number | null;
  base?: number | null;
  amount: number;
}

export interface SalesDocumentSummary {
  id: number;
  type: SalesDocumentType;
  status: SalesDocumentStatus;
  reference?: string | null;
  customerId: number;
  customerName: string;
  issueDate: string;
  dueDate?: string | null;
  total: number;
  /** Invoice only: what has been paid, and what is left — both come from the server. */
  paidAmount?: number;
  creditedAmount?: number;
  balance?: number;
  createdAt: string;
}

export interface SalesDocumentResponse {
  id: number | null;
  type: SalesDocumentType;
  status: SalesDocumentStatus;
  reference?: string | null;
  customerId?: number | null;
  customerName?: string | null;
  /** Where a delivery note or an invoice takes its goods from. Null for a quote and a sales order. */
  warehouseId?: number | null;
  warehouseName?: string | null;
  sourceId?: number | null;
  sourceReference?: string | null;
  /** What the source is: an invoice comes from a quote, an order or a delivery note. */
  sourceType?: SalesDocumentType | null;
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
  lines: SalesDocumentLine[];
  taxes: SalesDocumentTaxRow[];
  /** The documents made from this one (the order a quote became). */
  derived: SalesDocumentSummary[];
  createdAt?: string;
  updatedAt?: string;
}

export interface SalesDocumentRequest {
  type: SalesDocumentType;
  customerId: number;
  warehouseId?: number | null;
  issueDate: string;
  dueDate?: string | null;
  notes?: string | null;
  terms?: string | null;
  taxIds: number[];
  lines: SalesDocumentLine[];
}

/** What differs between the document types on screen — everything else is shared. */
export interface SalesDocumentConfig {
  label: string;
  plural: string;
  /** Route prefix: `/quotes`, `/sales-orders`. */
  path: string;
  description: string;
  statuses: SalesDocumentStatus[];
}

export const DOCUMENT_CONFIGS: Record<SalesDocumentType, SalesDocumentConfig> = {
  QUOTE: {
    label: 'Quote',
    plural: 'Quotes',
    path: '/quotes',
    description: 'Offers sent to your customers',
    statuses: ['DRAFT', 'ISSUED', 'ACCEPTED', 'REJECTED']
  },
  SALES_ORDER: {
    label: 'Sales order',
    plural: 'Sales orders',
    path: '/sales-orders',
    description: 'What your customers have ordered',
    statuses: ['DRAFT', 'ISSUED', 'CONFIRMED', 'CANCELLED']
  },
  DELIVERY_NOTE: {
    label: 'Delivery note',
    plural: 'Delivery notes',
    path: '/delivery-notes',
    description: 'Goods that leave — delivering one takes them out of the stock',
    statuses: ['DRAFT', 'ISSUED', 'DELIVERED', 'CANCELLED']
  },
  INVOICE: {
    label: 'Invoice',
    plural: 'Invoices',
    path: '/invoices',
    description: 'What your customers owe you — issuing one sells its goods, payments settle it',
    statuses: ['DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED']
  },
  CREDIT_NOTE: {
    label: 'Credit note',
    plural: 'Credit notes',
    path: '/credit-notes',
    description: 'Corrections to issued invoices — issuing one lowers what the customer owes',
    statuses: ['DRAFT', 'ISSUED', 'CANCELLED']
  },
  RETURN_NOTE: {
    label: 'Return note',
    plural: 'Return notes',
    path: '/return-notes',
    description: 'Goods that come back from your customers — issuing one puts them back into the stock',
    statuses: ['DRAFT', 'ISSUED', 'CANCELLED']
  }
};

/** Whether a document can be started from scratch — a credit note is only ever made from an invoice. */
export function canBeCreatedByHand(type: SalesDocumentType): boolean {
  return type !== 'CREDIT_NOTE';
}

/** What a document's lines are followed by: an order by what was delivered, a delivery note or an invoice by what came back. */
export function fulfilledWord(type: SalesDocumentType): string {
  return type === 'SALES_ORDER' ? 'delivered' : 'returned';
}

/** What a draft that follows a source line may still take of it. */
export function takingWord(type: SalesDocumentType): string {
  return type === 'DELIVERY_NOTE' ? 'to deliver' : 'to return';
}

/** A delivery note and an invoice say which warehouse their goods leave from, a return note where they come back to. */
export function hasWarehouse(type: SalesDocumentType): boolean {
  return type === 'DELIVERY_NOTE' || type === 'INVOICE' || type === 'RETURN_NOTE';
}

const STATUS_LABELS: Record<SalesDocumentStatus, string> = {
  DRAFT: 'Draft',
  ISSUED: 'Issued',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
  CONFIRMED: 'Confirmed',
  DELIVERED: 'Delivered',
  PARTIALLY_PAID: 'Partially paid',
  PAID: 'Paid',
  CANCELLED: 'Cancelled'
};

export type StatusSeverity = 'secondary' | 'info' | 'success' | 'warn' | 'danger';

const STATUS_SEVERITIES: Record<SalesDocumentStatus, StatusSeverity> = {
  DRAFT: 'secondary',
  ISSUED: 'info',
  ACCEPTED: 'success',
  REJECTED: 'danger',
  CONFIRMED: 'success',
  DELIVERED: 'success',
  PARTIALLY_PAID: 'warn',
  PAID: 'success',
  CANCELLED: 'danger'
};

/** An issued invoice nobody has paid yet is "Unpaid" — for every other document, "Issued" is what it is. */
export function statusLabel(status: SalesDocumentStatus, type?: SalesDocumentType): string {
  if (status === 'ISSUED') {
    // A credit note is applied to its invoice the moment it is issued, a return note has its goods back
    return type === 'INVOICE' ? 'Unpaid'
      : type === 'CREDIT_NOTE' ? 'Applied'
      : type === 'RETURN_NOTE' ? 'Received'
      : STATUS_LABELS[status];
  }
  return STATUS_LABELS[status];
}

export function statusSeverity(status: SalesDocumentStatus, type?: SalesDocumentType): StatusSeverity {
  if (status === 'ISSUED') {
    return type === 'INVOICE' ? 'danger' : type === 'CREDIT_NOTE' || type === 'RETURN_NOTE' ? 'success' : STATUS_SEVERITIES[status];
  }
  return STATUS_SEVERITIES[status];
}

export function statusOptions(type: SalesDocumentType) {
  return DOCUMENT_CONFIGS[type].statuses.map(value => ({ value, label: statusLabel(value, type) }));
}
