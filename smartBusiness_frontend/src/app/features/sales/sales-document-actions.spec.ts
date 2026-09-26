import { salesActions, salesActionOpens, salesActionResult } from './sales-document-actions';
import { SalesDocumentResponse, SalesDocumentStatus, SalesDocumentType } from './sales-document.model';

/** The steps a person can take, per type and status — the one definition the list and the page share. */
describe('salesActions', () => {

  function ids(type: SalesDocumentType, status: SalesDocumentStatus, derived?: { status: SalesDocumentStatus }[]) {
    return salesActions({ type, status, reference: 'X-1', derived }).map(action => action.id);
  }

  it('only offers to issue a draft', () => {
    expect(ids('QUOTE', 'DRAFT')).toEqual(['issue']);
    expect(ids('SALES_ORDER', 'DRAFT')).toEqual(['issue']);
  });

  it('lets a quote move between issued, accepted and rejected, and be converted while it is open', () => {
    expect(ids('QUOTE', 'ISSUED')).toEqual(['accept', 'reject', 'convert', 'createInvoice']);
    expect(ids('QUOTE', 'ACCEPTED')).toEqual(['reject', 'reopen', 'convert', 'createInvoice']);
    expect(ids('QUOTE', 'REJECTED')).toEqual(['accept', 'reopen']);
  });

  it('never offers to cancel a quote', () => {
    for (const status of ['DRAFT', 'ISSUED', 'ACCEPTED', 'REJECTED'] as const) {
      expect(ids('QUOTE', status)).not.toContain('cancel');
    }
  });

  it('hides "convert" once a live order was made from the quote, and brings it back when that order is cancelled', () => {
    expect(ids('QUOTE', 'ACCEPTED', [{ status: 'DRAFT' }])).not.toContain('convert');
    expect(ids('QUOTE', 'ACCEPTED', [{ status: 'CANCELLED' }, { status: 'CONFIRMED' }])).not.toContain('convert');
    expect(ids('QUOTE', 'ACCEPTED', [{ status: 'CANCELLED' }])).toContain('convert');
  });

  it('confirms or cancels an issued sales order, and delivers or cancels a confirmed one', () => {
    expect(ids('SALES_ORDER', 'ISSUED')).toEqual(['confirm', 'cancel']);
    expect(ids('SALES_ORDER', 'CONFIRMED')).toEqual(['createDelivery', 'createInvoice', 'cancel']);
    expect(ids('SALES_ORDER', 'CANCELLED')).toEqual([]);
  });

  it('lets a delivery note be delivered or cancelled, and a delivered one only cancelled', () => {
    expect(ids('DELIVERY_NOTE', 'DRAFT')).toEqual(['issue']);
    expect(ids('DELIVERY_NOTE', 'ISSUED')).toEqual(['deliver', 'cancel']);
    expect(ids('DELIVERY_NOTE', 'DELIVERED')).toEqual(['createInvoice', 'createReturn', 'cancel']);
    expect(ids('DELIVERY_NOTE', 'CANCELLED')).toEqual([]);
  });

  it('asks the right permission for the delivery steps', () => {
    const permission = (type: SalesDocumentType, status: SalesDocumentStatus, id: string) =>
      salesActions({ type, status }).find(action => action.id === id)!.permission;

    expect(permission('SALES_ORDER', 'CONFIRMED', 'createDelivery')).toBe('SALE_CREATE');
    expect(permission('DELIVERY_NOTE', 'ISSUED', 'deliver')).toBe('SALE_UPDATE');
    expect(permission('DELIVERY_NOTE', 'ISSUED', 'cancel')).toBe('SALE_CANCEL');
  });

  it('warns that delivering takes the goods out of the stock, and that cancelling a delivered note brings them back', () => {
    const deliver = salesActions({ type: 'DELIVERY_NOTE', status: 'ISSUED', reference: 'BL-1' }).find(a => a.id === 'deliver')!;
    const cancel = salesActions({ type: 'DELIVERY_NOTE', status: 'DELIVERED', reference: 'BL-1' }).find(a => a.id === 'cancel')!;

    expect(deliver.confirm.message).toContain('taken out of the stock');
    expect(cancel.confirm.message).toContain('come back into the stock');
    expect(cancel.confirm.danger).toBeTrue();
  });

  it('only lets an unpaid invoice be cancelled: once paid, even in part, its payments come first', () => {
    expect(ids('INVOICE', 'DRAFT')).toEqual(['issue']);
    expect(ids('INVOICE', 'ISSUED')).toEqual(['createCreditNote', 'createReturn', 'cancel']);
    expect(ids('INVOICE', 'PARTIALLY_PAID')).toEqual(['createCreditNote', 'createReturn']);
    expect(ids('INVOICE', 'PAID')).toEqual(['createCreditNote', 'createReturn']);
    expect(ids('INVOICE', 'CANCELLED')).toEqual([]);
  });

  it('never moves an invoice to paid by hand: there is no such step', () => {
    for (const status of ['DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'] as const) {
      expect(ids('INVOICE', status)).not.toContain('accept');
      expect(ids('INVOICE', status)).not.toContain('confirm');
      expect(ids('INVOICE', status)).not.toContain('deliver');
    }
  });

  it('hides "Create invoice" once a live invoice was made from the order or the delivery note', () => {
    expect(ids('SALES_ORDER', 'CONFIRMED', [{ type: 'INVOICE', status: 'ISSUED' } as never]))
      .toEqual(['cancel']);
    expect(ids('SALES_ORDER', 'CONFIRMED', [{ type: 'DELIVERY_NOTE', status: 'DELIVERED' } as never]))
      .toEqual(['createDelivery', 'cancel']);
    expect(ids('SALES_ORDER', 'CONFIRMED', [{ type: 'INVOICE', status: 'CANCELLED' } as never]))
      .toEqual(['createDelivery', 'createInvoice', 'cancel']);
    expect(ids('DELIVERY_NOTE', 'DELIVERED', [{ type: 'INVOICE', status: 'PAID' } as never])).toEqual(['createReturn', 'cancel']);
  });

  it('asks the right permission for the invoice steps, and warns about the stock', () => {
    const find = (type: SalesDocumentType, status: SalesDocumentStatus, id: string) =>
      salesActions({ type, status, reference: 'INV-1' }).find(action => action.id === id)!;

    expect(find('DELIVERY_NOTE', 'DELIVERED', 'createInvoice').permission).toBe('SALE_CREATE');
    expect(find('INVOICE', 'ISSUED', 'cancel').permission).toBe('SALE_CANCEL');
    expect(find('INVOICE', 'ISSUED', 'cancel').confirm.danger).toBeTrue();
    expect(find('INVOICE', 'DRAFT', 'issue').confirm.message).toContain('goods leave the stock');
    expect(find('DELIVERY_NOTE', 'DELIVERED', 'createInvoice').confirm.message).toContain('moves no stock');
    expect(find('QUOTE', 'ISSUED', 'createInvoice').confirm.message).toContain('INV-1');
  });

  it('offers a credit note on an invoice that stands, never on a draft or a cancelled one', () => {
    expect(ids('INVOICE', 'DRAFT')).not.toContain('createCreditNote');
    expect(ids('INVOICE', 'CANCELLED')).not.toContain('createCreditNote');
    expect(ids('QUOTE', 'ISSUED')).not.toContain('createCreditNote');
    expect(ids('DELIVERY_NOTE', 'DELIVERED')).not.toContain('createCreditNote');
  });

  it('hides "Cancel invoice" while the invoice has a live credit note, and brings it back once that is cancelled', () => {
    expect(ids('INVOICE', 'ISSUED', [{ type: 'CREDIT_NOTE', status: 'DRAFT' } as never])).toEqual(['createCreditNote', 'createReturn']);
    expect(ids('INVOICE', 'ISSUED', [{ type: 'CREDIT_NOTE', status: 'CANCELLED' } as never])).toEqual(['createCreditNote', 'createReturn', 'cancel']);
  });

  it('issues a credit note, warning that it is taken off its invoice at once, and only lets an issued one be cancelled', () => {
    expect(ids('CREDIT_NOTE', 'DRAFT')).toEqual(['issue']);
    expect(ids('CREDIT_NOTE', 'ISSUED')).toEqual(['cancel']);
    expect(ids('CREDIT_NOTE', 'CANCELLED')).toEqual([]);

    const issue = salesActions({ type: 'CREDIT_NOTE', status: 'DRAFT' })[0];
    expect(issue.confirm.message).toContain('taken off its invoice');
    expect(issue.confirm.message).toContain('does not move the stock');

    const cancel = salesActions({ type: 'CREDIT_NOTE', status: 'ISSUED', reference: 'CN-1' })[0];
    expect(cancel.permission).toBe('SALE_CANCEL');
    expect(cancel.confirm.danger).toBeTrue();
    expect(cancel.confirm.message).toContain('asks for that amount again');
  });

  it('asks the right permission to create a credit note, and warns it moves no stock', () => {
    const create = salesActions({ type: 'INVOICE', status: 'PAID', reference: 'INV-1' }).find(a => a.id === 'createCreditNote')!;

    expect(create.permission).toBe('SALE_CREATE');
    expect(create.confirm.message).toContain('INV-1');
    expect(create.confirm.message).toContain('does not move the stock');
  });

  it('says which document a creating step opens', () => {
    expect(salesActionOpens('createCreditNote')).toBe('CREDIT_NOTE');
    expect(salesActionOpens('createInvoice')).toBe('INVOICE');
    expect(salesActionOpens('convert')).toBe('SALES_ORDER');
    expect(salesActionOpens('createDelivery')).toBe('DELIVERY_NOTE');
    expect(salesActionOpens('deliver')).toBeNull();
  });

  it('asks the right permission for each step', () => {
    const permission = (type: SalesDocumentType, status: SalesDocumentStatus, id: string) =>
      salesActions({ type, status }).find(action => action.id === id)!.permission;

    expect(permission('QUOTE', 'DRAFT', 'issue')).toBe('SALE_UPDATE');
    expect(permission('QUOTE', 'ISSUED', 'accept')).toBe('SALE_UPDATE');
    expect(permission('QUOTE', 'ISSUED', 'convert')).toBe('SALE_CREATE');
    expect(permission('SALES_ORDER', 'ISSUED', 'cancel')).toBe('SALE_CANCEL');
  });

  it('gives every step a confirmation, and marks only what cannot be taken back as dangerous', () => {
    const all = [
      ...salesActions({ type: 'QUOTE', status: 'DRAFT' }),
      ...salesActions({ type: 'QUOTE', status: 'ISSUED' }),
      ...salesActions({ type: 'QUOTE', status: 'REJECTED' }),
      ...salesActions({ type: 'SALES_ORDER', status: 'ISSUED' })
    ];

    for (const action of all) {
      expect(action.confirm.header).toBeTruthy();
      expect(action.confirm.message).toBeTruthy();
      expect(action.confirm.acceptLabel).toBeTruthy();
    }
    expect(all.filter(action => action.confirm.danger).map(action => action.id)).toEqual(['cancel']);
  });

  it('names the document in the question, or says "this quote" for a draft that has no number', () => {
    const named = salesActions({ type: 'QUOTE', status: 'ISSUED', reference: 'QUO-2026-00001' });
    expect(named.find(a => a.id === 'accept')!.confirm.message).toContain('QUO-2026-00001');

    const unnamed = salesActions({ type: 'QUOTE', status: 'ISSUED' });
    expect(unnamed.find(a => a.id === 'accept')!.confirm.message).toContain('this quote');
  });

  it('warns that confirming an order reserves its goods and that cancelling puts them back on sale', () => {
    const [confirm, cancel] = salesActions({ type: 'SALES_ORDER', status: 'ISSUED', reference: 'SO-1' });

    expect(confirm.confirm.message).toContain('reserved');
    expect(cancel.confirm.message).toContain('go back on sale');
  });

  it('words the result of a step', () => {
    const result = (overrides: Partial<SalesDocumentResponse>) => ({
      id: 5, type: 'QUOTE', status: 'ISSUED', reference: 'QUO-2026-00001', issueDate: '', subtotal: 0, total: 0,
      lines: [], taxes: [], derived: [], ...overrides
    } as SalesDocumentResponse);

    expect(salesActionResult('issue', result({}))).toBe('Quote issued as QUO-2026-00001.');
    expect(salesActionResult('accept', result({ status: 'ACCEPTED' }))).toBe('The quote is now accepted.');
    expect(salesActionResult('cancel', result({ type: 'SALES_ORDER', status: 'CANCELLED' })))
      .toBe('The sales order is now cancelled.');
    expect(salesActionResult('convert', result({ type: 'SALES_ORDER' }))).toContain('draft sales order');
    expect(salesActionResult('createDelivery', result({ type: 'DELIVERY_NOTE' }))).toContain('draft delivery note');
    expect(salesActionResult('createInvoice', result({ type: 'INVOICE' }))).toContain('draft invoice');
    expect(salesActionResult('createCreditNote', result({ type: 'CREDIT_NOTE' }))).toContain('draft credit note');
    expect(salesActionResult('cancel', result({ type: 'INVOICE', status: 'CANCELLED' }))).toBe('The invoice is now cancelled.');
    expect(salesActionResult('deliver', result({ type: 'DELIVERY_NOTE', status: 'DELIVERED', reference: 'BL-2026-00001' })))
      .toBe('BL-2026-00001 delivered — the goods have left the stock.');
  });

  it('offers a return note on a delivered note and on an invoice that stands, never on an undelivered note or a draft', () => {
    expect(ids('DELIVERY_NOTE', 'DELIVERED')).toContain('createReturn');
    expect(ids('DELIVERY_NOTE', 'ISSUED')).not.toContain('createReturn');
    expect(ids('DELIVERY_NOTE', 'CANCELLED')).not.toContain('createReturn');
    for (const status of ['ISSUED', 'PARTIALLY_PAID', 'PAID'] as const) {
      expect(ids('INVOICE', status)).toContain('createReturn');
    }
    expect(ids('INVOICE', 'DRAFT')).not.toContain('createReturn');
    expect(ids('INVOICE', 'CANCELLED')).not.toContain('createReturn');
    expect(ids('QUOTE', 'ISSUED')).not.toContain('createReturn');
    expect(ids('SALES_ORDER', 'CONFIRMED')).not.toContain('createReturn');
  });

  it('hides the cancellation of a delivery note or an invoice that has a live return note, and brings it back when that is cancelled', () => {
    expect(ids('DELIVERY_NOTE', 'DELIVERED', [{ type: 'RETURN_NOTE', status: 'ISSUED' } as never]))
      .toEqual(['createInvoice', 'createReturn']);
    expect(ids('DELIVERY_NOTE', 'DELIVERED', [{ type: 'RETURN_NOTE', status: 'CANCELLED' } as never]))
      .toEqual(['createInvoice', 'createReturn', 'cancel']);
    expect(ids('INVOICE', 'ISSUED', [{ type: 'RETURN_NOTE', status: 'DRAFT' } as never]))
      .toEqual(['createCreditNote', 'createReturn']);
  });

  it('issues a return note, warning its goods come back into the stock, and only lets an issued one be cancelled', () => {
    expect(ids('RETURN_NOTE', 'DRAFT')).toEqual(['issue']);
    expect(ids('RETURN_NOTE', 'ISSUED')).toEqual(['cancel']);
    expect(ids('RETURN_NOTE', 'CANCELLED')).toEqual([]);

    expect(salesActions({ type: 'RETURN_NOTE', status: 'DRAFT' })[0].confirm.message).toContain('come back into the stock');

    const cancel = salesActions({ type: 'RETURN_NOTE', status: 'ISSUED', reference: 'RN-1' })[0];
    expect(cancel.permission).toBe('SALE_CANCEL');
    expect(cancel.confirm.danger).toBeTrue();
    expect(cancel.confirm.message).toContain('go back out of the stock');
  });

  it('asks the right permission to create a return note, and says the goods go back into the stock', () => {
    const create = salesActions({ type: 'DELIVERY_NOTE', status: 'DELIVERED', reference: 'BL-1' })
      .find(a => a.id === 'createReturn')!;

    expect(create.permission).toBe('SALE_CREATE');
    expect(create.confirm.message).toContain('BL-1');
    expect(create.confirm.message).toContain('back into the stock');
    expect(salesActionOpens('createReturn')).toBe('RETURN_NOTE');
  });

  it('words the creation of a return note', () => {
    const result = { id: 5, type: 'RETURN_NOTE', status: 'DRAFT', issueDate: '', subtotal: 0, total: 0, lines: [], taxes: [], derived: [] } as SalesDocumentResponse;

    expect(salesActionResult('createReturn', result)).toContain('draft return note');
  });
});
