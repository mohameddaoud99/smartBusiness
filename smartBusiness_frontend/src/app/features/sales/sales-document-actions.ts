import { Observable } from 'rxjs';

import { DocumentAction } from '../../shared/document-action';
import { SalesDocumentService } from '../../core/services/sales-document.service';
import {
  DOCUMENT_CONFIGS, SalesDocumentResponse, SalesDocumentStatus, SalesDocumentType, statusLabel
} from './sales-document.model';

export type SalesActionId =
  | 'issue' | 'accept' | 'reject' | 'reopen' | 'confirm' | 'convert' | 'createDelivery' | 'createInvoice' | 'createCreditNote' | 'createReturn' | 'deliver' | 'cancel';

export interface SalesAction extends DocumentAction {
  id: SalesActionId;
}

/** The little of a document the steps depend on — a list row has it as well as a full document. */
export interface SalesDocumentSubject {
  type: SalesDocumentType;
  status: SalesDocumentStatus;
  reference?: string | null;
  /** Known on a full document, not on a list row: an order already made from a quote hides "Convert". */
  derived?: { type?: SalesDocumentType; status: SalesDocumentStatus }[];
}

/**
 * What can be done with a quote or a sales order in its current status, in the order a person
 * would do it — forward steps first, the ones that cannot be taken back last. Mirrors
 * `SalesDocument.canMoveTo` on the server, which is the one that actually decides.
 */
export function salesActions(document: SalesDocumentSubject): SalesAction[] {
  const label = DOCUMENT_CONFIGS[document.type].label.toLowerCase();
  const name = document.reference ?? `this ${label}`;
  const actions: SalesAction[] = [];

  const add = (action: SalesAction) => actions.push(action);
  const update = 'SALE_UPDATE' as const;
  /** Whether a document of one of these types was made from this one and still stands. */
  const hasLiveChild = (...types: SalesDocumentType[]) =>
    document.derived?.some(child => child.status !== 'CANCELLED' && !!child.type && types.includes(child.type)) ?? false;
  const createReturn = (): SalesAction => ({
    id: 'createReturn', label: 'Create return note', icon: 'pi pi-undo', permission: 'SALE_CREATE',
    confirm: {
      header: 'Create return note',
      message: `A draft return note will be created from ${name}, with the same lines. Lower them to what really comes back: `
        + 'issuing it puts those goods back into the stock.',
      acceptLabel: 'Create return note'
    }
  });
  const createInvoice = (from: string): SalesAction => ({
    id: 'createInvoice', label: 'Create invoice', icon: 'pi pi-file', permission: 'SALE_CREATE',
    confirm: {
      header: 'Create invoice',
      message: `A draft invoice will be created from ${name}, with the same lines and prices. ${from}`,
      acceptLabel: 'Create invoice'
    }
  });

  if (document.status === 'DRAFT') {
    add({
      id: 'issue', label: 'Issue', icon: 'pi pi-send', permission: update,
      confirm: {
        header: `Issue ${label}`,
        message: `Once issued, this ${label} gets its number and can no longer be edited.`
          + (document.type === 'INVOICE'
            ? ' Its goods leave the stock now, unless it comes from a delivery note, which already took them out.'
            : document.type === 'CREDIT_NOTE'
              ? ' It is taken off its invoice at once. It does not move the stock.'
              : document.type === 'RETURN_NOTE'
                ? ' Its goods come back into the stock now.'
                : ''),
        acceptLabel: 'Issue'
      }
    });
    return actions;
  }

  if (document.type === 'QUOTE') {
    if (document.status !== 'ACCEPTED') {
      add({
        id: 'accept', label: 'Accept', icon: 'pi pi-check', permission: update, severity: 'success',
        confirm: { header: 'Accept quote', message: `Mark ${name} as accepted by the customer?`, acceptLabel: 'Accept' }
      });
    }
    if (document.status !== 'REJECTED') {
      add({
        id: 'reject', label: 'Reject', icon: 'pi pi-times', permission: update, severity: 'danger', outlined: true,
        confirm: { header: 'Reject quote', message: `Mark ${name} as rejected by the customer?`, acceptLabel: 'Reject' }
      });
    }
    if (document.status !== 'ISSUED') {
      add({
        id: 'reopen', label: 'Back to issued', icon: 'pi pi-undo', permission: update, severity: 'secondary', outlined: true,
        confirm: { header: 'Back to issued', message: `Put ${name} back to issued, waiting for the customer's answer?`, acceptLabel: 'Put back' }
      });
    }
    const alreadyOrdered = document.derived?.some(order => order.status !== 'CANCELLED') ?? false;
    if ((document.status === 'ISSUED' || document.status === 'ACCEPTED') && !alreadyOrdered) {
      add({
        id: 'convert', label: 'Convert to order', icon: 'pi pi-arrow-right', permission: 'SALE_CREATE',
        confirm: {
          header: 'Convert to sales order',
          message: `A draft sales order will be created from ${name}, with the same lines and prices.`,
          acceptLabel: 'Convert'
        }
      });
      add(createInvoice('The goods leave the stock when the invoice is issued.'));
    }
    return actions;
  }

  if (document.type === 'DELIVERY_NOTE') {
    if (document.status === 'ISSUED') {
      add({
        id: 'deliver', label: 'Mark as delivered', icon: 'pi pi-check', permission: update, severity: 'success',
        confirm: {
          header: 'Mark as delivered',
          message: `${name} has left: its goods will be taken out of the stock and out of the order they were reserved on. `
            + 'It is refused if a product cannot go below zero and the goods are not there.',
          acceptLabel: 'Mark as delivered'
        }
      });
    }
    if (document.status === 'DELIVERED' && !hasLiveChild('INVOICE')) {
      add(createInvoice('The goods have already left, so issuing it moves no stock.'));
    }
    if (document.status === 'DELIVERED') {
      add(createReturn());
    }
    if ((document.status === 'ISSUED' || document.status === 'DELIVERED') && !hasLiveChild('RETURN_NOTE')) {
      const delivered = document.status === 'DELIVERED';
      add({
        id: 'cancel', label: 'Cancel delivery note', icon: 'pi pi-ban', permission: 'SALE_CANCEL', severity: 'danger', outlined: true,
        confirm: {
          header: 'Cancel delivery note',
          message: delivered
            ? `${name} will be cancelled: its goods come back into the stock, and are reserved again for the order if it still stands. This cannot be undone.`
            : `${name} will be cancelled. Nothing has left the stock yet. This cannot be undone.`,
          acceptLabel: 'Cancel delivery note', danger: true
        }
      });
    }
    return actions;
  }

  if (document.type === 'RETURN_NOTE') {
    if (document.status === 'ISSUED') {
      add({
        id: 'cancel', label: 'Cancel return note', icon: 'pi pi-ban', permission: 'SALE_CANCEL', severity: 'danger', outlined: true,
        confirm: {
          header: 'Cancel return note',
          message: `${name} will be cancelled: its goods go back out of the stock again. `
            + 'It is refused if a product cannot go below zero and the goods are no longer there. This cannot be undone.',
          acceptLabel: 'Cancel return note', danger: true
        }
      });
    }
    return actions;
  }

  if (document.type === 'CREDIT_NOTE') {
    if (document.status === 'ISSUED') {
      add({
        id: 'cancel', label: 'Cancel credit note', icon: 'pi pi-ban', permission: 'SALE_CANCEL', severity: 'danger', outlined: true,
        confirm: {
          header: 'Cancel credit note',
          message: `${name} will be cancelled: the invoice it was applied to asks for that amount again. This cannot be undone.`,
          acceptLabel: 'Cancel credit note', danger: true
        }
      });
    }
    return actions;
  }

  if (document.type === 'INVOICE') {
    // A paid invoice is corrected by a credit note; it is cancelled only while nobody paid or credited it
    if (document.status === 'ISSUED' || document.status === 'PARTIALLY_PAID' || document.status === 'PAID') {
      add({
        id: 'createCreditNote', label: 'Create credit note', icon: 'pi pi-replay', permission: 'SALE_CREATE',
        confirm: {
          header: 'Create credit note',
          message: `A draft credit note will be created from ${name}, with the same lines. Lower them to what you credit: `
            + 'once issued, it is taken off the invoice. It does not move the stock.',
          acceptLabel: 'Create credit note'
        }
      });
    }
    if (document.status === 'ISSUED' || document.status === 'PARTIALLY_PAID' || document.status === 'PAID') {
      add(createReturn());
    }
    if (document.status === 'ISSUED' && !hasLiveChild('CREDIT_NOTE', 'RETURN_NOTE')) {
      add({
        id: 'cancel', label: 'Cancel invoice', icon: 'pi pi-ban', permission: 'SALE_CANCEL', severity: 'danger', outlined: true,
        confirm: {
          header: 'Cancel invoice',
          message: `${name} will be cancelled: what it took out of the stock comes back, unless it came from a delivery note. `
            + 'It keeps its number. This cannot be undone.',
          acceptLabel: 'Cancel invoice', danger: true
        }
      });
    }
    return actions;
  }

  if (document.status === 'ISSUED') {
    add({
      id: 'confirm', label: 'Confirm', icon: 'pi pi-check', permission: update, severity: 'success',
      confirm: {
        header: 'Confirm sales order',
        message: `Confirm ${name}? Its goods will be reserved in the stock, and it is refused if there is not enough.`,
        acceptLabel: 'Confirm'
      }
    });
  }
  if (document.status === 'CONFIRMED' && !hasLiveChild('INVOICE')) {
    add({
      id: 'createDelivery', label: 'Create delivery note', icon: 'pi pi-truck', permission: 'SALE_CREATE',
      confirm: {
        header: 'Create delivery note',
        message: `A draft delivery note will be created from ${name}. Adjust it to what really leaves, then issue and deliver it.`,
        acceptLabel: 'Create delivery note'
      }
    });
  }
  if (document.status === 'CONFIRMED' && !hasLiveChild('INVOICE', 'DELIVERY_NOTE')) {
    add(createInvoice('The goods it reserved leave the stock when the invoice is issued.'));
  }
  if (document.status === 'ISSUED' || document.status === 'CONFIRMED') {
    add({
      id: 'cancel', label: 'Cancel order', icon: 'pi pi-ban', permission: 'SALE_CANCEL', severity: 'danger', outlined: true,
      confirm: {
        header: 'Cancel sales order',
        message: `${name} will be cancelled and the goods it reserved will go back on sale. This cannot be undone.`,
        acceptLabel: 'Cancel order', danger: true
      }
    });
  }
  return actions;
}

/** Performs a step on the server and answers with the document as it is afterwards. */
export function runSalesAction(
  service: SalesDocumentService, id: number, action: SalesActionId
): Observable<SalesDocumentResponse> {
  switch (action) {
    case 'issue': return service.issue(id);
    case 'accept': return service.changeStatus(id, 'ACCEPTED');
    case 'reject': return service.changeStatus(id, 'REJECTED');
    case 'reopen': return service.changeStatus(id, 'ISSUED');
    case 'confirm': return service.changeStatus(id, 'CONFIRMED');
    case 'convert': return service.convertToOrder(id);
    case 'createDelivery': return service.convertToDeliveryNote(id);
    case 'createInvoice': return service.convertToInvoice(id);
    case 'createCreditNote': return service.convertToCreditNote(id);
    case 'createReturn': return service.convertToReturnNote(id);
    case 'deliver': return service.changeStatus(id, 'DELIVERED');
    case 'cancel': return service.cancel(id);
  }
}

/** What to tell the user once a step went through. */
export function salesActionResult(action: SalesActionId, result: SalesDocumentResponse): string {
  const label = DOCUMENT_CONFIGS[result.type].label;
  switch (action) {
    case 'issue': return `${label} issued as ${result.reference}.`;
    case 'convert': return 'A draft sales order has been created from this quote.';
    case 'createDelivery': return 'A draft delivery note has been created — adjust it to what really leaves.';
    case 'createInvoice': return 'A draft invoice has been created — check it, then issue it.';
    case 'createCreditNote': return 'A draft credit note has been created — lower it to what you credit, then issue it.';
    case 'createReturn': return 'A draft return note has been created — lower it to what really comes back, then issue it.';
    case 'deliver': return `${result.reference} delivered — the goods have left the stock.`;
    default: return `The ${label.toLowerCase()} is now ${statusLabel(result.status, result.type).toLowerCase()}.`;
  }
}

/** The kind of document a step creates and should open afterwards — null for a step that only changes the current one. */
export function salesActionOpens(action: SalesActionId): SalesDocumentType | null {
  switch (action) {
    case 'convert': return 'SALES_ORDER';
    case 'createDelivery': return 'DELIVERY_NOTE';
    case 'createInvoice': return 'INVOICE';
    case 'createCreditNote': return 'CREDIT_NOTE';
    case 'createReturn': return 'RETURN_NOTE';
    default: return null;
  }
}
