import { Address } from '../../core/models/address.model';
import { CustomerResponse } from '../customers/customer.model';
import { SupplierResponse } from '../suppliers/supplier.model';
import { SalesDocumentResponse, SalesDocumentType } from '../sales/sales-document.model';
import { PurchaseDocumentResponse, PurchaseDocumentType } from '../purchases/purchase-document.model';
import { TaxKind } from '../settings/taxes/tax.model';

/**
 * What every printed document is made of, whatever it is — a quote, a sales order, a purchase
 * order, a goods receipt, and the invoices to come. Each family of documents maps its own
 * response into this shape, so one sheet lays them all out the same way, as Finco does.
 */
export interface PrintableParty {
  name: string;
  /** Matricule fiscal of a business. */
  taxId?: string | null;
  /** CIN of an individual. */
  nationalId?: string | null;
  email?: string | null;
  phone?: string | null;
  billingAddress?: Address | null;
  shippingAddress?: Address | null;
}

export interface PrintableLine {
  reference?: string | null;
  designation: string;
  quantity: number;
  unitPrice: number;
  discountRate: number;
  vatRate: number;
  lineTotal: number;
}

export interface PrintableTax {
  kind: TaxKind;
  name: string;
  rate?: number | null;
  base?: number | null;
  amount: number;
}

/** DRAFT and CANCELLED get a watermark: a preview of a draft must never pass for the real thing. */
export type PrintableState = 'DRAFT' | 'ACTIVE' | 'CANCELLED';

export interface PrintableDocument {
  /** "DEVIS", "COMMANDE CLIENT"… */
  title: string;
  /** Null for a draft — it has no number yet. */
  reference: string | null;
  state: PrintableState;
  issueDate: string;
  dueDate?: string | null;
  /** What the due date means on this document: "Valable jusqu'au", "Livraison prévue le"… */
  dueDateLabel: string;
  partyLabel: string;
  /** The label of the last total: "Net à payer", "Net à déduire" for a credit note. */
  netLabel: string;
  party: PrintableParty;
  /** A goods receipt says where the goods went, a delivery note where they left from. */
  warehouseName?: string | null;
  warehouseLabel: string;
  lines: PrintableLine[];
  taxes: PrintableTax[];
  subtotal: number;
  total: number;
  notes?: string | null;
  terms?: string | null;
  /** The legal sentence that introduces the amount in words: "Arrêté le présent devis à la somme de". */
  totalPhrase: string;
}

interface DocumentWording {
  title: string;
  /** Heading of the warehouse block, for the documents that name one. */
  warehouseLabel: string;
  totalPhrase: string;
  dueDateLabel: string;
  partyLabel: string;
  netLabel: string;
}

/** French wording of every document type — one table, so a new type is one new line. */
const WORDING: Record<SalesDocumentType | PurchaseDocumentType, DocumentWording> = {
  QUOTE: {
    title: 'DEVIS', partyLabel: 'Client', warehouseLabel: '', netLabel: 'Net à payer',
    dueDateLabel: 'Valable jusqu\'au', totalPhrase: 'Arrêté le présent devis à la somme de'
  },
  SALES_ORDER: {
    title: 'COMMANDE CLIENT', partyLabel: 'Client', warehouseLabel: '', netLabel: 'Net à payer',
    dueDateLabel: 'Échéance', totalPhrase: 'Arrêtée la présente commande à la somme de'
  },
  DELIVERY_NOTE: {
    title: 'BON DE LIVRAISON', partyLabel: 'Client', warehouseLabel: 'Entrepôt de départ', netLabel: 'Net à payer',
    dueDateLabel: 'Échéance', totalPhrase: 'Arrêté le présent bon de livraison à la somme de'
  },
  RETURN_NOTE: {
    title: 'BON DE RETOUR', partyLabel: 'Client', warehouseLabel: 'Entrepôt de retour', netLabel: 'Net à déduire',
    dueDateLabel: 'Échéance', totalPhrase: 'Arrêté le présent bon de retour à la somme de'
  },
  PURCHASE_RETURN_NOTE: {
    title: 'BON DE RETOUR FOURNISSEUR', partyLabel: 'Fournisseur', warehouseLabel: 'Entrepôt de départ', netLabel: 'Net à déduire',
    dueDateLabel: 'Échéance', totalPhrase: 'Arrêté le présent bon de retour à la somme de'
  },
  CREDIT_NOTE: {
    title: 'AVOIR', partyLabel: 'Client', warehouseLabel: '', netLabel: 'Net à déduire',
    dueDateLabel: 'Échéance', totalPhrase: 'Arrêté le présent avoir à la somme de'
  },
  INVOICE: {
    title: 'FACTURE', partyLabel: 'Client', warehouseLabel: '', netLabel: 'Net à payer',
    dueDateLabel: 'Échéance', totalPhrase: 'Arrêtée la présente facture à la somme de'
  },
  PURCHASE_ORDER: {
    title: 'COMMANDE FOURNISSEUR', partyLabel: 'Fournisseur', warehouseLabel: '', netLabel: 'Net à payer',
    dueDateLabel: 'Livraison prévue le', totalPhrase: 'Arrêtée la présente commande à la somme de'
  },
  PURCHASE_CREDIT_NOTE: {
    title: 'AVOIR FOURNISSEUR', partyLabel: 'Fournisseur', warehouseLabel: '', netLabel: 'Net à déduire',
    dueDateLabel: 'Échéance', totalPhrase: 'Arrêté le présent avoir à la somme de'
  },
  PURCHASE_INVOICE: {
    title: 'FACTURE D\'ACHAT', partyLabel: 'Fournisseur', warehouseLabel: '', netLabel: 'Net à payer',
    dueDateLabel: 'Échéance', totalPhrase: 'Arrêtée la présente facture à la somme de'
  },
  GOODS_RECEIPT: {
    title: 'BON DE RÉCEPTION', partyLabel: 'Fournisseur', warehouseLabel: 'Entrepôt de réception', netLabel: 'Net à payer',
    dueDateLabel: 'Échéance', totalPhrase: 'Arrêté le présent bon de réception à la somme de'
  }
};

function stateOf(status: string): PrintableState {
  return status === 'DRAFT' ? 'DRAFT' : status === 'CANCELLED' ? 'CANCELLED' : 'ACTIVE';
}

type LineSource = {
  reference?: string | null; designation: string; quantity: number; unitPrice: number;
  discountRate: number; vatRate?: number; lineTotal?: number;
};

function toLines(lines: LineSource[]): PrintableLine[] {
  return lines.map(line => ({
    reference: line.reference,
    designation: line.designation,
    quantity: line.quantity,
    unitPrice: line.unitPrice,
    discountRate: line.discountRate,
    vatRate: line.vatRate ?? 0,
    lineTotal: line.lineTotal ?? 0
  }));
}

export function fromSalesDocument(document: SalesDocumentResponse, customer: CustomerResponse): PrintableDocument {
  const wording = WORDING[document.type];
  return {
    ...wording,
    reference: document.reference ?? null,
    state: stateOf(document.status),
    issueDate: document.issueDate,
    dueDate: document.dueDate,
    party: customer,
    warehouseName: document.warehouseName,
    lines: toLines(document.lines),
    taxes: document.taxes,
    subtotal: document.subtotal,
    total: document.total,
    notes: document.notes,
    terms: document.terms
  };
}

export function fromPurchaseDocument(document: PurchaseDocumentResponse, supplier: SupplierResponse): PrintableDocument {
  const wording = WORDING[document.type];
  return {
    ...wording,
    reference: document.reference ?? null,
    state: stateOf(document.status),
    issueDate: document.issueDate,
    dueDate: document.dueDate,
    party: supplier,
    warehouseName: document.warehouseName,
    lines: toLines(document.lines),
    taxes: document.taxes,
    subtotal: document.subtotal,
    total: document.total,
    notes: document.notes,
    terms: document.terms
  };
}

/** The company as it appears on the sheet — see `GET /api/print-profile`. */
export interface PrintProfile {
  name: string;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  postalCode?: string | null;
  city?: string | null;
  taxId?: string | null;
  currency: string;
  logoDataUri?: string | null;
  stampDataUri?: string | null;
  bankAccounts: { label: string; bankName?: string | null; rib: string; currency: string }[];
}

/** What the person printing chose to show — Finco's "Paramètres" panel, the useful part of it. */
export interface PrintOptions {
  /** Off, a sheet without amounts (what a delivery or receipt slip needs). */
  showPrices: boolean;
  showReference: boolean;
}

/** Finco writes the dinar "DT"; any other currency shows its code. */
export function currencyLabel(currency: string): string {
  return currency === 'TND' ? 'DT' : currency;
}
