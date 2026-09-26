import { currencyLabel, fromPurchaseDocument, fromSalesDocument } from './printable-document.model';
import { SalesDocumentResponse, SalesDocumentType } from '../sales/sales-document.model';
import { PurchaseDocumentResponse, PurchaseDocumentType } from '../purchases/purchase-document.model';

describe('printable document model', () => {

  function sales(overrides: Partial<SalesDocumentResponse> = {}): SalesDocumentResponse {
    return {
      id: 5, type: 'QUOTE', status: 'ISSUED', reference: 'QUO-1', customerId: 3, customerName: 'Client',
      issueDate: '2026-09-20', dueDate: '2026-10-20', subtotal: 30, total: 37.057, notes: 'n', terms: 't',
      lines: [{ designation: 'Item', reference: 'P-1', quantity: 2, unitPrice: 15, discountRate: 0, vatRate: 19, lineTotal: 30 }],
      taxes: [{ kind: 'VAT_RATE', name: 'VAT 19%', rate: 19, base: 30, amount: 5.7 }],
      derived: [],
      ...overrides
    };
  }

  function purchase(overrides: Partial<PurchaseDocumentResponse> = {}): PurchaseDocumentResponse {
    return {
      id: 8, type: 'GOODS_RECEIPT', status: 'VALIDATED', reference: 'GR-1', supplierId: 4, supplierName: 'Supplier',
      warehouseId: 1, warehouseName: 'Sfax', issueDate: '2026-09-21', subtotal: 10, total: 10, lines: [], taxes: [],
      derived: [],
      ...overrides
    };
  }

  const customer = { id: 3, name: 'Client', type: 'COMPANY' } as never;
  const supplier = { id: 4, name: 'Supplier', type: 'COMPANY' } as never;

  it('carries the document, its party, its lines and its taxes over', () => {
    const printable = fromSalesDocument(sales(), customer);

    expect(printable.reference).toBe('QUO-1');
    expect(printable.party.name).toBe('Client');
    expect(printable.lines[0]).toEqual({
      designation: 'Item', reference: 'P-1', quantity: 2, unitPrice: 15, discountRate: 0, vatRate: 19, lineTotal: 30
    });
    expect(printable.taxes[0].amount).toBe(5.7);
    expect(printable.total).toBe(37.057);
    expect(printable.notes).toBe('n');
  });

  it('words each type of document in French', () => {
    const words: [SalesDocumentType | PurchaseDocumentType, string, string, string][] = [
      ['QUOTE', 'DEVIS', 'Client', 'Arrêté le présent devis à la somme de'],
      ['SALES_ORDER', 'COMMANDE CLIENT', 'Client', 'Arrêtée la présente commande à la somme de'],
      ['DELIVERY_NOTE', 'BON DE LIVRAISON', 'Client', 'Arrêté le présent bon de livraison à la somme de'],
      ['INVOICE', 'FACTURE', 'Client', 'Arrêtée la présente facture à la somme de'],
      ['CREDIT_NOTE', 'AVOIR', 'Client', 'Arrêté le présent avoir à la somme de'],
      ['PURCHASE_INVOICE', 'FACTURE D\'ACHAT', 'Fournisseur', 'Arrêtée la présente facture à la somme de'],
      ['PURCHASE_CREDIT_NOTE', 'AVOIR FOURNISSEUR', 'Fournisseur', 'Arrêté le présent avoir à la somme de'],
      ['RETURN_NOTE', 'BON DE RETOUR', 'Client', 'Arrêté le présent bon de retour à la somme de'],
      ['PURCHASE_RETURN_NOTE', 'BON DE RETOUR FOURNISSEUR', 'Fournisseur', 'Arrêté le présent bon de retour à la somme de'],
      ['PURCHASE_ORDER', 'COMMANDE FOURNISSEUR', 'Fournisseur', 'Arrêtée la présente commande à la somme de'],
      ['GOODS_RECEIPT', 'BON DE RÉCEPTION', 'Fournisseur', 'Arrêté le présent bon de réception à la somme de']
    ];

    for (const [type, title, partyLabel, phrase] of words) {
      const printable = type === 'QUOTE' || type === 'SALES_ORDER' || type === 'DELIVERY_NOTE' || type === 'INVOICE' || type === 'CREDIT_NOTE' || type === 'RETURN_NOTE'
        ? fromSalesDocument(sales({ type }), customer)
        : fromPurchaseDocument(purchase({ type }), supplier);

      expect(printable.title).toBe(title);
      expect(printable.partyLabel).toBe(partyLabel);
      expect(printable.totalPhrase).toBe(phrase);
    }
  });

  it('says what the due date means on each document', () => {
    expect(fromSalesDocument(sales({ type: 'QUOTE' }), customer).dueDateLabel).toBe('Valable jusqu\'au');
    expect(fromPurchaseDocument(purchase({ type: 'PURCHASE_ORDER' }), supplier).dueDateLabel).toBe('Livraison prévue le');
  });

  it('marks a draft and a cancelled document, and every other status as active', () => {
    expect(fromSalesDocument(sales({ status: 'DRAFT' }), customer).state).toBe('DRAFT');
    expect(fromSalesDocument(sales({ status: 'CANCELLED' }), customer).state).toBe('CANCELLED');
    for (const status of ['ISSUED', 'ACCEPTED', 'REJECTED', 'CONFIRMED', 'DELIVERED', 'PARTIALLY_PAID', 'PAID'] as const) {
      expect(fromSalesDocument(sales({ status }), customer).state).toBe('ACTIVE');
    }
    expect(fromPurchaseDocument(purchase({ status: 'DRAFT' }), supplier).state).toBe('DRAFT');
    expect(fromPurchaseDocument(purchase({ status: 'VALIDATED' }), supplier).state).toBe('ACTIVE');
    expect(fromPurchaseDocument(purchase({ status: 'CANCELLED' }), supplier).state).toBe('CANCELLED');
  });

  it('names the warehouse a delivery note leaves from and a receipt arrives at', () => {
    const delivery = fromSalesDocument(sales({ type: 'DELIVERY_NOTE', warehouseName: 'Tunis' }), customer);
    expect(delivery.warehouseLabel).toBe('Entrepôt de départ');
    expect(delivery.warehouseName).toBe('Tunis');
    expect(fromPurchaseDocument(purchase(), supplier).warehouseLabel).toBe('Entrepôt de réception');
    expect(fromSalesDocument(sales(), customer).warehouseLabel).toBe('');
  });

  it('labels the last total "Net à déduire" on a credit note and "Net à payer" on the others', () => {
    expect(fromSalesDocument(sales({ type: 'CREDIT_NOTE' }), customer).netLabel).toBe('Net à déduire');
    expect(fromSalesDocument(sales({ type: 'INVOICE' }), customer).netLabel).toBe('Net à payer');
    expect(fromPurchaseDocument(purchase(), supplier).netLabel).toBe('Net à payer');
    expect(fromPurchaseDocument(purchase({ type: 'PURCHASE_CREDIT_NOTE' }), supplier).netLabel).toBe('Net à déduire');
    expect(fromSalesDocument(sales({ type: 'RETURN_NOTE' }), customer).netLabel).toBe('Net à déduire');
  });

  it('names the warehouse a return note comes back to, or leaves from', () => {
    const customerReturn = fromSalesDocument(sales({ type: 'RETURN_NOTE', warehouseName: 'Tunis' }), customer);
    expect(customerReturn.warehouseLabel).toBe('Entrepôt de retour');
    expect(customerReturn.warehouseName).toBe('Tunis');
    expect(fromPurchaseDocument(purchase({ type: 'PURCHASE_RETURN_NOTE' }), supplier).warehouseLabel).toBe('Entrepôt de départ');
  });

  it('gives an invoice no warehouse heading: its goods left from one, but the sheet does not say so', () => {
    const invoice = fromSalesDocument(sales({ type: 'INVOICE', warehouseName: 'Tunis' }), customer);
    expect(invoice.warehouseLabel).toBe('');
  });

  it('gives no reference to a draft, and carries the warehouse of a receipt', () => {
    expect(fromSalesDocument(sales({ reference: null, status: 'DRAFT' }), customer).reference).toBeNull();
    expect(fromPurchaseDocument(purchase(), supplier).warehouseName).toBe('Sfax');
  });

  it('writes the dinar as DT and any other currency as its code', () => {
    expect(currencyLabel('TND')).toBe('DT');
    expect(currencyLabel('EUR')).toBe('EUR');
  });
});
