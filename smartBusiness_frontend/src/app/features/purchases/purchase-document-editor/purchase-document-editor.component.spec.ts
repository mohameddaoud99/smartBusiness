import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';

import { PurchaseDocumentEditorComponent } from './purchase-document-editor.component';
import { PurchaseDocumentResponse, PurchaseDocumentType } from '../purchase-document.model';
import { TaxResponse } from '../../settings/taxes/tax.model';
import { ProductResponse } from '../../products/product.model';
import { WarehouseResponse } from '../../settings/warehouses/warehouse.model';

describe('PurchaseDocumentEditorComponent', () => {

  const api = 'http://localhost:8080/api';
  const url = `${api}/purchase-documents`;

  let fixture: ComponentFixture<PurchaseDocumentEditorComponent>;
  let component: PurchaseDocumentEditorComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  function tax(id: number, name: string, kind: TaxResponse['kind'], extra: Partial<TaxResponse> = {}): TaxResponse {
    return {
      id, name, kind, includedInVatBase: false, appliesToLine: kind !== 'FIXED_PER_DOCUMENT',
      activeByDefault: false, active: true, system: true, createdAt: '', updatedAt: '', ...extra
    };
  }

  const taxes: TaxResponse[] = [
    tax(1, 'TVA 19%', 'VAT_RATE', { rate: 19, activeByDefault: true }),
    tax(2, 'TVA 7%', 'VAT_RATE', { rate: 7, active: false }),
    tax(3, 'FODEC', 'PERCENTAGE_SURCHARGE', { rate: 1, includedInVatBase: true, activeByDefault: true }),
    tax(4, 'Timbre fiscal', 'FIXED_PER_DOCUMENT', { amount: 1, activeByDefault: true })
  ];

  const fabric = {
    id: 20, reference: 'P-0020', name: 'Fabric', purpose: 'BOTH', purchasePrice: 7.5, salePrice: 12.5,
    defaultTaxes: [{ id: 1, name: 'TVA 19%' }]
  } as ProductResponse;

  const saleOnly = { id: 21, reference: 'P-0021', name: 'Finished shirt', purpose: 'SALE', defaultTaxes: [] } as unknown as ProductResponse;

  function warehouse(id: number, name: string, active = true): WarehouseResponse {
    return { id, name, defaultWarehouse: id === 1, active, createdAt: '', updatedAt: '' };
  }

  function page<T>(content: T[]) {
    return { content, totalElements: content.length, totalPages: 1, size: 10, number: 0 };
  }

  function savedDocument(overrides: Partial<PurchaseDocumentResponse> = {}): PurchaseDocumentResponse {
    return {
      id: 5, type: 'PURCHASE_ORDER', status: 'DRAFT', reference: null, supplierId: 3, supplierName: 'Fournisseur Alpha',
      warehouseId: null, warehouseName: null, issueDate: '2026-09-20', dueDate: null, subtotal: 30, total: 37.057,
      notes: null, terms: null,
      lines: [
        { id: 1, productId: 20, reference: 'P-0020', designation: 'Fabric', quantity: 1, unitPrice: 30,
          discountRate: 0, vatTaxId: 1, vatRate: 19, lineTotal: 30 }
      ],
      taxes: [
        { taxId: 3, kind: 'PERCENTAGE_SURCHARGE', name: 'FODEC', rate: 1, base: 30, amount: 0.3 },
        { taxId: null, kind: 'VAT_RATE', name: 'VAT 19%', rate: 19, base: 30.3, amount: 5.757 },
        { taxId: 4, kind: 'FIXED_PER_DOCUMENT', name: 'Timbre fiscal', amount: 1 }
      ],
      derived: [],
      ...overrides
    };
  }

  function receipt(overrides: Partial<PurchaseDocumentResponse> = {}): PurchaseDocumentResponse {
    return savedDocument({ type: 'GOODS_RECEIPT', warehouseId: 1, warehouseName: 'Default warehouse', ...overrides });
  }

  /** Builds the page for a route and answers the option requests it makes. */
  function open(options: { type?: PurchaseDocumentType; id?: string; warehouses?: WarehouseResponse[] } = {}) {
    const type = options.type ?? 'PURCHASE_ORDER';
    TestBed.configureTestingModule({
      imports: [PurchaseDocumentEditorComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        MessageService,
        ConfirmationService,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { data: { documentType: type } },
            paramMap: of(convertToParamMap(options.id ? { id: options.id } : {}))
          }
        }
      ]
    });

    fixture = TestBed.createComponent(PurchaseDocumentEditorComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);

    fixture.detectChanges();

    httpMock.expectOne(`${api}/settings/taxes`).flush(taxes);
    httpMock.expectOne(r => r.url === `${api}/products`).flush(page([fabric, saleOnly]));
    httpMock.expectOne(r => r.url === `${api}/suppliers`)
      .flush(page([{ id: 3, name: 'Fournisseur Alpha' }, { id: 4, name: 'Fournisseur Beta' }]));
    if (type === 'GOODS_RECEIPT' || type === 'PURCHASE_INVOICE' || type === 'PURCHASE_RETURN_NOTE') {
      httpMock.expectOne(`${api}/warehouses`)
        .flush(options.warehouses ?? [warehouse(1, 'Default warehouse'), warehouse(2, 'Annex'), warehouse(3, 'Old', false)]);
    }
  }

  function openSaved(document: PurchaseDocumentResponse) {
    open({ type: document.type, id: '5' });
    httpMock.expectOne(`${url}/5`).flush(document);
    fixture.detectChanges();
    // A validated invoice shows its payments, which the page asks for
    if (document.type === 'PURCHASE_INVOICE' && document.status !== 'DRAFT') {
      answerPayments([]);
    }
  }

  function answerPayments(content: unknown[]) {
    const req = httpMock.expectOne(r => r.url === `${api}/supplier-payments`);
    expect(req.request.params.get('invoiceId')).toBe('5');
    req.flush(page(content));
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  // ----- New document -----

  it('starts a new draft with one line, the default VAT and the default document taxes', () => {
    open();

    expect(component.title()).toBe('New purchase order');
    expect(component.isNew()).toBeTrue();
    expect(component.lines.length).toBe(1);
    expect(component.lines.at(0).value.vatTaxId).toBe(1);
    expect(component.form.value.taxIds).toEqual([3, 4]);
    expect(component.loading()).toBeFalse();
  });

  it('offers only active VAT rates on the lines and only surcharges and flat charges on the document', () => {
    open();

    expect(component.vatTaxes().map(t => t.name)).toEqual(['TVA 19%']);
    expect(component.documentTaxes().map(t => t.name)).toEqual(['FODEC', 'Timbre fiscal']);
  });

  it('does not offer a sale-only product', () => {
    open();

    expect(component.purchasableProducts().map(p => p.name)).toEqual(['Fabric']);
  });

  it('fills a line from the picked product at its PURCHASE price with its default VAT', () => {
    open();

    component.onProductPicked(0, 20);

    const line = component.lines.at(0).value;
    expect(line.designation).toBe('Fabric');
    expect(line.reference).toBe('P-0020');
    expect(line.unitPrice).toBe(7.5);
    expect(line.vatTaxId).toBe(1);
  });

  it('keeps what was typed when the product pick is cleared', () => {
    open();
    component.onProductPicked(0, 20);

    component.onProductPicked(0, null);

    const line = component.lines.at(0).value;
    expect(line.productId).toBeNull();
    expect(line.reference).toBeNull();
    expect(line.designation).toBe('Fabric');
  });

  it('adds and removes lines, but always keeps one', () => {
    open();
    component.addLine();
    expect(component.lines.length).toBe(2);

    component.removeLine(1);
    expect(component.lines.length).toBe(1);
  });

  it('does not call the API while the form is invalid', () => {
    open();

    component.saveDraft();

    httpMock.expectNone(url);
    expect(component.form.touched).toBeTrue();
  });

  it('POSTs a new purchase order with ISO dates and no warehouse, then opens it', () => {
    open();
    component.form.patchValue({ supplierId: 3, issueDate: new Date(2026, 8, 20), notes: 'Thanks' });
    component.lines.at(0).patchValue({ designation: 'Fabric', quantity: 2, unitPrice: 30 });

    component.saveDraft();

    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.type).toBe('PURCHASE_ORDER');
    expect(req.request.body.supplierId).toBe(3);
    expect(req.request.body.warehouseId).toBeNull();
    expect(req.request.body.issueDate).toBe('2026-09-20');
    expect(req.request.body.taxIds).toEqual([3, 4]);
    expect(req.request.body.lines[0]).toEqual({
      productId: null, reference: null, designation: 'Fabric',
      quantity: 2, sourceLineId: null, unitPrice: 30, discountRate: 0, vatTaxId: 1
    });
    req.flush(savedDocument());

    expect(router.navigate).toHaveBeenCalledWith(['/purchase-orders', 5], { replaceUrl: true });
    expect(component.saving()).toBeFalse();
  });

  it('releases the saving state on an API error', () => {
    open();
    component.form.patchValue({ supplierId: 3 });
    component.lines.at(0).patchValue({ designation: 'Fabric' });

    component.saveDraft();
    expect(component.saving()).toBeTrue();
    httpMock.expectOne(url).flush({ message: 'boom' }, { status: 422, statusText: 'Unprocessable' });

    expect(component.saving()).toBeFalse();
  });

  it('asks the server for the totals once the form is valid, after a short pause', fakeAsync(() => {
    open();
    component.form.patchValue({ supplierId: 3 });
    component.lines.at(0).patchValue({ designation: 'Fabric', quantity: 1, unitPrice: 30 });

    tick(300);

    const req = httpMock.expectOne(`${url}/preview`);
    expect(req.request.method).toBe('POST');
    req.flush(savedDocument({ id: null }));
    expect(component.totals()?.total).toBe(37.057);
  }));

  it('asks nothing while the form is invalid', fakeAsync(() => {
    open();
    component.lines.at(0).patchValue({ designation: 'Fabric' });

    tick(300);

    httpMock.expectNone(`${url}/preview`);
  }));

  // ----- Goods receipt -----

  it('gives a goods receipt a required warehouse, starting on the default one, active ones only', () => {
    open({ type: 'GOODS_RECEIPT' });

    expect(component.title()).toBe('New goods receipt');
    expect(component.warehouses().map(w => w.name)).toEqual(['Default warehouse', 'Annex']);
    expect(component.form.value.warehouseId).toBe(1);

    component.form.patchValue({ warehouseId: null, supplierId: 3 });
    component.lines.at(0).patchValue({ designation: 'Fabric' });
    expect(component.form.controls['warehouseId'].hasError('required')).toBeTrue();
    expect(component.form.valid).toBeFalse();
  });

  it('a purchase order has no warehouse to choose, and sends none', () => {
    open();

    expect(component.form.controls['warehouseId'].hasError('required')).toBeFalse();
    expect(component.warehouses()).toEqual([]);
  });

  it('POSTs a goods receipt with its warehouse', () => {
    open({ type: 'GOODS_RECEIPT' });
    component.form.patchValue({ supplierId: 3, warehouseId: 2 });
    component.lines.at(0).patchValue({ designation: 'Fabric' });

    component.saveDraft();

    const req = httpMock.expectOne(url);
    expect(req.request.body.type).toBe('GOODS_RECEIPT');
    expect(req.request.body.warehouseId).toBe(2);
    req.flush(receipt());
    expect(router.navigate).toHaveBeenCalledWith(['/goods-receipts', 5], { replaceUrl: true });
  });

  it('tells the user the goods enter the stock before validating a receipt', () => {
    openSaved(receipt());
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm');

    component.confirmValidate();

    expect(confirm.calls.mostRecent().args[0].message).toContain('added to the stock');
  });

  // ----- Printing -----

  it('opens the print preview of a saved document in a new tab', () => {
    openSaved(savedDocument());
    const opened = spyOn(window, 'open').and.returnValue(null);

    component.print();

    expect(opened).toHaveBeenCalledWith('/print/purchases/5', '_blank');
  });

  it('offers no print button while the document is not saved yet', () => {
    open();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Print');
  });

  // ----- Existing draft -----

  it('loads a saved draft into the form and lets it be edited', () => {
    openSaved(savedDocument());

    expect(component.isNew()).toBeFalse();
    expect(component.readOnly()).toBeFalse();
    expect(component.title()).toBe('Purchase order draft');
    expect(component.form.value.supplierId).toBe(3);
    expect(component.form.value.issueDate).toEqual(new Date(2026, 8, 20));
    expect(component.form.value.taxIds).toEqual([3, 4]);
    expect(component.lines.length).toBe(1);
    expect(component.form.enabled).toBeTrue();
    expect(component.totals()?.total).toBe(37.057);
  });

  it('PUTs a saved draft when it is saved again', () => {
    openSaved(savedDocument());
    component.lines.at(0).patchValue({ quantity: 3 });

    component.saveDraft();

    const req = httpMock.expectOne(`${url}/5`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.lines[0].quantity).toBe(3);
    req.flush(savedDocument());
  });

  it('saves, then validates, once the user confirms', () => {
    openSaved(savedDocument());
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.confirmValidate();

    httpMock.expectOne(`${url}/5`).flush(savedDocument());
    httpMock.expectOne(`${url}/5/validate`).flush(savedDocument({ status: 'VALIDATED', reference: 'PO-2026-00001' }));
    expect(component.saving()).toBeFalse();
  });

  it('validates nothing before the user confirms', () => {
    openSaved(savedDocument());
    spyOn(TestBed.inject(ConfirmationService), 'confirm');

    component.confirmValidate();

    httpMock.expectNone(`${url}/5/validate`);
  });

  it('deletes a draft once the user confirms, then goes back to the list', () => {
    openSaved(savedDocument());
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.confirmDelete();

    const req = httpMock.expectOne(`${url}/5`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    expect(router.navigate).toHaveBeenCalledWith(['/purchase-orders']);
  });

  // ----- Validated documents -----

  it('shows a validated document read-only, with its number', () => {
    openSaved(savedDocument({ status: 'VALIDATED', reference: 'PO-2026-00001' }));

    expect(component.readOnly()).toBeTrue();
    expect(component.title()).toBe('PO-2026-00001');
    expect(component.form.disabled).toBeTrue();
  });

  it('offers to create a receipt or an invoice and to cancel a validated purchase order', () => {
    openSaved(savedDocument({ status: 'VALIDATED', reference: 'PO-2026-00001' }));

    expect(component.actions().map(a => a.label)).toEqual(['Create receipt', 'Create invoice', 'Cancel order']);
  });

  it('still offers a receipt when the order was already received in part: orders are received in several parts', () => {
    openSaved(savedDocument({
      status: 'VALIDATED', reference: 'PO-2026-00001',
      derived: [{
        id: 8, type: 'GOODS_RECEIPT', status: 'VALIDATED', supplierId: 3, supplierName: 'Fournisseur Alpha',
        issueDate: '2026-09-20', total: 10, createdAt: ''
      }]
    }));

    expect(component.actions().map(a => a.label)).toContain('Create receipt');
  });

  it('offers to invoice or cancel a validated goods receipt', () => {
    openSaved(receipt({ status: 'VALIDATED', reference: 'GR-2026-00001' }));

    expect(component.actions().map(a => a.label)).toEqual(['Create invoice', 'Create return note', 'Cancel receipt']);
  });

  it('offers nothing on a cancelled document', () => {
    openSaved(savedDocument({ status: 'CANCELLED', reference: 'PO-2026-00001' }));

    expect(component.actions()).toEqual([]);
  });

  it('converts a purchase order and opens the new goods receipt', () => {
    openSaved(savedDocument({ status: 'VALIDATED', reference: 'PO-2026-00001' }));

    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());
    component.actions().find(a => a.label === 'Create receipt')!.run();

    httpMock.expectOne(`${url}/5/convert-to-receipt`).flush(receipt({ id: 9, sourceId: 5 }));
    expect(router.navigate).toHaveBeenCalledWith(['/goods-receipts', 9]);
  });

  it('warns that the goods leave the stock before cancelling a receipt, then cancels it', () => {
    openSaved(receipt({ status: 'VALIDATED', reference: 'GR-2026-00001' }));
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Cancel receipt')!.run();

    expect(confirm.calls.mostRecent().args[0].message).toContain('taken back out of the stock');
    const req = httpMock.expectOne(`${url}/5/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush(receipt({ status: 'CANCELLED', reference: 'GR-2026-00001' }));
    expect(component.document()?.status).toBe('CANCELLED');
  });

  it('keeps a validated receipt shown in a warehouse that was deactivated since', () => {
    openSaved(receipt({ status: 'VALIDATED', reference: 'GR-2026-00001', warehouseId: 3, warehouseName: 'Old' }));

    expect(component.form.getRawValue().warehouseId).toBe(3);
    expect(component.warehouses().some(w => w.id === 3)).toBeTrue();
  });

  it('asks before creating a receipt and does nothing when the user declines', () => {
    openSaved(savedDocument({ status: 'VALIDATED', reference: 'PO-2026-00001' }));
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm');

    for (const action of component.actions()) {
      action.run();
    }

    expect(confirm).toHaveBeenCalledTimes(3); // create receipt, create invoice, cancel order
    httpMock.expectNone(`${url}/5/convert-to-receipt`);
    httpMock.expectNone(`${url}/5/convert-to-invoice`);
    httpMock.expectNone(`${url}/5/cancel`);
  });

  // ----- Invoice -----

  const invoice = (overrides: Partial<PurchaseDocumentResponse> = {}) =>
    savedDocument({
      type: 'PURCHASE_INVOICE', warehouseId: 2, warehouseName: 'Annex', reference: 'PINV-2026-00001', status: 'VALIDATED',
      total: 90, paidAmount: 0, balance: 90, ...overrides
    });

  it('starts a new invoice on the default warehouse, which it requires', () => {
    open({ type: 'PURCHASE_INVOICE' });

    expect(component.title()).toBe('New purchase invoice');
    expect(component.form.value.warehouseId).toBe(1);
    component.form.patchValue({ warehouseId: null });
    expect(component.form.controls['warehouseId'].valid).toBeFalse();
  });

  it('POSTs a new invoice with its warehouse, then opens it', () => {
    open({ type: 'PURCHASE_INVOICE' });
    component.form.patchValue({ supplierId: 3, warehouseId: 2, issueDate: new Date(2026, 8, 20) });
    component.lines.at(0).patchValue({ designation: 'Fabric', quantity: 1, unitPrice: 30 });

    component.saveDraft();

    const req = httpMock.expectOne(url);
    expect(req.request.body.type).toBe('PURCHASE_INVOICE');
    expect(req.request.body.warehouseId).toBe(2);
    req.flush(invoice({ status: 'DRAFT', reference: null }));
    expect(router.navigate).toHaveBeenCalledWith(['/purchase-invoices', 5], { replaceUrl: true });
  });

  it('shows the payments of a validated invoice, and none while it is a draft', () => {
    openSaved(invoice({ status: 'DRAFT', reference: null }));

    expect((fixture.nativeElement as HTMLElement).querySelector('app-invoice-payments')).toBeNull();
    httpMock.expectNone(r => r.url === `${api}/supplier-payments`);
  });

  it('lists the payments of a validated invoice with what is left to pay, as payments to the supplier', () => {
    openSaved(invoice({ status: 'PARTIALLY_PAID', paidAmount: 20, balance: 70 }));

    const text = (fixture.nativeElement as HTMLElement).querySelector('app-invoice-payments')!.textContent!;
    expect(text).toContain('Payments');
    expect(text).toContain('70.000');
    expect(text).toContain('No payment yet');
  });

  it('titles the status of an unpaid invoice "Unpaid"', () => {
    openSaved(invoice());

    expect((fixture.nativeElement as HTMLElement).querySelector('app-page-header')!.textContent).toContain('Unpaid');
  });

  it('offers a credit note and the cancellation of an unpaid invoice, and only the credit note once it has been paid', () => {
    openSaved(invoice());
    expect(component.actions().map(a => a.label)).toEqual(['Create credit note', 'Create return note', 'Cancel invoice']);

    component.document.set(invoice({ status: 'PARTIALLY_PAID', paidAmount: 20, balance: 70 }));
    expect(component.actions().map(a => a.label)).toEqual(['Create credit note', 'Create return note']);
  });

  it('cancels an unpaid invoice once the user confirms, and says what happens to the stock', () => {
    openSaved(invoice());
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Cancel invoice')!.run();

    expect(confirm.calls.mostRecent().args[0].message).toContain('goes back out');
    httpMock.expectOne(`${url}/5/cancel`).flush(invoice({ status: 'CANCELLED' }));
    expect(component.document()?.status).toBe('CANCELLED');
    fixture.detectChanges();
    answerPayments([]); // the panel follows the new document
  });

  it('reloads the invoice, and its payments, once a payment was recorded or cancelled', () => {
    openSaved(invoice());

    component.reloadDocument();
    httpMock.expectOne(`${url}/5`).flush(invoice({ status: 'PAID', paidAmount: 90, balance: 0 }));
    fixture.detectChanges();

    expect(component.document()?.status).toBe('PAID');
    answerPayments([]);
  });

  it('links an invoice to the goods receipt it was made from', () => {
    openSaved(invoice({ sourceId: 9, sourceReference: 'GR-2026-00001', sourceType: 'GOODS_RECEIPT' }));

    const link = (fixture.nativeElement as HTMLElement).querySelector('.link-line a') as HTMLAnchorElement;
    expect(link.textContent).toContain('GR-2026-00001');
    expect(link.getAttribute('href')).toBe('/goods-receipts/9');
  });

  // ----- Supplier credit note -----

  const creditNote = (overrides: Partial<PurchaseDocumentResponse> = {}) =>
    savedDocument({
      type: 'PURCHASE_CREDIT_NOTE', reference: 'PCN-2026-00001', status: 'VALIDATED', sourceId: 9,
      sourceReference: 'PINV-2026-00001', sourceType: 'PURCHASE_INVOICE', ...overrides
    });

  it('creates a credit note from an invoice and opens it', () => {
    openSaved(invoice());
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Create credit note')!.run();

    httpMock.expectOne(`${url}/5/convert-to-credit-note`).flush(creditNote({ id: 13, status: 'DRAFT', reference: null }));
    expect(router.navigate).toHaveBeenCalledWith(['/purchase-credit-notes', 13]);
  });

  it('keeps the supplier of the invoice on a draft credit note', () => {
    openSaved(creditNote({ status: 'DRAFT', reference: null }));

    expect(component.form.controls['supplierId'].disabled).toBeTrue();
    expect(component.form.controls['taxIds'].enabled).toBeTrue();
    expect(component.form.getRawValue().supplierId).toBe(3);
  });

  it('shows a validated credit note, linked to its invoice, with the cancellation as its only step', () => {
    openSaved(creditNote());

    expect(component.actions().map(a => a.label)).toEqual(['Cancel credit note']);
    const link = (fixture.nativeElement as HTMLElement).querySelector('.link-line a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/purchase-invoices/9');
    expect((fixture.nativeElement as HTMLElement).querySelector('app-page-header')!.textContent).toContain('Applied');
  });

  it('asks for no warehouse on a credit note: it moves no stock', () => {
    openSaved(creditNote({ status: 'DRAFT', reference: null }));

    expect(component.hasWarehouse).toBeFalse();
    httpMock.expectNone(`${api}/warehouses`);
  });

  it('creates an invoice from a validated goods receipt and opens it', () => {
    openSaved(receipt({ status: 'VALIDATED', reference: 'GR-2026-00001' }));
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Create invoice')!.run();

    httpMock.expectOne(`${url}/5/convert-to-invoice`).flush(invoice({ id: 12, status: 'DRAFT', sourceId: 5 }));
    expect(router.navigate).toHaveBeenCalledWith(['/purchase-invoices', 12]);
  });

  // ----- Supplier return note -----

  const returnNote = (overrides: Partial<PurchaseDocumentResponse> = {}) =>
    savedDocument({
      type: 'PURCHASE_RETURN_NOTE', warehouseId: 2, warehouseName: 'Annex', reference: 'PRN-2026-00001', status: 'VALIDATED',
      sourceId: 9, sourceReference: 'GR-2026-00001', sourceType: 'GOODS_RECEIPT', ...overrides
    });

  it('starts a new return note on the default warehouse, which it requires', () => {
    open({ type: 'PURCHASE_RETURN_NOTE' });

    expect(component.title()).toBe('New supplier return note');
    expect(component.form.value.warehouseId).toBe(1);
    component.form.patchValue({ warehouseId: null });
    expect(component.form.controls['warehouseId'].valid).toBeFalse();
  });

  it('creates a return note from a validated goods receipt and opens it', () => {
    openSaved(receipt({ status: 'VALIDATED', reference: 'GR-2026-00001' }));
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Create return note')!.run();

    httpMock.expectOne(`${url}/5/convert-to-return-note`).flush(returnNote({ id: 14, status: 'DRAFT', reference: null }));
    expect(router.navigate).toHaveBeenCalledWith(['/purchase-return-notes', 14]);
  });

  it('shows a validated return note as sent back, linked to the receipt, with the cancellation as its only step', () => {
    openSaved(returnNote());

    expect(component.actions().map(a => a.label)).toEqual(['Cancel return note']);
    const link = (fixture.nativeElement as HTMLElement).querySelector('.link-line a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/goods-receipts/9');
    expect((fixture.nativeElement as HTMLElement).querySelector('app-page-header')!.textContent).toContain('Sent back');
  });

  it('cancels a return note once the user confirms, and says the goods come back into the stock', () => {
    openSaved(returnNote());
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Cancel return note')!.run();

    expect(confirm.calls.mostRecent().args[0].message).toContain('come back into the stock');
    httpMock.expectOne(`${url}/5/cancel`).flush(returnNote({ status: 'CANCELLED' }));
    expect(component.document()?.status).toBe('CANCELLED');
  });

  // ----- Quantities followed line by line -----

  const followedLine = { id: 1, productId: 20, reference: 'P-0020', designation: 'Fabric', quantity: 6, unitPrice: 30,
    discountRate: 0, vatTaxId: 1, vatRate: 19, lineTotal: 180, sourceLineId: 7, sourceRemaining: 6 };

  it('says what a draft may still take of its source line, and refuses more', () => {
    openSaved(receipt({ lines: [followedLine] }));

    expect((fixture.nativeElement as HTMLElement).querySelector('.qty-hint')!.textContent).toContain('6 left to receive');
    component.lines.at(0).patchValue({ quantity: 7 });
    expect(component.lines.at(0).controls['quantity'].valid).toBeFalse();
    component.lines.at(0).patchValue({ quantity: 6 });
    expect(component.lines.at(0).controls['quantity'].valid).toBeTrue();
  });

  it('sends the link to the source line back with the draft', () => {
    openSaved(receipt({ lines: [followedLine] }));

    component.saveDraft();

    const req = httpMock.expectOne(`${url}/5`);
    expect(req.request.body.lines[0].sourceLineId).toBe(7);
    req.flush(receipt({ lines: [followedLine] }));
  });

  it('shows on a saved order what has been received of each line, and what is left', () => {
    openSaved(savedDocument({
      status: 'VALIDATED', reference: 'PO-2026-00001',
      lines: [{ ...followedLine, quantity: 10, sourceLineId: null, sourceRemaining: null, fulfilledQuantity: 4, remainingQuantity: 6 }]
    }));

    expect((fixture.nativeElement as HTMLElement).querySelector('.qty-hint')!.textContent).toContain('received 4 · 6 left');
  });

  it('shows no hint on a line that follows nothing', () => {
    openSaved(savedDocument());

    expect((fixture.nativeElement as HTMLElement).querySelector('.qty-hint')).toBeNull();
  });
});
