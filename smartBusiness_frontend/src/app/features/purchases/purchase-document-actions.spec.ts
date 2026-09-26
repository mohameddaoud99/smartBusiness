import { purchaseActionOpens, purchaseActionResult, purchaseActions } from './purchase-document-actions';
import { PurchaseDocumentResponse, PurchaseDocumentStatus, PurchaseDocumentType } from './purchase-document.model';

describe('purchaseActions', () => {

  function ids(type: PurchaseDocumentType, status: PurchaseDocumentStatus, derived?: { type: PurchaseDocumentType; status: PurchaseDocumentStatus }[]) {
    return purchaseActions({ type, status, reference: 'X-1', derived }).map(action => action.id);
  }

  it('only offers to validate a draft', () => {
    expect(ids('PURCHASE_ORDER', 'DRAFT')).toEqual(['validate']);
    expect(ids('GOODS_RECEIPT', 'DRAFT')).toEqual(['validate']);
  });

  it('lets a validated order be received or cancelled, and a validated receipt only be cancelled', () => {
    expect(ids('PURCHASE_ORDER', 'VALIDATED')).toEqual(['convert', 'createInvoice', 'cancel']);
    expect(ids('GOODS_RECEIPT', 'VALIDATED')).toEqual(['createInvoice', 'createReturn', 'cancel']);
  });

  it('offers nothing on a cancelled document', () => {
    expect(ids('PURCHASE_ORDER', 'CANCELLED')).toEqual([]);
    expect(ids('GOODS_RECEIPT', 'CANCELLED')).toEqual([]);
  });

  it('asks the right permission for each step', () => {
    const permission = (type: PurchaseDocumentType, status: PurchaseDocumentStatus, id: string) =>
      purchaseActions({ type, status }).find(action => action.id === id)!.permission;

    expect(permission('PURCHASE_ORDER', 'DRAFT', 'validate')).toBe('PURCHASE_UPDATE');
    expect(permission('PURCHASE_ORDER', 'VALIDATED', 'convert')).toBe('PURCHASE_CREATE');
    expect(permission('GOODS_RECEIPT', 'VALIDATED', 'cancel')).toBe('PURCHASE_CANCEL');
  });

  it('says the goods enter the stock on validation of a receipt — and not for an order', () => {
    const receipt = purchaseActions({ type: 'GOODS_RECEIPT', status: 'DRAFT' })[0];
    const order = purchaseActions({ type: 'PURCHASE_ORDER', status: 'DRAFT' })[0];

    expect(receipt.confirm.message).toContain('added to the stock');
    expect(order.confirm.message).not.toContain('stock');
  });

  it('says the goods leave the stock when a receipt is cancelled, and marks every cancellation as dangerous', () => {
    const receipt = purchaseActions({ type: 'GOODS_RECEIPT', status: 'VALIDATED', reference: 'GR-1' }).find(a => a.id === 'cancel')!;
    const order = purchaseActions({ type: 'PURCHASE_ORDER', status: 'VALIDATED', reference: 'PO-1' }).find(a => a.id === 'cancel')!;

    expect(receipt.confirm.message).toContain('taken back out of the stock');
    expect(receipt.confirm.danger).toBeTrue();
    expect(order.confirm.danger).toBeTrue();
  });

  it('only lets an unpaid invoice be cancelled: once paid, even in part, its payments come first', () => {
    expect(ids('PURCHASE_INVOICE', 'DRAFT')).toEqual(['validate']);
    expect(ids('PURCHASE_INVOICE', 'VALIDATED')).toEqual(['createCreditNote', 'createReturn', 'cancel']);
    expect(ids('PURCHASE_INVOICE', 'PARTIALLY_PAID')).toEqual(['createCreditNote', 'createReturn']);
    expect(ids('PURCHASE_INVOICE', 'PAID')).toEqual(['createCreditNote', 'createReturn']);
    expect(ids('PURCHASE_INVOICE', 'CANCELLED')).toEqual([]);
  });

  it('never offers to pay or receive an invoice by hand: payments and receipts have their own place', () => {
    for (const status of ['DRAFT', 'VALIDATED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'] as const) {
      expect(ids('PURCHASE_INVOICE', status)).not.toContain('convert');
      expect(ids('PURCHASE_INVOICE', status)).not.toContain('createInvoice');
    }
  });

  it('hides "Create invoice" once a live invoice was made from the order or the receipt', () => {
    expect(ids('PURCHASE_ORDER', 'VALIDATED', [{ type: 'PURCHASE_INVOICE', status: 'VALIDATED' }]))
      .toEqual(['cancel']);
    expect(ids('PURCHASE_ORDER', 'VALIDATED', [{ type: 'GOODS_RECEIPT', status: 'VALIDATED' }]))
      .toEqual(['convert', 'cancel']);
    expect(ids('PURCHASE_ORDER', 'VALIDATED', [{ type: 'PURCHASE_INVOICE', status: 'CANCELLED' }]))
      .toEqual(['convert', 'createInvoice', 'cancel']);
    expect(ids('GOODS_RECEIPT', 'VALIDATED', [{ type: 'PURCHASE_INVOICE', status: 'PAID' }])).toEqual(['createReturn', 'cancel']);
  });

  it('asks the right permission for the invoice steps, and warns about the stock', () => {
    const find = (type: PurchaseDocumentType, status: PurchaseDocumentStatus, id: string) =>
      purchaseActions({ type, status, reference: 'PINV-1' }).find(action => action.id === id)!;

    expect(find('GOODS_RECEIPT', 'VALIDATED', 'createInvoice').permission).toBe('PURCHASE_CREATE');
    expect(find('PURCHASE_INVOICE', 'VALIDATED', 'cancel').permission).toBe('PURCHASE_CANCEL');
    expect(find('PURCHASE_INVOICE', 'VALIDATED', 'cancel').confirm.danger).toBeTrue();
    expect(find('PURCHASE_INVOICE', 'DRAFT', 'validate').confirm.message).toContain('come into the stock');
    expect(find('GOODS_RECEIPT', 'VALIDATED', 'createInvoice').confirm.message).toContain('moves no stock');
    expect(find('PURCHASE_ORDER', 'VALIDATED', 'createInvoice').confirm.message).toContain('come into the stock');
  });

  it('offers a credit note on an invoice that stands, never on a draft, a cancelled invoice or another document', () => {
    expect(ids('PURCHASE_INVOICE', 'DRAFT')).not.toContain('createCreditNote');
    expect(ids('PURCHASE_INVOICE', 'CANCELLED')).not.toContain('createCreditNote');
    expect(ids('PURCHASE_ORDER', 'VALIDATED')).not.toContain('createCreditNote');
    expect(ids('GOODS_RECEIPT', 'VALIDATED')).not.toContain('createCreditNote');
  });

  it('hides "Cancel invoice" while the invoice has a live credit note, and brings it back once that is cancelled', () => {
    expect(ids('PURCHASE_INVOICE', 'VALIDATED', [{ type: 'PURCHASE_CREDIT_NOTE', status: 'DRAFT' }]))
      .toEqual(['createCreditNote', 'createReturn']);
    expect(ids('PURCHASE_INVOICE', 'VALIDATED', [{ type: 'PURCHASE_CREDIT_NOTE', status: 'CANCELLED' }]))
      .toEqual(['createCreditNote', 'createReturn', 'cancel']);
  });

  it('validates a credit note, warning that it is taken off its invoice at once, and only lets a validated one be cancelled', () => {
    expect(ids('PURCHASE_CREDIT_NOTE', 'DRAFT')).toEqual(['validate']);
    expect(ids('PURCHASE_CREDIT_NOTE', 'VALIDATED')).toEqual(['cancel']);
    expect(ids('PURCHASE_CREDIT_NOTE', 'CANCELLED')).toEqual([]);

    const validate = purchaseActions({ type: 'PURCHASE_CREDIT_NOTE', status: 'DRAFT' })[0];
    expect(validate.confirm.message).toContain('taken off its invoice');
    expect(validate.confirm.message).toContain('does not move the stock');

    const cancel = purchaseActions({ type: 'PURCHASE_CREDIT_NOTE', status: 'VALIDATED', reference: 'PCN-1' })[0];
    expect(cancel.permission).toBe('PURCHASE_CANCEL');
    expect(cancel.confirm.danger).toBeTrue();
    expect(cancel.confirm.message).toContain('asks for that amount again');
  });

  it('asks the right permission to create a credit note, and warns it moves no stock', () => {
    const create = purchaseActions({ type: 'PURCHASE_INVOICE', status: 'PAID', reference: 'PINV-1' })
      .find(a => a.id === 'createCreditNote')!;

    expect(create.permission).toBe('PURCHASE_CREATE');
    expect(create.confirm.message).toContain('PINV-1');
    expect(create.confirm.message).toContain('does not move the stock');
  });

  it('says which document a creating step opens', () => {
    expect(purchaseActionOpens('createCreditNote')).toBe('PURCHASE_CREDIT_NOTE');
    expect(purchaseActionOpens('convert')).toBe('GOODS_RECEIPT');
    expect(purchaseActionOpens('createInvoice')).toBe('PURCHASE_INVOICE');
    expect(purchaseActionOpens('cancel')).toBeNull();
  });

  it('words the result of a step', () => {
    const result = (overrides: Partial<PurchaseDocumentResponse>) => ({
      id: 5, type: 'PURCHASE_ORDER', status: 'VALIDATED', reference: 'PO-1', issueDate: '', subtotal: 0, total: 0,
      lines: [], taxes: [], derived: [], ...overrides
    } as PurchaseDocumentResponse);

    expect(purchaseActionResult('validate', result({}))).toBe('Purchase order validated as PO-1.');
    expect(purchaseActionResult('validate', result({ type: 'GOODS_RECEIPT', reference: 'GR-1' })))
      .toBe('Goods receipt GR-1 validated — the goods are in stock.');
    expect(purchaseActionResult('cancel', result({ status: 'CANCELLED' }))).toBe('The purchase order has been cancelled.');
    expect(purchaseActionResult('convert', result({ type: 'GOODS_RECEIPT' }))).toContain('draft goods receipt');
    expect(purchaseActionResult('createInvoice', result({ type: 'PURCHASE_INVOICE' }))).toContain('draft invoice');
    expect(purchaseActionResult('createCreditNote', result({ type: 'PURCHASE_CREDIT_NOTE' }))).toContain('draft credit note');
    expect(purchaseActionResult('cancel', result({ type: 'PURCHASE_CREDIT_NOTE', status: 'CANCELLED' })))
      .toBe('The supplier credit note has been cancelled.');
    expect(purchaseActionResult('validate', result({ type: 'PURCHASE_INVOICE', reference: 'PINV-1' })))
      .toBe('Purchase invoice validated as PINV-1.');
  });

  it('offers a return note on a validated receipt and on an invoice that stands, never on a draft or an order', () => {
    expect(ids('GOODS_RECEIPT', 'VALIDATED')).toContain('createReturn');
    expect(ids('GOODS_RECEIPT', 'DRAFT')).not.toContain('createReturn');
    for (const status of ['VALIDATED', 'PARTIALLY_PAID', 'PAID'] as const) {
      expect(ids('PURCHASE_INVOICE', status)).toContain('createReturn');
    }
    expect(ids('PURCHASE_INVOICE', 'DRAFT')).not.toContain('createReturn');
    expect(ids('PURCHASE_INVOICE', 'CANCELLED')).not.toContain('createReturn');
    expect(ids('PURCHASE_ORDER', 'VALIDATED')).not.toContain('createReturn');
  });

  it('hides the cancellation of a receipt or an invoice that has a live return note, and brings it back when that is cancelled', () => {
    expect(ids('GOODS_RECEIPT', 'VALIDATED', [{ type: 'PURCHASE_RETURN_NOTE', status: 'VALIDATED' }]))
      .toEqual(['createInvoice', 'createReturn']);
    expect(ids('GOODS_RECEIPT', 'VALIDATED', [{ type: 'PURCHASE_RETURN_NOTE', status: 'CANCELLED' }]))
      .toEqual(['createInvoice', 'createReturn', 'cancel']);
    expect(ids('PURCHASE_INVOICE', 'VALIDATED', [{ type: 'PURCHASE_RETURN_NOTE', status: 'DRAFT' }]))
      .toEqual(['createCreditNote', 'createReturn']);
  });

  it('validates a return note, warning its goods leave the stock, and only lets a validated one be cancelled', () => {
    expect(ids('PURCHASE_RETURN_NOTE', 'DRAFT')).toEqual(['validate']);
    expect(ids('PURCHASE_RETURN_NOTE', 'VALIDATED')).toEqual(['cancel']);
    expect(ids('PURCHASE_RETURN_NOTE', 'CANCELLED')).toEqual([]);

    expect(purchaseActions({ type: 'PURCHASE_RETURN_NOTE', status: 'DRAFT' })[0].confirm.message).toContain('leave the stock');

    const cancel = purchaseActions({ type: 'PURCHASE_RETURN_NOTE', status: 'VALIDATED', reference: 'PRN-1' })[0];
    expect(cancel.permission).toBe('PURCHASE_CANCEL');
    expect(cancel.confirm.danger).toBeTrue();
    expect(cancel.confirm.message).toContain('come back into the stock');
  });

  it('asks the right permission to create a return note, and says the goods leave the stock', () => {
    const create = purchaseActions({ type: 'GOODS_RECEIPT', status: 'VALIDATED', reference: 'GR-1' })
      .find(a => a.id === 'createReturn')!;

    expect(create.permission).toBe('PURCHASE_CREATE');
    expect(create.confirm.message).toContain('GR-1');
    expect(create.confirm.message).toContain('out of the stock');
    expect(purchaseActionOpens('createReturn')).toBe('PURCHASE_RETURN_NOTE');
  });

  it('words the creation of a return note', () => {
    const result = { id: 5, type: 'PURCHASE_RETURN_NOTE', status: 'DRAFT', issueDate: '', subtotal: 0, total: 0, lines: [], taxes: [], derived: [] } as PurchaseDocumentResponse;

    expect(purchaseActionResult('createReturn', result)).toContain('draft return note');
  });
});
