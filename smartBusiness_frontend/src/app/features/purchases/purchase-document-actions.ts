import { Observable } from 'rxjs';

import { DocumentAction } from '../../shared/document-action';
import { PurchaseDocumentService } from '../../core/services/purchase-document.service';
import {
  PURCHASE_CONFIGS, PurchaseDocumentResponse, PurchaseDocumentStatus, PurchaseDocumentType
} from './purchase-document.model';

export type PurchaseActionId = 'validate' | 'convert' | 'createInvoice' | 'createCreditNote' | 'createReturn' | 'cancel';

export interface PurchaseAction extends DocumentAction {
  id: PurchaseActionId;
}

export interface PurchaseDocumentSubject {
  type: PurchaseDocumentType;
  status: PurchaseDocumentStatus;
  reference?: string | null;
  /** Known on a full document, not on a list row: a document already invoiced hides "Create invoice". */
  derived?: { type?: PurchaseDocumentType; status: PurchaseDocumentStatus }[];
}

/**
 * What can be done with a purchase order or a goods receipt in its current status. Validating a
 * receipt is what puts its goods INTO the stock and cancelling it takes them back out — the
 * confirmation says so, because that is the part people do not expect from a button.
 */
export function purchaseActions(document: PurchaseDocumentSubject): PurchaseAction[] {
  const label = PURCHASE_CONFIGS[document.type].label.toLowerCase();
  const name = document.reference ?? `this ${label}`;
  const receipt = document.type === 'GOODS_RECEIPT';
  const invoice = document.type === 'PURCHASE_INVOICE';
  const actions: PurchaseAction[] = [];
  /** Whether a document of one of these types was made from this one and still stands. */
  const hasLiveChild = (...types: PurchaseDocumentType[]) =>
    document.derived?.some(child => child.status !== 'CANCELLED' && !!child.type && types.includes(child.type)) ?? false;

  if (document.status === 'DRAFT') {
    actions.push({
      id: 'validate', label: 'Validate', icon: 'pi pi-check', permission: 'PURCHASE_UPDATE',
      confirm: {
        header: `Validate ${label}`,
        message: receipt
          ? 'Once validated, the document gets its number, can no longer be edited, and its goods are added to the stock.'
          : invoice
            ? 'Once validated, the document gets its number and can no longer be edited. Its goods come into the stock now, '
              + 'unless it comes from a goods receipt, which already brought them in.'
            : document.type === 'PURCHASE_CREDIT_NOTE'
              ? 'Once validated, the document gets its number and can no longer be edited. It is taken off its invoice at once. '
                + 'It does not move the stock.'
              : document.type === 'PURCHASE_RETURN_NOTE'
                ? 'Once validated, the document gets its number and can no longer be edited. Its goods leave the stock now: '
                  + 'it is refused if a product cannot go below zero and the goods are not there.'
                : 'Once validated, the document gets its number and can no longer be edited.',
        acceptLabel: 'Validate'
      }
    });
    return actions;
  }

  if (document.type === 'PURCHASE_RETURN_NOTE') {
    if (document.status === 'VALIDATED') {
      actions.push({
        id: 'cancel', label: 'Cancel return note', icon: 'pi pi-ban', permission: 'PURCHASE_CANCEL',
        severity: 'danger', outlined: true,
        confirm: {
          header: 'Cancel supplier return note',
          message: `${name} will be cancelled: its goods come back into the stock. This cannot be undone.`,
          acceptLabel: 'Cancel return note', danger: true
        }
      });
    }
    return actions;
  }

  const createReturn = (): PurchaseAction => ({
    id: 'createReturn', label: 'Create return note', icon: 'pi pi-undo', permission: 'PURCHASE_CREATE',
    confirm: {
      header: 'Create supplier return note',
      message: `A draft return note will be created from ${name}, with the same lines. Lower them to what really goes back: `
        + 'validating it takes those goods out of the stock.',
      acceptLabel: 'Create return note'
    }
  });

  if (document.type === 'PURCHASE_CREDIT_NOTE') {
    if (document.status === 'VALIDATED') {
      actions.push({
        id: 'cancel', label: 'Cancel credit note', icon: 'pi pi-ban', permission: 'PURCHASE_CANCEL',
        severity: 'danger', outlined: true,
        confirm: {
          header: 'Cancel supplier credit note',
          message: `${name} will be cancelled: the invoice it was applied to asks for that amount again. This cannot be undone.`,
          acceptLabel: 'Cancel credit note', danger: true
        }
      });
    }
    return actions;
  }

  // A paid invoice is corrected by a credit note; it is cancelled only while nobody paid or credited it
  if (invoice && ['VALIDATED', 'PARTIALLY_PAID', 'PAID'].includes(document.status)) {
    actions.push({
      id: 'createCreditNote', label: 'Create credit note', icon: 'pi pi-replay', permission: 'PURCHASE_CREATE',
      confirm: {
        header: 'Create supplier credit note',
        message: `A draft credit note will be created from ${name}, with the same lines. Lower them to what the supplier credits: `
          + 'once validated, it is taken off the invoice. It does not move the stock.',
        acceptLabel: 'Create credit note'
      }
    });
  }

  if (invoice && ['VALIDATED', 'PARTIALLY_PAID', 'PAID'].includes(document.status)) {
    actions.push(createReturn());
  }

  if (document.status !== 'VALIDATED') {
    return actions;
  }

  if (document.type === 'PURCHASE_ORDER' && !hasLiveChild('PURCHASE_INVOICE')) {
    actions.push({
      id: 'convert', label: 'Create receipt', icon: 'pi pi-arrow-right', permission: 'PURCHASE_CREATE',
      confirm: {
        header: 'Create goods receipt',
        message: `A draft goods receipt will be created from ${name}. Adjust it to what really arrived, then validate it.`,
        acceptLabel: 'Create receipt'
      }
    });
  }
  const invoiceable = document.type === 'PURCHASE_ORDER'
    ? !hasLiveChild('PURCHASE_INVOICE', 'GOODS_RECEIPT')
    : receipt && !hasLiveChild('PURCHASE_INVOICE');
  if (invoiceable) {
    actions.push({
      id: 'createInvoice', label: 'Create invoice', icon: 'pi pi-file', permission: 'PURCHASE_CREATE',
      confirm: {
        header: 'Create invoice',
        message: `A draft invoice will be created from ${name}, with the same lines and prices. `
          + (receipt
            ? 'The goods are already in, so validating it moves no stock.'
            : 'The goods come into the stock when the invoice is validated.'),
        acceptLabel: 'Create invoice'
      }
    });
  }
  if (receipt) {
    actions.push(createReturn());
  }
  const cancellable = !(invoice && hasLiveChild('PURCHASE_CREDIT_NOTE'))
    && !((invoice || receipt) && hasLiveChild('PURCHASE_RETURN_NOTE'));
  if (cancellable) actions.push({
    id: 'cancel', label: `Cancel ${receipt ? 'receipt' : invoice ? 'invoice' : 'order'}`, icon: 'pi pi-ban', permission: 'PURCHASE_CANCEL',
    severity: 'danger', outlined: true,
    confirm: {
      header: `Cancel ${receipt ? 'goods receipt' : invoice ? 'invoice' : 'purchase order'}`,
      message: receipt
        ? `${name} will be cancelled and its goods taken back out of the stock. This cannot be undone.`
        : invoice
          ? `${name} will be cancelled: what it brought into the stock goes back out, unless it came from a goods receipt. `
            + 'It keeps its number. This cannot be undone.'
          : `${name} will be cancelled. This cannot be undone.`,
      acceptLabel: `Cancel ${receipt ? 'receipt' : invoice ? 'invoice' : 'order'}`, danger: true
    }
  });
  return actions;
}

/** Performs a step on the server and answers with the document as it is afterwards. */
export function runPurchaseAction(
  service: PurchaseDocumentService, id: number, action: PurchaseActionId
): Observable<PurchaseDocumentResponse> {
  switch (action) {
    case 'validate': return service.validate(id);
    case 'convert': return service.convertToReceipt(id);
    case 'createInvoice': return service.convertToInvoice(id);
    case 'createCreditNote': return service.convertToCreditNote(id);
    case 'createReturn': return service.convertToReturnNote(id);
    case 'cancel': return service.cancel(id);
  }
}

/** What to tell the user once a step went through. */
export function purchaseActionResult(action: PurchaseActionId, result: PurchaseDocumentResponse): string {
  const label = PURCHASE_CONFIGS[result.type].label;
  switch (action) {
    case 'validate':
      return result.type === 'GOODS_RECEIPT'
        ? `Goods receipt ${result.reference} validated — the goods are in stock.`
        : `${label} validated as ${result.reference}.`;
    case 'convert': return 'A draft goods receipt has been created — adjust it to what arrived, then validate it.';
    case 'createInvoice': return 'A draft invoice has been created — check it, then validate it.';
    case 'createCreditNote': return 'A draft credit note has been created — lower it to what the supplier credits, then validate it.';
    case 'createReturn': return 'A draft return note has been created — lower it to what really goes back, then validate it.';
    case 'cancel': return `The ${label.toLowerCase()} has been cancelled.`;
  }
}

/** The kind of document a step creates and should open afterwards — null for a step that only changes the current one. */
export function purchaseActionOpens(action: PurchaseActionId): PurchaseDocumentType | null {
  switch (action) {
    case 'convert': return 'GOODS_RECEIPT';
    case 'createInvoice': return 'PURCHASE_INVOICE';
    case 'createCreditNote': return 'PURCHASE_CREDIT_NOTE';
    case 'createReturn': return 'PURCHASE_RETURN_NOTE';
    default: return null;
  }
}
